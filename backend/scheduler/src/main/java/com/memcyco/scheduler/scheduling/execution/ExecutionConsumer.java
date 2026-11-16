package com.memcyco.scheduler.scheduling.execution;

import com.memcyco.scheduler.config.ExecutionConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ExecutionConsumer {
  private final ExecutionCoordinator coordinator;

  public ExecutionConsumer(ExecutionCoordinator coordinator) {
    this.coordinator = coordinator;
  }

  @RabbitListener(
      queues = ExecutionConfiguration.QUEUE,
      autoStartup = "${app.execution.worker-enabled:true}")
  public void receive(Message message) {
    UUID executionId;
    try {
      executionId = UUID.fromString(new String(message.getBody(), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException exception) {
      throw new AmqpRejectAndDontRequeueException("Malformed execution identifier", exception);
    }
    // AUTO acknowledgment occurs after the transactional service has committed.
    coordinator.execute(executionId);
  }
}
