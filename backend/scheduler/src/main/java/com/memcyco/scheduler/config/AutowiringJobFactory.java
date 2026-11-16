package com.memcyco.scheduler.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/** Quartz creates jobs outside component scanning; delegate construction to Spring. */
public final class AutowiringJobFactory extends SpringBeanJobFactory {
  private final AutowireCapableBeanFactory beanFactory;

  public AutowiringJobFactory(AutowireCapableBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  protected Object createJobInstance(TriggerFiredBundle bundle) {
    return beanFactory.createBean(bundle.getJobDetail().getJobClass());
  }
}
