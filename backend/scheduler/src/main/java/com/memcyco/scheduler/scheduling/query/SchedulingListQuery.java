package com.memcyco.scheduler.scheduling.query;

import com.memcyco.scheduler.scheduling.domain.Scheduling;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/** Validated collection parameters; no arbitrary property names reach JPA sorting. */
public record SchedulingListQuery(
    int page, int size, String search, Boolean enabled, String sort, Sort.Direction direction) {
  private static final Set<String> SORT_FIELDS =
      Set.of(
          "name",
          "taskType",
          "scheduleType",
          "enabled",
          "startsAt",
          "nextRunAt",
          "createdAt",
          "updatedAt");

  public SchedulingListQuery {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException(
          "page must be nonnegative and size must be between 1 and 100");
    }
    if (!SORT_FIELDS.contains(sort)) {
      throw new IllegalArgumentException("Unsupported sort field: " + sort);
    }
    search = search == null ? "" : search.strip();
  }

  public static SchedulingListQuery parse(
      int page, int size, String search, String enabled, String sort, String direction) {
    if (enabled != null && !enabled.equals("true") && !enabled.equals("false")) {
      throw new IllegalArgumentException("enabled must be true or false");
    }
    return new SchedulingListQuery(
        page,
        size,
        search,
        enabled == null ? null : Boolean.valueOf(enabled),
        sort,
        Sort.Direction.fromString(direction));
  }

  public PageRequest pageable() {
    return PageRequest.of(page, size, Sort.by(direction, sort).and(Sort.by("id")));
  }

  public Specification<Scheduling> specification() {
    return (root, query, builder) -> {
      var predicates = new ArrayList<Predicate>();
      if (enabled != null) {
        predicates.add(builder.equal(root.get("enabled"), enabled));
      }
      if (!search.isEmpty()) {
        String pattern =
            "%"
                + search
                    .toLowerCase(Locale.ROOT)
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_")
                + "%";
        predicates.add(
            builder.or(
                builder.like(builder.lower(root.get("name")), pattern, '!'),
                builder.like(builder.lower(root.get("description")), pattern, '!')));
      }
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }
}
