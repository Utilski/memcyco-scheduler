# Scheduling management and next-run calculation

Reconstructed on 2026-11-16 from the working tree, backend README, and tests.
The original conversation plan is unavailable. The user confirmed on this date
that the approved phase covers scheduling CRUD and next-run calculation;
dispatch and execution belong to a later phase.

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
