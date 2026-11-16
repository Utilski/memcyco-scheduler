package com.memcyco.scheduler.scheduling;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Local handlers only. Email and SQL deliberately have no external effects. */
@Component
public class TaskHandlers {
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskHandlers.class);

  public void execute(ExecutionStore.Execution execution, Map<String, Object> payload) {
    switch (execution.taskType()) {
      case LOG -> LOGGER.info("execution={} message={}", execution.id(), payload.get("message"));
      case DUMMY_EMAIL ->
          LOGGER.info(
              "execution={} simulated email to={} subject={}",
              execution.id(),
              payload.get("to"),
              payload.get("subject"));
      case DB_QUERY ->
          LOGGER.info("execution={} simulated database query (not executed)", execution.id());
    }
  }
}
