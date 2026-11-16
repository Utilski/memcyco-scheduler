package com.memcyco.scheduler.scheduling.service;

import com.memcyco.scheduler.scheduling.api.SchedulingPage;
import com.memcyco.scheduler.scheduling.api.SchedulingRequest;
import com.memcyco.scheduler.scheduling.api.SchedulingResponse;
import com.memcyco.scheduler.scheduling.domain.Scheduling;
import com.memcyco.scheduler.scheduling.persistence.ExecutionStore;
import com.memcyco.scheduler.scheduling.persistence.SchedulingRepository;
import com.memcyco.scheduler.scheduling.quartz.QuartzTriggerFactory;
import com.memcyco.scheduler.scheduling.query.SchedulingListQuery;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class SchedulingService {

  private final SchedulingRepository schedulingRepository;
  private final SchedulingRules schedulingRules;
  private final QuartzTriggerFactory triggerFactory;
  private final Clock clock;
  private final ExecutionStore executionStore;

  public SchedulingService(
      SchedulingRepository schedulingRepository,
      SchedulingRules schedulingRules,
      QuartzTriggerFactory triggerFactory,
      Clock clock,
      ExecutionStore executionStore) {
    this.schedulingRepository = schedulingRepository;
    this.schedulingRules = schedulingRules;
    this.triggerFactory = triggerFactory;
    this.clock = clock;
    this.executionStore = executionStore;
  }

  public SchedulingPage list(SchedulingListQuery query) {
    if (query.pageable().getOffset() > Integer.MAX_VALUE) {
      long count = schedulingRepository.count(query.specification());
      return SchedulingPage.from(new PageImpl<>(List.of(), query.pageable(), count));
    }
    return SchedulingPage.from(
        schedulingRepository.findAll(query.specification(), query.pageable()));
  }

  public SchedulingResponse get(UUID id) {
    return SchedulingResponse.from(findScheduling(id));
  }

  @Transactional
  public SchedulingResponse create(SchedulingRequest request) {
    schedulingRules.validate(request);
    Instant now = clock.instant();
    Scheduling scheduling = new Scheduling();
    applyRequest(scheduling, request);
    scheduling.setCreatedAt(now);
    scheduling.setUpdatedAt(now);
    scheduling.setNextRunAt(triggerFactory.firstFireTime(request, now));
    return saveAndRequestSync(scheduling, now);
  }

  @Transactional
  public SchedulingResponse replace(UUID id, SchedulingRequest request, Long expectedVersion) {
    return update(findForUpdate(id), request, expectedVersion);
  }

  @Transactional
  public SchedulingResponse patch(UUID id, Map<String, Object> patch, Long expectedVersion) {
    SchedulingRequest.validateWritableFields(patch);
    Scheduling scheduling = findForUpdate(id);
    Map<String, Object> mergedFields = SchedulingRequest.from(scheduling).toMap();
    mergedFields.putAll(patch);
    return update(scheduling, SchedulingRequest.parse(mergedFields), expectedVersion);
  }

  @Transactional
  public void delete(UUID id, Long expectedVersion) {
    Scheduling scheduling = findForUpdate(id);
    checkExpectedVersion(scheduling, expectedVersion);
    executionStore.cancelPending(id, clock.instant());
    executionStore.requestSync(id, clock.instant());
    schedulingRepository.delete(scheduling);
    schedulingRepository.flush();
  }

  private SchedulingResponse update(
      Scheduling scheduling, SchedulingRequest request, Long expectedVersion) {
    checkExpectedVersion(scheduling, expectedVersion);
    schedulingRules.validate(request);
    SchedulingRequest previousRequest = SchedulingRequest.from(scheduling);
    applyRequest(scheduling, request);
    Instant now = clock.instant();
    updateProgress(scheduling, request, previousRequest, now);
    return saveAndRequestSync(scheduling, now);
  }

  private void updateProgress(
      Scheduling scheduling,
      SchedulingRequest request,
      SchedulingRequest previousRequest,
      Instant now) {
    boolean timingChanged = hasTimingChanged(previousRequest, request);
    boolean generationChanged = timingChanged || previousRequest.enabled() != request.enabled();
    if (generationChanged) {
      scheduling.setGeneration(scheduling.getGeneration() + 1);
      executionStore.cancelPending(scheduling.getId(), now);
    }
    scheduling.setUpdatedAt(now);
    if (timingChanged) {
      scheduling.setDispatchedCount(0);
    }
    if (!request.enabled() || scheduling.isCompleted()) {
      scheduling.setNextRunAt(null);
    } else if (timingChanged || !previousRequest.enabled()) {
      scheduling.setNextRunAt(triggerFactory.firstFireTime(request, now));
    }
  }

  private SchedulingResponse saveAndRequestSync(Scheduling scheduling, Instant now) {
    schedulingRepository.saveAndFlush(scheduling);
    executionStore.requestSync(scheduling.getId(), now);
    return SchedulingResponse.from(scheduling);
  }

  private static boolean hasTimingChanged(
      SchedulingRequest previousRequest, SchedulingRequest request) {
    return previousRequest.scheduleType() != request.scheduleType()
        || !previousRequest.timeZone().equals(request.timeZone())
        || !previousRequest.startsAt().equals(request.startsAt())
        || !previousRequest.scheduleConfig().equals(request.scheduleConfig())
        || !Objects.equals(previousRequest.endsAt(), request.endsAt())
        || !Objects.equals(previousRequest.maxOccurrences(), request.maxOccurrences());
  }

  private static void checkExpectedVersion(Scheduling scheduling, Long expectedVersion) {
    if (expectedVersion != null && !expectedVersion.equals(scheduling.getVersion())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Scheduling was changed by another request");
    }
  }

  private Scheduling findScheduling(UUID id) {
    return schedulingRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scheduling not found"));
  }

  private Scheduling findForUpdate(UUID id) {
    return schedulingRepository
        .findForUpdate(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scheduling not found"));
  }

  private static void applyRequest(Scheduling scheduling, SchedulingRequest request) {
    scheduling.setName(request.name());
    scheduling.setDescription(request.description());
    scheduling.setTaskType(request.taskType());
    scheduling.setPayload(request.payload());
    scheduling.setScheduleType(request.scheduleType());
    scheduling.setTimeZone(request.timeZone());
    scheduling.setStartsAt(request.startsAt());
    scheduling.setScheduleConfig(request.scheduleConfig());
    scheduling.setEndsAt(request.endsAt());
    scheduling.setMaxOccurrences(request.maxOccurrences());
    scheduling.setEnabled(request.enabled());
  }
}
