# Scheduling management and next-run calculation

**Superseded scheduling contract:** the approved
[Quartz execution plan](2026-11-16-quartz-execution.md) replaces custom recurrence
and Spring cron with native Quartz triggers and adds RabbitMQ execution. This
document remains a historical record of the initial phase.

Reconstructed on 2026-11-16 from the working tree, backend README, and tests.
The original conversation plan is unavailable. The user confirmed on this date
that the approved phase covers scheduling CRUD and next-run calculation;
dispatch and execution belong to a later phase.

## Backend README and detailed contract

The [backend scheduler README](../../backend/scheduler/README.md) is part of this
plan and preserves the detailed implementation contract recovered from the
earlier work. Read it together with this document; the checklist below does not
replace its API examples or scheduling rules.

In particular, this phase includes:

- The README's task types and exact payload shapes; DB_QUERY is stored dummy
  data and is never executed.
- All six recurring frequencies, positive integer intervals, Monday-anchored
  weekly schedules, and skipping invalid monthly/yearly calendar dates.
- Elapsed-time minute/hour intervals versus local-calendar longer intervals;
  skipping DST gaps and choosing the earlier offset for overlaps.
- Local startsAt, instant endsAt/response timestamps, microsecond precision,
  inclusive bounds, and MySQL years 1000 through 9999.
- The documented nextRunAt behavior on creation, enable/disable, and edits;
  null when no eligible occurrence remains. Progress preservation and reset
  rules follow the README.
- The CRUD request/response contract, default enabled=true, whole-object nested
  PATCH replacement, optional-field null handling, read-only fields, ETags,
  optional If-Match, and 400/404/409 problem responses.
- Anonymous scheduling API access, configurable local frontend CORS, Docker
  startup, persistent MySQL storage, and H2 plus MySQL verification.

The README's future-dispatcher paragraph is a deferred requirement, not completed
behavior: dispatch one catch-up occurrence, increment the count, advance to the
future, and stop at the configured bounds.

## Scope and acceptance criteria

- Persist scheduling definitions in MySQL using a Flyway-managed schema.
- Provide create, list, retrieve, replace, patch, and delete operations at
  `/api/schedulings`.
- Validate writable fields, task payload shapes, time zones, cron expressions,
  recurrence settings, end bounds, and occurrence limits.
- Support one-time, recurring (minute through year), and six-field cron rules.
- Compute the next eligible run using the configured time zone, documented DST
  semantics, inclusive bounds, and invalid-calendar-date skipping.
- Preserve progress for metadata edits, reset progress for timing edits, and
  recalculate the next run when enabling a schedule.
- Protect concurrent edits using optimistic locking and optional If-Match.
- Return structured errors and support the local frontend through CORS.
- Verify unit behavior and API contracts on both H2 and real MySQL, including
  JSON and microsecond timestamp round trips and migration/index creation.

## Implementation status

- [x] Model, repository, request/response types, and database migration.
- [x] Validation and next-run calculator.
- [x] Transactional service and CRUD controller.
- [x] Error handling, version checks, security/CORS, and clock configuration.
- [x] Unit, API, and MySQL integration tests.
- [x] API and scheduling semantics documented in `backend/scheduler/README.md`.
- [x] VS Code configuration adjusted; user confirmed reload resolved IDE errors.
- [x] Fresh unit and H2 API verification: 25 passed, zero failures/errors.
- [x] Fresh real MySQL verification: all 11 MySQL tests passed after launching
  Gradle with the existing Docker group membership.

## Validation

Run `./gradlew test --rerun-tasks` from `backend/scheduler`.
Inspect suite totals and skipped counts, especially `SchedulingMySqlTests`.
Fresh final run on 2026-11-16: BUILD SUCCESSFUL; all 36 tests passed, zero
failures/errors/skips. Passed suites: application context (1), H2 API (10),
validation (5), next-run calculation (9), and real MySQL (11).
The initial attempt skipped MySQL because the process lacked the Docker group.
The account already belonged to that group; rerunning with
`sg docker -c './gradlew test --rerun-tasks --no-daemon'` restored access to
Snap Docker. Final build output: `/tmp/memcyco-scheduling-tests-docker.log`.

Passing tests establish the tested contracts; they do not prove exhaustive
correctness. Fresh MySQL verification is complete for the documented phase.

## Deferred work

Due-task claiming and dispatch, RabbitMQ publishing, task consumers/execution,
dispatch counter advancement, catch-up processing, and the scheduling frontend.
No transactional outbox design was recovered from the original plan; it was a
later recommendation and is not part of this phase.

## Readability review

On 2026-11-16, the user requested descriptive naming and conventional, readable
Java. Replaced single-letter variables, added conditional braces and explicit
imports, and separated recurrence calculations and validation into focused
methods. Added the readability agreement to `AGENTS.md`.

Verification after refactoring: all 36 tests passed, including all 11 real
MySQL tests, with zero failures, errors, or skips. Log:
`/tmp/memcyco-readability-tests.log`.

## Branch review against main

Reviewed branch `backend-scheduler` at `1f9e635` against local `main` at
`849d11e` on 2026-11-16. The 27 changed files fit the confirmed phase, including
development environment fixes. This review uncovered gaps beyond the existing
36 passing tests; that test count does not establish complete correctness.

Correctness findings (resolved in the approved follow-up below):

- [x] Bound cron candidate searches by endsAt and the supported year range
  inside the search loop. A New York cron `0 30 2 ? MAR SUN#2`, starting in
  2026 and ending that December, keeps searching beyond its end through spring
  DST gaps instead of returning null. An isolated probe exceeded five seconds and was
  terminated. Add regression coverage for both finite end bounds and no end.
- [x] Stop yearly searches outside the supported range before converting the
  candidate year to int. A 2024-02-29 start with YEARLY interval 2147483647,
  evaluated in 2026, produces ArithmeticException at Math.toIntExact and thus
  HTTP 400, although the validated rule should have no supported next run.
  This was reproduced in an isolated calculator probe.

Complexity assessment:

- One-time and elapsed-time calculations use constant work rather than walking
  all historical occurrences.
- Calendar recurrence jumps close to now, then examines at most 4802 candidates;
  weekly rules examine at most seven weekdays per candidate. Work and working
  memory do not grow with the number of stored schedules.
- Cron cost depends on the candidate search. The missing termination bounds
  above must be fixed before its worst-case behavior is acceptable.
- List-all materializes every scheduling and payload: O(n) application memory
  and response work, plus database ordering that may require O(n log n) work
  without a matching created_at/id index. This matches the current list-all
  contract but needs pagination and suitable ordering indexes before large-scale
  use. Payload parsing, comparison, and serialization also scale with data size.

Conventions: production sources use descriptive names, explicit imports, braces,
and consistent formatting. The follow-up also expands wildcard test imports
and single-letter request variables. No runtime source was changed during the
review itself; the approved fixes below change the calculator.

## Approved review fixes

The user approved fixing the review findings and adding regression tests.
Bound cron searches by the end instant and database year range, stop yearly
calculations before overflow, clean up test naming/imports, then run all tests.
Keep list-all compatibility; pagination remains a documented scaling follow-up.

Implementation: cron now stops at the earlier of endsAt and the maximum UTC
instant, and skips entire DST gaps. Yearly candidates stop before overflow while
allowing local year 10000 when it still resolves to supported UTC year 9999.
Five calculator regressions cover bounds, dense DST gaps, large intervals, and
time-zone year boundaries; an API regression runs against H2 and MySQL.
Validation: all 43 tests passed, including all 12 real MySQL tests, with zero
failures, errors, or skips. The dense DST-gap regression completed in 0.048
seconds. Log: `/tmp/memcyco-review-fixes-tests.log`.
