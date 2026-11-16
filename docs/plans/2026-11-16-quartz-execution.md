# Quartz-native scheduling and RabbitMQ execution

Approved 2026-11-16. This supersedes the reconstructed plan's custom recurrence
contract. The backend README will describe the new contract at completion.

## Agreed contract

- Quartz owns recurrence, next-fire times, persistent triggers, misfires, and
  clustering. Remove NextRunCalculator and Spring cron parsing.
- Types: ONE_TIME (empty config), SIMPLE_INTERVAL (positive interval plus
  SECONDS/MINUTES/HOURS unit), CALENDAR_INTERVAL (positive interval plus
  DAY/WEEK/MONTH/YEAR unit), CRON (Quartz six/seven-field expression).
- Millisecond scheduling precision; calendar triggers preserve local hour and
  skip nonexistent DST hours. Otherwise native Quartz calendar/DST semantics.
- Existing CRUD/task payloads remain. maxOccurrences counts dispatches, not
  retries. One outstanding execution per schedule. One catch-up, not a backlog.
- Durable clustered JDBC Quartz, Flyway schema, outbox publishing with confirms,
  RabbitMQ consumers in the same module; scheduler/worker roles configurable.
- Three handler attempts with 5s/30s durable retry delays; final failure disables
  the matching schedule generation. Infrastructure failures retry indefinitely.
- Pending older-generation executions are canceled by timing edits, disable/
  re-enable, and deletion. Running handlers may finish; metadata edits preserve
  payload snapshots. Execution history stays in database/logs, no history API.
- MySQL data is disposable (user confirmed empty outside tests). Inspect before
  any explicit project-schema reset; never reset unrelated schemas or broker.
- Durable CRUD synchronization, startup recovery, stale-trigger rejection,
  bounded indexed outbox batches of 100 every second; no per-tick full scan.
- At-least-once deliveries, terminal-execution deduplication, and DB row locking.
  Logging/simulated effects may repeat after a crash; no exactly-once promise.

## Implementation and validation

- [x] Save approved plan before implementation.
- [x] Add Quartz starter and Flyway-managed Quartz/execution/outbox schema.
- [x] Replace recurrence model, validation, calculation, and tests with native triggers.
- [x] Synchronize CRUD and persisted Quartz jobs/triggers using durable intents.
- [x] Implement transactional occurrence dispatch, cancellation and generation checks.
- [x] Implement confirmed RabbitMQ publishing, workers, retries, and final failure.
- [x] Add native-trigger unit tests plus H2 CRUD and real MySQL/RabbitMQ tests.
- [x] Verify restart recovery, two scheduler instances, duplicate messages,
      cancellation, count limits, publisher failures, and retry behavior.
- [x] Update README, format changed Java, and pass the full suite with no skips.

## Test approach

Keep independent CRUD tests deterministic by disabling background processing.
Add focused trigger tests and integration tests with real MySQL and RabbitMQ,
including persisted Quartz tables, restart/cluster behavior, and broker confirms.
Run `sg docker -c './gradlew test --rerun-tasks --no-daemon'` in backend/scheduler.
Retain list-all API compatibility; pagination is a separate scaling follow-up.

## Implementation notes

- MySQL inspection found zero schedules in the local project schema. Kept V1
  intact and added V2/V3 migrations; no destructive reset was necessary.
- Quartz schema is the upstream 2.5.2 MySQL/InnoDB schema, with DROP statements
  removed and comments made portable for H2 tests.
- Native trigger builders preserve DST flags when advancing initial start times.
  Maximum end bounds prevent Quartz dates outside MySQL storage range.
- Execution rows double as the message outbox: committed pending work retains its
  payload snapshot and publication/retry state. Terminal rows deduplicate delivery.
- Consumers hold the schedule and execution locks while running these short local
  handlers. No external network handlers are implemented; logs may repeat on crash.
- CRUD synchronization is eventual (normally one polling interval); failed sync
  remains durable. Quartz recovery jobs retain the original occurrence identity.

## Verification progress

- Initial complete integration run: 46 tests passed, no failures/errors/skips,
  including MySQL API contracts and real MySQL/RabbitMQ execution tests.
- Final suite: **49/49 passed**, zero failures/errors/skips. Breakdown: 12
  execution integration (MySQL/RabbitMQ), 12 MySQL API, 11 H2 API, 6 native
  triggers, 6 validation, 1 recovery identity, and 1 application context. Includes
  recovered-job identity, native pause/resume catch-up, and simulated handlers.
  Log: `/tmp/memcyco-quartz-tests.log`; build successful in 49 seconds.
- Local `docker compose up -d backend` succeeded. Flyway applied V2/V3 to the
  inspected empty application database; Quartz started clustered and connected
  to RabbitMQ. MySQL and RabbitMQ containers remained healthy.
- Java formatted with the existing Google Java formatter on Java 21; source
  compiles/runs with the project's Java 27 toolchain.

## Spring conventions and maintainability review

Requested after integration: review all methods, configuration, duplication,
utility setup, and transaction boundaries; fix issues and rerun the full suite.

- [x] Extract focused Quartz synchronization service; shorten trigger-building
  methods and keep transactional entry points on public Spring service methods.
- [x] Share production/test Quartz job factory and constructor injection.
- [x] Centralize retry/completion rules and typed execution readiness; configure
  the production Clock at millisecond precision instead of repeated truncation.
- [x] Preserve publisher interruption and stop polling on interrupted shutdown.
- [x] Add regressions for completed one-time enable transitions and interrupted
  confirms; retain real MySQL/RabbitMQ/cluster coverage.
- [x] Format and run all tests; record final results.

Review validation: **51/51 tests passed**, zero failures/errors/skips, including
13 MySQL/RabbitMQ execution tests and 12 MySQL API tests. Log:
`/tmp/memcyco-quartz-review-tests.log`; build successful in 49 seconds.

Quartz synchronization is now a separate public transactional service. Job
construction uses one shared Spring-aware factory with constructor injection;
production and cluster tests use the same path. Trigger construction has focused
helpers, completion lives on the scheduling domain object, and retry decisions
have one shared policy. Status mapping is typed and payload decoding uses a
TypeReference instead of unchecked casts. Configuration customizers preserve
explicit user settings. Publisher shutdown interruption is preserved, and
completed one-time schedules cannot restart solely by toggling enabled.
No schema migration or public request-shape change was required for this review.
