package com.memcyco.scheduler.config;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

  @Bean
  public Clock clock() {
    return Clock.tickMillis(ZoneOffset.UTC);
  }
}
