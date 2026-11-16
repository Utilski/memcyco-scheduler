-- Quartz persists the job class name. Preserve existing jobs when organizing Java packages.
UPDATE QRTZ_JOB_DETAILS
SET JOB_CLASS_NAME = 'com.memcyco.scheduler.scheduling.quartz.QuartzExecutionJob'
WHERE JOB_CLASS_NAME = 'com.memcyco.scheduler.scheduling.QuartzExecutionJob';
