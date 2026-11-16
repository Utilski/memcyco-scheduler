package com.memcyco.scheduler.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ExecutionConfiguration {
  public static final String EXCHANGE = "scheduler.tasks";
  public static final String QUEUE = "scheduler.tasks.execute";

  @Bean
  DirectExchange taskExchange() {
    return new DirectExchange(EXCHANGE, true, false);
  }

  @Bean
  Queue taskQueue() {
    return new Queue(QUEUE, true);
  }

  @Bean
  Binding taskBinding(Queue taskQueue, DirectExchange taskExchange) {
    return BindingBuilder.bind(taskQueue).to(taskExchange).with(QUEUE);
  }
}
