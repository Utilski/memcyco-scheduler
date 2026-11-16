package com.memcyco.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memcyco.scheduler.scheduling.SchedulingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@SpringBootTest
@Import(SchedulingApiTests.FixedClock.class)
public class SchedulingApiTests {

  @Autowired private WebApplicationContext context;
  @Autowired protected SchedulingRepository repository;
  @Autowired private ObjectMapper mapper;

  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
  private MockMvc mvc;

  @TestConfiguration
  static class FixedClock {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }
  }

  @BeforeEach
  void setUp() {
    jdbc.update("DELETE FROM task_executions");
    jdbc.update("DELETE FROM scheduling_sync");
    repository.deleteAllInBatch();
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private Map<String, Object> valid() {
    return new java.util.LinkedHashMap<>(
        Map.of(
            "name",
            "Example",
            "taskType",
            "LOG",
            "payload",
            Map.of("message", "Hello"),
            "scheduleType",
            "ONE_TIME",
            "timeZone",
            "UTC",
            "startsAt",
            "2026-01-01T09:00:00",
            "scheduleConfig",
            Map.of()));
  }

  private MvcResult create(Map<String, Object> input) throws Exception {
    return mvc.perform(
            post("/api/schedulings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input)))
        .andExpect(status().isCreated())
        .andReturn();
  }

  private String location(MvcResult result) {
    return result.getResponse().getHeader("Location");
  }

  @Test
  void completeCrudPersistsAndReturnsExpectedStatuses() throws Exception {
    mvc.perform(get("/api/schedulings")).andExpect(status().isOk()).andExpect(content().json("[]"));
    MvcResult created = create(valid());
    String path = location(created);
    UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    assertTrue(repository.existsById(id));
    mvc.perform(get(path))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payload.message").value("Hello"))
        .andExpect(jsonPath("$.nextRunAt").value("2026-01-01T09:00:00Z"))
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(header().string("ETag", "\"0\""));
    Map<String, Object> replacement = valid();
    replacement.put("name", "Replaced");
    mvc.perform(
            put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(replacement)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Replaced"));
    mvc.perform(
            patch(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Patched\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Patched"));
    mvc.perform(get("/api/schedulings")).andExpect(jsonPath("$.length()").value(1));
    mvc.perform(delete(path)).andExpect(status().isNoContent());
    assertFalse(repository.existsById(id));
    mvc.perform(get(path))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    mvc.perform(delete(path)).andExpect(status().isNotFound());
  }

  @Test
  void patchValidatesMergedResultAndReplacesNestedObjects() throws Exception {
    String path = location(create(valid()));
    mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":null}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":null,\"endsAt\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").doesNotExist());
    mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"payload\":{}}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskType\":\"DB_QUERY\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"taskType\":\"DB_QUERY\",\"payload\":{\"query\":\"dummy"
                        + " text\",\"parameters\":{\"id\":5}}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payload.message").doesNotExist())
        .andExpect(jsonPath("$.payload.parameters.id").value(5));
  }

  @Test
  void rejectsMalformedRequestsAndReadOnlyFields() throws Exception {
    for (String body : new String[] {"{", "[]", "null", "{}", "{\"version\":0}"}) {
      mvc.perform(post("/api/schedulings").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
    mvc.perform(get("/api/schedulings/not-a-uuid")).andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/schedulings/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(valid())))
        .andExpect(status().isNotFound());
  }

  @Test
  void staleVersionIsRejectedWithoutOverwritingChanges() throws Exception {
    MvcResult created = create(valid());
    String path = location(created);
    String etag = created.getResponse().getHeader("ETag");
    mvc.perform(
            patch(path)
                .header("If-Match", etag)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            patch(path)
                .header("If-Match", etag)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Stale\"}"))
        .andExpect(status().isConflict());
    mvc.perform(delete(path).header("If-Match", etag)).andExpect(status().isConflict());
    mvc.perform(get(path)).andExpect(jsonPath("$.name").value("New"));
  }

  @Test
  void disablingAndEnablingAndTimingChangesRecalculateNextRun() throws Exception {
    String path = location(create(valid()));
    mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextRunAt").doesNotExist());
    mvc.perform(
            patch(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"startsAt\":\"2026-01-02T10:00:00\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextRunAt").value("2026-01-02T10:00:00Z"));
  }

  @Test
  void jsonAndMillisecondTimestampsRoundTripAcrossSeparateTransactions() throws Exception {
    Map<String, Object> input = valid();
    input.put("taskType", "DB_QUERY");
    input.put(
        "payload",
        Map.of(
            "query",
            "dummy query",
            "parameters",
            Map.of("a", java.util.List.of(1, 2), "flag", true)));
    input.put("startsAt", "2026-01-01T09:00:00.123");
    input.put("endsAt", "2026-01-02T09:00:00.654Z");
    String path = location(create(input));
    assertEquals(
        java.time.LocalDateTime.parse("2026-01-01T09:00:00.123"),
        jdbc.queryForObject("SELECT starts_at FROM schedulings", java.time.LocalDateTime.class));
    assertEquals(
        java.time.LocalDateTime.parse("2026-01-01T09:00:00.123"),
        jdbc.queryForObject("SELECT next_run_at FROM schedulings", java.time.LocalDateTime.class));
    mvc.perform(get(path))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payload.parameters.a[1]").value(2))
        .andExpect(jsonPath("$.payload.parameters.flag").value(true))
        .andExpect(jsonPath("$.startsAt").value("2026-01-01T09:00:00.123"))
        .andExpect(jsonPath("$.nextRunAt").value("2026-01-01T09:00:00.123Z"))
        .andExpect(jsonPath("$.endsAt").value("2026-01-02T09:00:00.654Z"));
  }

  @Test
  void metadataPreservesProgressAndTimingEditsResetIt() throws Exception {
    Map<String, Object> input = valid();
    input.put("scheduleType", "CALENDAR_INTERVAL");
    input.put("scheduleConfig", Map.of("unit", "DAY", "interval", 1));
    input.put("maxOccurrences", 5);
    String path = location(create(input));
    UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    var stored = repository.findById(id).orElseThrow();
    stored.setDispatchedCount(3);
    repository.saveAndFlush(stored);
    mvc.perform(
            patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dispatchedCount").value(3));
    mvc.perform(
            patch(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scheduleConfig\":{\"unit\":\"DAY\",\"interval\":2}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dispatchedCount").value(0));
  }

  @Test
  void jpaRejectsConflictingDetachedUpdates() throws Exception {
    String path = location(create(valid()));
    UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    var first = repository.findById(id).orElseThrow();
    var stale = repository.findById(id).orElseThrow();
    first.setName("First update");
    repository.saveAndFlush(first);
    stale.setName("Conflicting update");
    assertThrows(
        org.springframework.dao.OptimisticLockingFailureException.class,
        () -> repository.saveAndFlush(stale));
    assertEquals("First update", repository.findById(id).orElseThrow().getName());
  }

  @Test
  void viteCanPreflightAllCrudMethodsAndReadResponseHeaders() throws Exception {
    for (String origin : new String[] {"http://localhost:5173", "http://127.0.0.1:5173"}) {
      for (String method : new String[] {"GET", "POST", "PUT", "PATCH", "DELETE"}) {
        mvc.perform(
                options("/api/schedulings/" + UUID.randomUUID())
                    .header("Origin", origin)
                    .header("Access-Control-Request-Method", method)
                    .header(
                        "Access-Control-Request-Headers", "content-type,authorization,if-match"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", origin));
      }
    }
    mvc.perform(get("/api/schedulings").header("Origin", "http://localhost:5173"))
        .andExpect(header().string("Access-Control-Expose-Headers", "Location, ETag"));
  }

  @Test
  void unlistedOriginsAreRejected() throws Exception {
    mvc.perform(
            options("/api/schedulings")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  @Test
  void acceptsLargeYearlyIntervalWithNoSupportedNextRun() throws Exception {
    Map<String, Object> input = valid();
    input.put("startsAt", "2024-02-29T09:00:00");
    input.put("scheduleType", "CALENDAR_INTERVAL");
    input.put("scheduleConfig", Map.of("unit", "YEAR", "interval", Integer.MAX_VALUE));
    String path = location(create(input));
    mvc.perform(get(path))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextRunAt").doesNotExist())
        .andExpect(jsonPath("$.scheduleConfig.interval").value(Integer.MAX_VALUE));
  }
}
