package com.memcyco.scheduler.scheduling.api;

import com.memcyco.scheduler.scheduling.domain.TaskType;
import com.memcyco.scheduler.scheduling.query.SchedulingListQuery;
import com.memcyco.scheduler.scheduling.service.SchedulingService;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedulings")
public class SchedulingController {

  private final SchedulingService schedulingService;

  public SchedulingController(SchedulingService schedulingService) {
    this.schedulingService = schedulingService;
  }

  @GetMapping
  public SchedulingPage list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String enabled,
      @RequestParam(defaultValue = "createdAt") String sort,
      @RequestParam(defaultValue = "asc") String direction) {
    return schedulingService.list(
        SchedulingListQuery.parse(page, size, search, enabled, sort, direction));
  }

  @GetMapping("/task-types")
  public List<TaskType> taskTypes() {
    return List.of(TaskType.values());
  }

  @GetMapping("/{id}")
  public ResponseEntity<SchedulingResponse> get(@PathVariable UUID id) {
    return toResponse(schedulingService.get(id));
  }

  @PostMapping
  public ResponseEntity<SchedulingResponse> create(@RequestBody Map<String, Object> requestBody) {
    SchedulingResponse result = schedulingService.create(SchedulingRequest.parse(requestBody));
    return ResponseEntity.created(URI.create("/api/schedulings/" + result.id()))
        .eTag(result.version().toString())
        .body(result);
  }

  @PutMapping("/{id}")
  public ResponseEntity<SchedulingResponse> replace(
      @PathVariable UUID id,
      @RequestBody Map<String, Object> requestBody,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
    return toResponse(
        schedulingService.replace(
            id, SchedulingRequest.parse(requestBody), parseExpectedVersion(ifMatchHeader)));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<SchedulingResponse> update(
      @PathVariable UUID id,
      @RequestBody Map<String, Object> patch,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
    return toResponse(schedulingService.patch(id, patch, parseExpectedVersion(ifMatchHeader)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
    schedulingService.delete(id, parseExpectedVersion(ifMatchHeader));
    return ResponseEntity.noContent().build();
  }

  private static ResponseEntity<SchedulingResponse> toResponse(SchedulingResponse result) {
    return ResponseEntity.ok().eTag(result.version().toString()).body(result);
  }

  private static Long parseExpectedVersion(String value) {
    if (value == null) {
      return null;
    }
    String versionText =
        value.matches("\"[0-9]+\"") ? value.substring(1, value.length() - 1) : value;
    if (!versionText.matches("[0-9]+")) {
      throw new IllegalArgumentException("If-Match must contain one numeric version");
    }
    return Long.valueOf(versionText);
  }
}
