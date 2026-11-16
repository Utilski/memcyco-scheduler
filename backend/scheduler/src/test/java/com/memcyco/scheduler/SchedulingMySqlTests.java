package com.memcyco.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Runs the same API contract against real MySQL, including Flyway and JSON mappings. */
@Testcontainers(disabledWithoutDocker = true)
class SchedulingMySqlTests extends SchedulingApiTests {

  @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
  }

  @Autowired private JdbcTemplate jdbc;

  @Test
  void flywayMigrationAndDueIndexExist() {
    assertEquals(
        5,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class));
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND"
                + " table_name = 'schedulings' AND index_name = 'idx_schedulings_due'",
            Integer.class));
  }

  @Test
  void packageMigrationUpdatesPersistedQuartzJobsWithoutChangingOtherJobClasses() throws Exception {
    String insert =
        "INSERT INTO QRTZ_JOB_DETAILS (SCHED_NAME, JOB_NAME, JOB_GROUP, JOB_CLASS_NAME, IS_DURABLE,"
            + " IS_NONCONCURRENT, IS_UPDATE_DATA, REQUESTS_RECOVERY) VALUES ('migration-test', ?,"
            + " 'test', ?, '1', '1', '0', '1')";
    jdbc.update(insert, "target", "com.memcyco.scheduler.scheduling.QuartzExecutionJob");
    jdbc.update(insert, "unrelated", "unrelated.Job");
    try {
      var resource =
          new org.springframework.core.io.ClassPathResource(
              "db/migration/V5__quartz_job_package.sql");
      jdbc.execute(resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
      assertEquals(
          com.memcyco.scheduler.scheduling.quartz.QuartzExecutionJob.class.getName(),
          jdbc.queryForObject(
              "SELECT JOB_CLASS_NAME FROM QRTZ_JOB_DETAILS WHERE SCHED_NAME = 'migration-test' AND"
                  + " JOB_NAME = 'target'",
              String.class));
      assertEquals(
          "unrelated.Job",
          jdbc.queryForObject(
              "SELECT JOB_CLASS_NAME FROM QRTZ_JOB_DETAILS WHERE SCHED_NAME = 'migration-test' AND"
                  + " JOB_NAME = 'unrelated'",
              String.class));
    } finally {
      jdbc.update("DELETE FROM QRTZ_JOB_DETAILS WHERE SCHED_NAME = 'migration-test'");
    }
  }
}
