package com.memcyco.scheduler.scheduling;

import com.memcyco.scheduler.config.ExecutionConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionPublisher {
  private final ExecutionStore executionStore;
  private final RabbitTemplate rabbitTemplate;
  private final Clock clock;

  public ExecutionPublisher(
      ExecutionStore executionStore, RabbitTemplate rabbitTemplate, Clock clock) {
    this.executionStore = executionStore;
    this.rabbitTemplate = rabbitTemplate;
    this.clock = clock;
  }

  @Transactional(rollbackFor = Exception.class)
  public void publish(UUID id) throws ExecutionException, InterruptedException, TimeoutException {
    ExecutionStore.Execution execution = executionStore.lockExecution(id);
    if (execution == null || !execution.isReady(clock.instant()) || execution.published()) {
      return;
    }
    CorrelationData correlation = new CorrelationData(id.toString());
    rabbitTemplate.send(
        ExecutionConfiguration.EXCHANGE, ExecutionConfiguration.QUEUE, message(id), correlation);
    awaitConfirmation(id, correlation);
    executionStore.markPublished(id);
  }

  private Message message(UUID id) {
    MessageProperties properties = new MessageProperties();
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    properties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
    properties.setMessageId(id.toString());
    return new Message(id.toString().getBytes(StandardCharsets.UTF_8), properties);
  }

  private void awaitConfirmation(UUID id, CorrelationData correlation)
      throws ExecutionException, InterruptedException, TimeoutException {
    try {
      CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
      if (!confirm.ack() || correlation.getReturned() != null) {
        throw new IllegalStateException("Task publication was not confirmed and routed: " + id);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw exception;
    }
  }
}
