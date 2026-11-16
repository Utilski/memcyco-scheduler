# Quartz scheduling and task execution

Start from the repository root with `docker compose up`. The API listens on
8080 and the Vite starter frontend on 5173. MySQL persists in `mysql-data`.
Flyway owns the application and Quartz tables; Hibernate validates its mappings.
Quartz's destructive automatic schema initialization is disabled.

For a local backend, start dependencies with `docker compose up mysql rabbitmq`.
The datasource defaults to `jdbc:mysql://localhost:3306/mydatabase`, user `myuser`,
password `secret`. Override `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
and `SPRING_DATASOURCE_PASSWORD`. Set `SPRING_DOCKER_COMPOSE_ENABLED=false` when
managing the containers yourself; otherwise Spring's Compose integration supplies
connection details, including RabbitMQ's published port.

## Scheduling contract

Quartz owns recurrence, next-fire calculation, persistent triggers, misfires, and
cluster coordination. The former custom calculator and `RECURRING` type have been
removed. This is an intentional API change; the old Spring cron and recurrence
contract is superseded by the native Quartz contract below.

Create a schedule with `POST /api/schedulings`:

```json
{
  "name": "Weekday log",
  "description": "Runs at 09:00 in Jerusalem",
  "taskType": "LOG",
  "payload": { "message": "Good morning" },
  "scheduleType": "CRON",
  "timeZone": "Asia/Jerusalem",
  "startsAt": "2026-12-01T09:00:00",
  "scheduleConfig": { "expression": "0 0 9 ? * MON-FRI" },
  "endsAt": "2027-12-31T23:59:59Z",
  "maxOccurrences": 20,
  "enabled": true
}
```

Creation returns `201`, the UUID, `Location`, `ETag`, and the schedule.
`description`, `endsAt`, and `maxOccurrences` are optional; `enabled` defaults to
true. `startsAt` is a local date/time; `endsAt` and response timestamps are UTC
instants. Input precision is at most **milliseconds**. Database years are
1000–9999, although Quartz may have narrower future scheduling horizons.

| scheduleType | scheduleConfig | Quartz trigger |
| --- | --- | --- |
| `ONE_TIME` | `{}` | Non-repeating SimpleTrigger |
| `SIMPLE_INTERVAL` | `{ "interval": 30, "unit": "SECONDS" }` | Repeating SimpleTrigger |
| `CALENDAR_INTERVAL` | `{ "interval": 2, "unit": "DAY" }` | CalendarIntervalTrigger |
| `CRON` | `{ "expression": "0 0 9 ? * MON-FRI" }` | CronTrigger |

Simple interval units: `SECONDS`, `MINUTES`, `HOURS`; these measure elapsed time.
Calendar interval units: `DAY`, `WEEK`, `MONTH`, `YEAR`. Intervals must be integers
between 1 and 2147483647. Cron uses Quartz syntax, including seconds, with an
optional seventh year field. Use `?` for an unspecified day-of-month/day-of-week;
Spring expressions such as `0 0 9 * * *` are not accepted. Macros are unsupported.
Use cron to select weekdays; the former Monday-anchored multiweek weekday arrays
are no longer supported.

Calendar triggers preserve the local hour across DST and skip nonexistent hours
on subsequent occurrences. Other calendar, invalid-day, and overlap behavior is
Quartz-native; the old custom invalid-date-skipping/earlier-offset guarantees no
longer apply. Local `startsAt` is resolved using the zone's standard Java rules
(gaps shift forward; overlaps use the earlier offset). Month/year recurrence
follows Quartz's calendar arithmetic rather than custom month-end rules.

The initial trigger is advanced to Quartz's first eligible occurrence at or after
registration. An overdue one-time task runs now if its end bound permits it.
`endsAt` cannot precede `startsAt`. End bounds are applied to triggers; no eligible
fire time yields `nextRunAt: null`. Disabled schedules also expose null.

## Execution and reliability

API transactions save a durable synchronization intent. A scheduler node applies
it to Quartz, normally within one second; transient synchronization failures
remain pending for retry. `nextRunAt` initially previews the native trigger and
is subsequently mirrored from Quartz, so it can be briefly stale while an intent
is pending. Quartz jobs contain only identifiers and a generation number.

A fire transaction locks the schedule, verifies its generation, records an
immutable task/payload snapshot in `task_executions`, increments `dispatchedCount`,
and creates publication work in that same row. The pending row is the durable
outbox. Only one occurrence per schedule may remain outstanding. Its Quartz job
is paused while the worker/retries are outstanding; completion resumes it with
an explicit one-fire catch-up policy instead of replaying the whole backlog.

The publisher sends persistent messages to durable direct exchange
`scheduler.tasks`, routed to durable queue `scheduler.tasks.execute`. It marks
publication complete only after publisher confirmation and successful routing.
Unconfirmed or unroutable messages remain pending. Infrastructure failures do not
consume handler attempts. Outbox and synchronization processing uses indexed
batches of 100; there is no per-second full scan of scheduling definitions.

Consumers load the snapshot by execution UUID and acknowledge after committing
the outcome. Terminal executions ignore redeliveries. Failed handlers retry after
5 seconds and then 30 seconds, with at most three committed handler attempts.
On final failure, the corresponding schedule is disabled and its next run cleared.
Manually enabling it creates a new generation. `maxOccurrences` limits distinct
dispatches, not attempts, and applies only to repeating schedules.

| Task type | Required payload | Effect |
| --- | --- | --- |
| `LOG` | `{ "message": "Hello" }` | Application log entry |
| `DUMMY_EMAIL` | `{ "to": "person@example.com", "subject": "Hi", "body": "Example" }` | Logs a simulated email |
| `DB_QUERY` | `{ "query": "SELECT ...", "parameters": { "id": 1 } }` | Logs a simulated query; never executes SQL |

`parameters` is optional. Unknown payload fields are rejected. Handlers are short,
local operations executed under schedule/execution row locks. There is no real
email delivery or arbitrary SQL execution. A crash can repeat a log/simulated
effect before its database outcome commits: delivery is **at least once**, not an
exactly-once external-side-effect guarantee.

Timing edits, disable/re-enable transitions, and deletion invalidate older pending
work. Workers check generation/enabled state before running. Already-running
handlers may finish; edits wait for their short transaction. Metadata/payload
edits preserve queued snapshots. Timing edits reset `dispatchedCount`. A completed
one-time schedule remains complete when toggling enabled; change its timing to
schedule another execution.

Execution status, attempts, retry availability, completion timestamps and failure
details are stored in `task_executions`. Scheduling synchronization lives in
`scheduling_sync`; Quartz uses `QRTZ_*`. No execution-history API is exposed.

## Roles, clustering, and recovery

`SCHEDULER_ENABLED` and `WORKER_ENABLED` default to true. Set either to false to
run just the other role within the same built application. Scheduler-disabled
nodes do not start Quartz or publish/process synchronization intents.

Scheduler instances use a shared MySQL-backed Quartz store and unique instance
IDs. Quartz recovers persisted triggers and requests recovery of interrupted
jobs. Occurrence identity and generation checks prevent duplicate dispatch on
recovery. Pending synchronization, publication, and retries survive restarts.
A dispatch transaction failure refires the same Quartz occurrence with a one-second
backoff. Graceful shutdown waits for jobs to finish.

Keep JDBC pool capacity above the Quartz worker count plus API and outbox load.
The defaults use four Quartz threads and two background polling threads. Logs
identify failed synchronization/publication work by UUID and report worker outcomes.

## CRUD and errors

- `GET /api/schedulings`: array ordered by creation time and UUID.
- `GET /api/schedulings/{id}`: retrieve one schedule.
- `PUT /api/schedulings/{id}`: replace writable fields.
- `PATCH /api/schedulings/{id}`: update top-level fields; supplied `payload` and
  `scheduleConfig` replace their entire objects. Only optional fields accept null.
- `DELETE /api/schedulings/{id}`: delete the definition and cancel pending work;
  returns `204`. Execution history is retained.

IDs, next runs, counters, timestamps, generation, and version are server-managed.
Unknown fields, malformed JSON/UUIDs and invalid definitions return `400`; missing
records return `404`; conflicting updates return `409`, with
`application/problem+json` bodies. Send the previous `ETag` in optional `If-Match`
headers on edits/deletion. Background state changes can also advance the version.

Scheduling endpoints allow anonymous access. CORS permits localhost/127.0.0.1 on
5173; override using comma-separated `CORS_ALLOWED_ORIGINS`. List-all still has
O(n) response/memory cost; add pagination before large-scale use.

## Implementation boundaries

`QuartzScheduleSynchronizer` applies durable intents and owns Quartz registration.
`QuartzTriggerFactory` builds native triggers. `ExecutionCoordinator` owns the
transactional dispatch/worker flow; `ExecutionStore` owns its SQL persistence.
`ExecutionRetryPolicy` shares retry decisions between persistence and execution,
and `Scheduling.isCompleted()` defines completion consistently for CRUD and jobs.

`QuartzConfiguration` configures JDBC defaults while preserving explicit Quartz
properties. `AutowiringJobFactory` constructs jobs through Spring so constructor
injection works identically in production and cluster tests. `TimeConfiguration`
provides a UTC millisecond clock; tests override that Clock for deterministic time.

## Migrations and tests

V1 is retained; V2 adds execution state and V3 installs Quartz's upstream MySQL
schema without its DROP statements. No startup reset is performed. Local inspection
before this change found zero schedules, so no destructive reset was needed.
Tests use isolated disposable databases, never the local application schema.

Run `./gradlew test` in this directory. If the agent session lacks its existing
Docker group, run `sg docker -c './gradlew test --rerun-tasks --no-daemon'`.
Tests include native triggers, H2 CRUD/validation, MySQL schema/API contracts, and
real MySQL/RabbitMQ execution, retries, duplicate delivery, cancellation, concurrent
dispatch, clustered trigger acquisition, persisted-trigger replacement, and
unroutable-publication retry. Container tests skip without Docker; inspect skipped
counts before treating integration verification as complete.
