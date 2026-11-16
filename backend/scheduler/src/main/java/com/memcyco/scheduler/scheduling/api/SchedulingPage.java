package com.memcyco.scheduler.scheduling.api;

import com.memcyco.scheduler.scheduling.domain.Scheduling;
import java.util.List;
import org.springframework.data.domain.Page;

public record SchedulingPage(
    List<SchedulingResponse> items, int page, int size, long totalItems, int totalPages) {
  public static SchedulingPage from(Page<Scheduling> result) {
    return new SchedulingPage(
        result.getContent().stream().map(SchedulingResponse::from).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }
}
