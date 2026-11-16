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

  private final SchedulingRepository repository;
  private final SchedulingRules rules;
  private final NextRunCalculator calculator;
  private final Clock clock;

  public SchedulingService(
      SchedulingRepository repository,
      SchedulingRules rules,
      NextRunCalculator calculator,
      Clock clock) {
    this.repository = repository;
    this.rules = rules;
    this.calculator = calculator;
    this.clock = clock;
  }

  public List<SchedulingResponse> list() {
    return repository.findAll(Sort.by("createdAt", "id")).stream()
        .map(SchedulingResponse::from)
        .toList();
  }

  public SchedulingResponse get(UUID id) {
    return SchedulingResponse.from(find(id));
  }

  @Transactional
  public SchedulingResponse create(SchedulingRequest request) {
    rules.validate(request);
    Instant now = now();
    Scheduling scheduling = new Scheduling();
    apply(scheduling, request);
    scheduling.setCreatedAt(now);
    scheduling.setUpdatedAt(now);
    scheduling.setNextRunAt(calculator.next(request, now));
    return SchedulingResponse.from(repository.saveAndFlush(scheduling));
  }

  @Transactional
  public SchedulingResponse replace(UUID id, SchedulingRequest request, Long expectedVersion) {
    return update(find(id), request, expectedVersion);
  }

  @Transactional
  public SchedulingResponse patch(UUID id, Map<String, Object> patch, Long expectedVersion) {
    SchedulingRequest.checkFields(patch);
    Scheduling scheduling = find(id);
    Map<String, Object> merged = SchedulingRequest.from(scheduling).toMap();
    merged.putAll(patch);
    return update(scheduling, SchedulingRequest.parse(merged), expectedVersion);
  }

  @Transactional
  public void delete(UUID id, Long expectedVersion) {
    Scheduling scheduling = find(id);
    checkVersion(scheduling, expectedVersion);
    repository.delete(scheduling);
    repository.flush();
  }

  private SchedulingResponse update(Scheduling s, SchedulingRequest request, Long expectedVersion) {
    checkVersion(s, expectedVersion);
    rules.validate(request);
    SchedulingRequest previous = SchedulingRequest.from(s);
    boolean timingChanged =
        previous.scheduleType() != request.scheduleType()
            || !previous.timeZone().equals(request.timeZone())
            || !previous.startsAt().equals(request.startsAt())
            || !previous.scheduleConfig().equals(request.scheduleConfig())
            || !Objects.equals(previous.endsAt(), request.endsAt())
            || !Objects.equals(previous.maxOccurrences(), request.maxOccurrences());
    apply(s, request);
    Instant now = now();
    s.setUpdatedAt(now);
    if (timingChanged) s.setDispatchedCount(0);
    if (!request.enabled()) {
      s.setNextRunAt(null);
    } else if (request.maxOccurrences() != null
        && s.getDispatchedCount() >= request.maxOccurrences()) {
      s.setNextRunAt(null);
    } else if (timingChanged || !previous.enabled()) {
      s.setNextRunAt(calculator.next(request, now));
    }
    return SchedulingResponse.from(repository.saveAndFlush(s));
  }

  private static void checkVersion(Scheduling s, Long expectedVersion) {
    if (expectedVersion != null && !expectedVersion.equals(s.getVersion()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Scheduling was changed by another request");
  }

  private Scheduling find(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scheduling not found"));
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  private static void apply(Scheduling s, SchedulingRequest r) {
    s.setName(r.name());
    s.setDescription(r.description());
    s.setTaskType(r.taskType());
    s.setPayload(r.payload());
    s.setScheduleType(r.scheduleType());
    s.setTimeZone(r.timeZone());
    s.setStartsAt(r.startsAt());
    s.setScheduleConfig(r.scheduleConfig());
    s.setEndsAt(r.endsAt());
    s.setMaxOccurrences(r.maxOccurrences());
    s.setEnabled(r.enabled());
  }
}
