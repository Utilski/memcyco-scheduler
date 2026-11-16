package com.memcyco.scheduler.scheduling;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedulings")
public class SchedulingController {

  private final SchedulingService service;

  public SchedulingController(SchedulingService service) {
    this.service = service;
  }

  @GetMapping
  public List<SchedulingResponse> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public ResponseEntity<SchedulingResponse> get(@PathVariable UUID id) {
    return response(service.get(id));
  }

  @PostMapping
  public ResponseEntity<SchedulingResponse> create(@RequestBody Map<String, Object> input) {
    SchedulingResponse result = service.create(SchedulingRequest.parse(input));
    return ResponseEntity.created(URI.create("/api/schedulings/" + result.id()))
        .eTag(result.version().toString())
        .body(result);
  }

  @PutMapping("/{id}")
  public ResponseEntity<SchedulingResponse> replace(
      @PathVariable UUID id,
      @RequestBody Map<String, Object> input,
      @RequestHeader(value = "If-Match", required = false) String version) {
    return response(service.replace(id, SchedulingRequest.parse(input), parseVersion(version)));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<SchedulingResponse> update(
      @PathVariable UUID id,
      @RequestBody Map<String, Object> patch,
      @RequestHeader(value = "If-Match", required = false) String version) {
    return response(service.patch(id, patch, parseVersion(version)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String version) {
    service.delete(id, parseVersion(version));
    return ResponseEntity.noContent().build();
  }

  private static ResponseEntity<SchedulingResponse> response(SchedulingResponse result) {
    return ResponseEntity.ok().eTag(result.version().toString()).body(result);
  }

  private static Long parseVersion(String value) {
    if (value == null) return null;
    String raw = value.matches("\"[0-9]+\"") ? value.substring(1, value.length() - 1) : value;
    if (!raw.matches("[0-9]+"))
      throw new IllegalArgumentException("If-Match must contain one numeric version");
    return Long.valueOf(raw);
  }
}
