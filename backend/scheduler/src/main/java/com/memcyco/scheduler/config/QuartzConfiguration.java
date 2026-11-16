package com.memcyco.scheduler.config;

import java.util.Properties;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.quartz.autoconfigure.QuartzProperties;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class QuartzConfiguration {
  @Bean
  SchedulerFactoryBeanCustomizer quartzCustomizer(
      AutowireCapableBeanFactory beanFactory, QuartzProperties quartzProperties) {
    return factory -> {
      factory.setQuartzProperties(schedulerProperties(quartzProperties));
      factory.setJobFactory(new AutowiringJobFactory(beanFactory));
    };
  }

  private Properties schedulerProperties(QuartzProperties quartzProperties) {
    Properties properties = new Properties();
    properties.putAll(quartzProperties.getProperties());
    if (quartzProperties.getJobStoreType().name().equals("JDBC")) {
      properties.putIfAbsent("org.quartz.jobStore.isClustered", "true");
      properties.putIfAbsent("org.quartz.jobStore.clusterCheckinInterval", "10000");
    }
    return properties;
  }
}
