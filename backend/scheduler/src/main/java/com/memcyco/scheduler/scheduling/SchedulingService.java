package com.memcyco.scheduler.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class SchedulingService {

  private final SchedulingRepository schedulingRepository;
  private final SchedulingRules schedulingRules;
  private final NextRunCalculator nextRunCalculator;
  private final Clock clock;

  public SchedulingService(
      SchedulingRepository schedulingRepository,
      SchedulingRules schedulingRules,
      NextRunCalculator nextRunCalculator,
      Clock clock) {
    this.schedulingRepository = schedulingRepository;
    this.schedulingRules = schedulingRules;
    this.nextRunCalculator = nextRunCalculator;
    this.clock = clock;
  }

  public List<SchedulingResponse> list() {
    return schedulingRepository.findAll(Sort.by("createdAt", "id")).stream()
        .map(SchedulingResponse::from)
        .toList();
  }

  public SchedulingResponse get(UUID id) {
    return SchedulingResponse.from(findScheduling(id));
  }

  @Transactional
  public SchedulingResponse create(SchedulingRequest request) {
    schedulingRules.validate(request);
    Instant now = now();
    Scheduling scheduling = new Scheduling();
    applyRequest(scheduling, request);
    scheduling.setCreatedAt(now);
    scheduling.setUpdatedAt(now);
    scheduling.setNextRunAt(nextRunCalculator.next(request, now));
    return SchedulingResponse.from(schedulingRepository.saveAndFlush(scheduling));
  }

  @Transactional
  public SchedulingResponse replace(UUID id, SchedulingRequest request, Long expectedVersion) {
    return update(findScheduling(id), request, expectedVersion);
  }

  @Transactional
  public SchedulingResponse patch(UUID id, Map<String, Object> patch, Long expectedVersion) {
    SchedulingRequest.validateWritableFields(patch);
    Scheduling scheduling = findScheduling(id);
    Map<String, Object> mergedFields = SchedulingRequest.from(scheduling).toMap();
    mergedFields.putAll(patch);
    return update(scheduling, SchedulingRequest.parse(mergedFields), expectedVersion);
  }

  @Transactional
  public void delete(UUID id, Long expectedVersion) {
    Scheduling scheduling = findScheduling(id);
    checkExpectedVersion(scheduling, expectedVersion);
    schedulingRepository.delete(scheduling);
    schedulingRepository.flush();
  }

  private SchedulingResponse update(
      Scheduling scheduling, SchedulingRequest request, Long expectedVersion) {
    checkExpectedVersion(scheduling, expectedVersion);
    schedulingRules.validate(request);
    SchedulingRequest previousRequest = SchedulingRequest.from(scheduling);
    boolean timingChanged = hasTimingChanged(previousRequest, request);
    applyRequest(scheduling, request);
    Instant now = now();
    scheduling.setUpdatedAt(now);
    if (timingChanged) {
      scheduling.setDispatchedCount(0);
    }
    if (!request.enabled()) {
      scheduling.setNextRunAt(null);
    } else if (request.maxOccurrences() != null
        && scheduling.getDispatchedCount() >= request.maxOccurrences()) {
      scheduling.setNextRunAt(null);
    } else if (timingChanged || !previousRequest.enabled()) {
      scheduling.setNextRunAt(nextRunCalculator.next(request, now));
    }
    return SchedulingResponse.from(schedulingRepository.saveAndFlush(scheduling));
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

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
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
