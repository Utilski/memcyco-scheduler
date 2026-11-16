package com.memcyco.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.memcyco.scheduler.config.AutowiringJobFactory;
import com.memcyco.scheduler.config.ExecutionConfiguration;
import com.memcyco.scheduler.scheduling.api.SchedulingRequest;
import com.memcyco.scheduler.scheduling.api.SchedulingResponse;
import com.memcyco.scheduler.scheduling.domain.ScheduleType;
import com.memcyco.scheduler.scheduling.domain.TaskType;
import com.memcyco.scheduler.scheduling.execution.ExecutionCoordinator;
import com.memcyco.scheduler.scheduling.execution.ExecutionPublisher;
import com.memcyco.scheduler.scheduling.execution.TaskHandlers;
import com.memcyco.scheduler.scheduling.persistence.ExecutionStore;
import com.memcyco.scheduler.scheduling.persistence.SchedulingRepository;
import com.memcyco.scheduler.scheduling.quartz.QuartzScheduleSynchronizer;
import com.memcyco.scheduler.scheduling.quartz.QuartzTriggerFactory;
import com.memcyco.scheduler.scheduling.service.SchedulingService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "spring.quartz.job-store-type=jdbc",
      "spring.quartz.properties.org.quartz.jobStore.isClustered=true",
      "app.execution.worker-enabled=true"
    })
@Import(ExecutionIntegrationTests.TestClockConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
class ExecutionIntegrationTests {
  @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

  @Container
  static final GenericContainer<?> RABBIT =
      new GenericContainer<>("rabbitmq:4-alpine")
          .withEnv("RABBITMQ_DEFAULT_USER", "testuser")
          .withEnv("RABBITMQ_DEFAULT_PASS", "testpass")
          .withExposedPorts(5672);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
    registry.add("spring.rabbitmq.username", () -> "testuser");
    registry.add("spring.rabbitmq.password", () -> "testpass");
  }

  static class MutableClock extends Clock {
    private volatile Instant current = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    void set(Instant value) {
      current = value;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return current;
    }
  }

  @TestConfiguration
  static class TestClockConfiguration {
    @Bean
    @Primary
    MutableClock executionTestClock() {
      return new MutableClock();
    }
  }

  @Autowired private MutableClock clock;
  @Autowired private SchedulingService service;
  @Autowired private SchedulingRepository repository;
  @Autowired private ExecutionCoordinator coordinator;
  @Autowired private QuartzScheduleSynchronizer synchronizer;
  @Autowired private ExecutionPublisher publisher;
  @Autowired private ExecutionStore store;
  @Autowired private Scheduler scheduler;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;
  @Autowired private AutowireCapableBeanFactory beanFactory;
  @Autowired private AmqpAdmin amqpAdmin;
  @Autowired private RabbitTemplate rabbitTemplate;
  @MockitoSpyBean private TaskHandlers handlers;

  @BeforeEach
  void clean() throws Exception {
    scheduler.standby();
    scheduler.clear();
    jdbc.update("DELETE FROM task_executions");
    jdbc.update("DELETE FROM scheduling_sync");
    repository.deleteAllInBatch();
    clock.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    reset(handlers);
  }

  private SchedulingResponse recurring(Long limit) {
    return service.create(
        new SchedulingRequest(
            "Task",
            null,
            TaskType.LOG,
            Map.of("message", "hello"),
            ScheduleType.SIMPLE_INTERVAL,
            "UTC",
            LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
            Map.of("unit", "SECONDS", "interval", 60),
            null,
            limit,
            true));
  }

  private UUID dispatch(SchedulingResponse scheduling) {
    coordinator.dispatch(
        scheduling.id(),
        repository.findById(scheduling.id()).orElseThrow().getGeneration(),
        clock.instant(),
        clock.instant().plusSeconds(60));
    return UUID.fromString(
        jdbc.queryForObject(
            "SELECT id FROM task_executions WHERE scheduling_id = ? ORDER BY scheduled_at DESC"
                + " LIMIT 1",
            String.class,
            scheduling.id().toString()));
  }

  private String status(UUID executionId) {
    return jdbc.queryForObject(
        "SELECT status FROM task_executions WHERE id = ?", String.class, executionId.toString());
  }

  private void await(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      Thread.sleep(25);
    }
    assertTrue(condition.getAsBoolean(), "Timed out waiting for execution state");
  }

  @Test
  void quartzFiresNativeTriggerAndRabbitWorkerCompletesOnce() throws Exception {
    SchedulingResponse scheduling =
        service.create(
            new SchedulingRequest(
                "One time",
                null,
                TaskType.LOG,
                Map.of("message", "from Quartz"),
                ScheduleType.ONE_TIME,
                "UTC",
                LocalDateTime.ofInstant(
                    Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC),
                Map.of(),
                null,
                null,
                true));
    synchronizer.synchronize(scheduling.id());
    scheduler.start();
    await(() -> store.hasOutstanding(scheduling.id()));
    UUID executionId =
        UUID.fromString(jdbc.queryForObject("SELECT id FROM task_executions", String.class));
    publisher.publish(executionId);
    await(() -> status(executionId).equals("SUCCEEDED"));
    coordinator.execute(executionId);
    verify(handlers, times(1)).execute(any(), any());
    assertEquals(1, service.get(scheduling.id()).dispatchedCount());
    synchronizer.synchronize(scheduling.id());
    assertEquals(null, service.get(scheduling.id()).nextRunAt());
  }

  @Test
  void limitsAndOutstandingWorkPreventDuplicateDispatch() throws Exception {
    SchedulingResponse scheduling = recurring(1L);
    UUID executionId = dispatch(scheduling);
    coordinator.dispatch(scheduling.id(), 0, clock.instant().plusSeconds(60), null);
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM task_executions", Integer.class));
    coordinator.execute(executionId);
    coordinator.dispatch(scheduling.id(), 0, clock.instant().plusSeconds(120), null);
    assertEquals(1, service.get(scheduling.id()).dispatchedCount());
    synchronizer.synchronize(scheduling.id());
    assertFalse(scheduler.checkExists(new QuartzTriggerFactory().jobKey(scheduling.id())));
  }

  @Test
  void retryDelaysAreDurableAndThirdFailureDisablesSchedule() {
    SchedulingResponse scheduling = recurring(null);
    UUID executionId = dispatch(scheduling);
    doThrow(new IllegalStateException("simulated handler failure"))
        .when(handlers)
        .execute(any(), any());
    coordinator.execute(executionId);
    assertEquals(
        Timestamp.from(clock.instant().plusSeconds(5)),
        jdbc.queryForObject("SELECT publish_after FROM task_executions", Timestamp.class));
    coordinator.execute(executionId);
    verify(handlers, times(1)).execute(any(), any());
    clock.set(clock.instant().plusSeconds(5));
    coordinator.execute(executionId);
    assertEquals(
        Timestamp.from(clock.instant().plusSeconds(30)),
        jdbc.queryForObject("SELECT publish_after FROM task_executions", Timestamp.class));
    clock.set(clock.instant().plusSeconds(30));
    coordinator.execute(executionId);
    assertEquals("FAILED", status(executionId));
    assertFalse(service.get(scheduling.id()).enabled());
    assertEquals(1, service.get(scheduling.id()).dispatchedCount());
  }

  @Test
  void metadataKeepsSnapshotButTimingEditsCancelPendingMessages() {
    SchedulingResponse scheduling = recurring(null);
    UUID executionId = dispatch(scheduling);
    service.patch(scheduling.id(), Map.of("payload", Map.of("message", "edited")), null);
    assertTrue(
        jdbc.queryForObject("SELECT payload FROM task_executions", String.class).contains("hello"));
    service.patch(
        scheduling.id(),
        Map.of("scheduleConfig", Map.of("unit", "SECONDS", "interval", 120)),
        null);
    assertEquals("CANCELED", status(executionId));
    coordinator.execute(executionId);
    verify(handlers, times(0)).execute(any(), any());
    coordinator.dispatch(scheduling.id(), 0, clock.instant(), null);
    assertEquals(0, service.get(scheduling.id()).dispatchedCount());
  }

  @Test
  void disableAndDeleteCancelQueuedWorkAndKeepHistory() throws Exception {
    SchedulingResponse scheduling = recurring(null);
    UUID executionId = dispatch(scheduling);
    service.patch(scheduling.id(), Map.of("enabled", false), null);
    assertEquals("CANCELED", status(executionId));
    synchronizer.synchronize(scheduling.id());
    service.delete(scheduling.id(), null);
    synchronizer.synchronize(scheduling.id());
    assertEquals("CANCELED", status(executionId));
    assertFalse(repository.existsById(scheduling.id()));
  }

  @Test
  void nativeTriggersAreStoredAndSynchronizationIsIdempotent() throws Exception {
    SchedulingResponse scheduling = recurring(null);
    synchronizer.synchronize(scheduling.id());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM QRTZ_JOB_DETAILS", Integer.class));
    assertEquals(
        1, jdbc.queryForObject("SELECT COUNT(*) FROM QRTZ_SIMPLE_TRIGGERS", Integer.class));
    synchronizer.synchronize(scheduling.id());
    assertNotNull(scheduler.getJobDetail(new QuartzTriggerFactory().jobKey(scheduling.id())));
    assertTrue(store.pendingSync().isEmpty());
  }

  private SchedulerFactoryBean anotherScheduler() throws Exception {
    var factory = new SchedulerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setSchedulerName("memcyco-scheduler");
    var properties = new Properties();
    properties.setProperty("org.quartz.scheduler.instanceId", UUID.randomUUID().toString());
    properties.setProperty("org.quartz.jobStore.isClustered", "true");
    properties.setProperty("org.quartz.jobStore.misfireThreshold", "1000");
    properties.setProperty("org.quartz.threadPool.threadCount", "2");
    factory.setQuartzProperties(properties);
    factory.setJobFactory(new AutowiringJobFactory(beanFactory));
    factory.afterPropertiesSet();
    return factory;
  }

  @Test
  void twoClusterNodesDispatchOneOccurrenceAndKeepPersistedTriggers() throws Exception {
    SchedulingResponse scheduling =
        service.create(
            new SchedulingRequest(
                "Cluster",
                null,
                TaskType.LOG,
                Map.of("message", "cluster"),
                ScheduleType.ONE_TIME,
                "UTC",
                LocalDateTime.ofInstant(
                    Instant.now().plusSeconds(2).truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC),
                Map.of(),
                null,
                null,
                true));
    synchronizer.synchronize(scheduling.id());
    var secondFactory = anotherScheduler();
    try {
      assertNotNull(
          secondFactory
              .getScheduler()
              .getTrigger(new TriggerKey(scheduling.id().toString(), QuartzTriggerFactory.GROUP)));
      scheduler.start();
      secondFactory.start();
      await(() -> store.hasOutstanding(scheduling.id()));
      assertEquals(1, service.get(scheduling.id()).dispatchedCount());
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM task_executions", Integer.class));
    } finally {
      secondFactory.destroy();
    }
  }

  @Test
  void persistedTriggerExecutesAfterSchedulerInstanceReplacement() throws Exception {
    SchedulingResponse scheduling = recurring(null);
    synchronizer.synchronize(scheduling.id());
    // Original node remains in standby: the fresh node loads and executes its persisted job.
    var replacement = anotherScheduler();
    try {
      replacement.start();
      await(() -> store.hasOutstanding(scheduling.id()));
      assertEquals(1, service.get(scheduling.id()).dispatchedCount());
    } finally {
      replacement.destroy();
    }
  }

  @Test
  void unroutedPublicationRollsBackAndCanBeRetriedWithoutConsumingAttempts() throws Exception {
    SchedulingResponse scheduling = recurring(null);
    UUID executionId = dispatch(scheduling);
    var binding =
        new Binding(
            ExecutionConfiguration.QUEUE,
            Binding.DestinationType.QUEUE,
            ExecutionConfiguration.EXCHANGE,
            ExecutionConfiguration.QUEUE,
            null);
    amqpAdmin.removeBinding(binding);
    try {
      assertThrows(IllegalStateException.class, () -> publisher.publish(executionId));
      assertFalse(jdbc.queryForObject("SELECT published FROM task_executions", Boolean.class));
      assertEquals(0, jdbc.queryForObject("SELECT attempts FROM task_executions", Integer.class));
    } finally {
      amqpAdmin.declareBinding(binding);
    }
    publisher.publish(executionId);
    await(() -> status(executionId).equals("SUCCEEDED"));
    rabbitTemplate.convertAndSend(
        ExecutionConfiguration.EXCHANGE, ExecutionConfiguration.QUEUE, executionId.toString());
    coordinator.execute(executionId);
    verify(handlers, times(1)).execute(any(), any());
  }

  @Test
  void concurrentDispatchTransactionsSerializeAndKeepOneOutstandingOccurrence() {
    SchedulingResponse scheduling = recurring(null);
    CompletableFuture.allOf(
            CompletableFuture.runAsync(
                () -> coordinator.dispatch(scheduling.id(), 0, clock.instant(), null)),
            CompletableFuture.runAsync(
                () -> coordinator.dispatch(scheduling.id(), 0, clock.instant(), null)))
        .join();
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM task_executions", Integer.class));
    assertEquals(1, service.get(scheduling.id()).dispatchedCount());
  }

  @Test
  void pendingExecutionPausesNativeTriggerAndResumesWithOneCatchUp() throws Exception {
    SchedulingResponse scheduling =
        service.create(
            new SchedulingRequest(
                "Catch-up",
                null,
                TaskType.LOG,
                Map.of("message", "catch-up"),
                ScheduleType.SIMPLE_INTERVAL,
                "UTC",
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                Map.of("unit", "SECONDS", "interval", 1),
                null,
                2L,
                true));
    synchronizer.synchronize(scheduling.id());
    scheduler.start();
    await(() -> store.hasOutstanding(scheduling.id()));
    UUID firstExecution =
        UUID.fromString(jdbc.queryForObject("SELECT id FROM task_executions", String.class));
    synchronizer.synchronize(scheduling.id());
    Thread.sleep(2200); // Deliberately miss multiple native trigger intervals while paused.
    assertEquals(1, service.get(scheduling.id()).dispatchedCount());
    coordinator.execute(firstExecution);
    synchronizer.synchronize(scheduling.id());
    await(() -> service.get(scheduling.id()).dispatchedCount() == 2);
    synchronizer.synchronize(scheduling.id());
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM task_executions", Integer.class));
    assertFalse(scheduler.checkExists(new QuartzTriggerFactory().jobKey(scheduling.id())));
  }

  @Test
  void dummyEmailAndQueryHandlersHaveNoExternalSideEffects() {
    for (TaskType taskType : new TaskType[] {TaskType.DUMMY_EMAIL, TaskType.DB_QUERY}) {
      Map<String, Object> payload =
          taskType == TaskType.DUMMY_EMAIL
              ? Map.of("to", "test@example.com", "subject", "simulated", "body", "hello")
              : Map.of("query", "DROP TABLE schedulings", "parameters", Map.of());
      SchedulingResponse scheduling =
          service.create(
              new SchedulingRequest(
                  "Dummy",
                  null,
                  taskType,
                  payload,
                  ScheduleType.ONE_TIME,
                  "UTC",
                  LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                  Map.of(),
                  null,
                  null,
                  true));
      UUID executionId = dispatch(scheduling);
      coordinator.execute(executionId);
      assertEquals("SUCCEEDED", status(executionId));
    }
    assertEquals(2, repository.count());
    verify(handlers, times(2)).execute(any(), any());
  }

  @Test
  void completedOneTimeScheduleCannotRedispatchOrRestartByTogglingEnabled() throws Exception {
    SchedulingResponse scheduling =
        service.create(
            new SchedulingRequest(
                "Once",
                null,
                TaskType.LOG,
                Map.of("message", "once"),
                ScheduleType.ONE_TIME,
                "UTC",
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                Map.of(),
                null,
                null,
                true));
    UUID executionId = dispatch(scheduling);
    coordinator.execute(executionId);
    coordinator.dispatch(scheduling.id(), 0, clock.instant().plusSeconds(1), null);
    assertEquals(1, service.get(scheduling.id()).dispatchedCount());
    service.patch(scheduling.id(), Map.of("enabled", false), null);
    SchedulingResponse enabled = service.patch(scheduling.id(), Map.of("enabled", true), null);
    assertEquals(null, enabled.nextRunAt());
    synchronizer.synchronize(scheduling.id());
    assertFalse(scheduler.checkExists(new QuartzTriggerFactory().jobKey(scheduling.id())));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM task_executions", Integer.class));
  }
}
