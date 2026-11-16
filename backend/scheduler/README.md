# Scheduling API

Start the application and its dependencies from the repository root with `docker compose up`.
The API runs on port 8080; the Vite frontend runs on port 5173. MySQL uses the named
`mysql-data` volume so records survive container replacement. The first migration creates
`schedulings`; Hibernate validates its schema on startup.

For an IDE/local backend, start MySQL and RabbitMQ with `docker compose up mysql rabbitmq`.
The default database URL is `jdbc:mysql://localhost:3306/mydatabase` with user `myuser` and
password `secret`. Override `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD` as needed. Disable Spring's Compose integration with
`SPRING_DOCKER_COMPOSE_ENABLED=false` when managing containers yourself.

## Create a scheduling

Send `POST /api/schedulings` with `Content-Type: application/json`:

```json
{
  "name": "Morning log",
  "description": "Every other week on Monday and Friday",
  "taskType": "LOG",
  "payload": { "message": "Good morning" },
  "scheduleType": "RECURRING",
  "timeZone": "Asia/Jerusalem",
  "startsAt": "2026-12-01T09:00:00",
  "scheduleConfig": {
    "frequency": "WEEKLY",
    "interval": 2,
    "weekdays": ["MONDAY", "FRIDAY"]
  },
  "endsAt": "2027-12-31T23:59:59Z",
  "maxOccurrences": 20,
  "enabled": true
}
```

Successful creation returns `201`, a UUID, `Location`, `ETag`, and the complete scheduling.
`description`, `endsAt`, and `maxOccurrences` are optional; `enabled` defaults to true.
All other fields shown are required. `startsAt` is local time without an offset;
`endsAt` and response timestamps are UTC instants. Date precision is at most six fractional digits.

Task types and payloads:

| Type | Payload |
| --- | --- |
| `LOG` | `{ "message": "Hello" }` |
| `DUMMY_EMAIL` | `{ "to": "person@example.com", "subject": "Hello", "body": "Example" }` |
| `DB_QUERY` | `{ "query": "SELECT * FROM example WHERE id = :id", "parameters": { "id": 1 } }` |

`parameters` is optional. DB_QUERY is a **dummy task**, just like DUMMY_EMAIL:
query text is stored as data and never executed. Payloads reject unknown fields.
Task execution, email delivery, logging handlers, and RabbitMQ publishing are not implemented in this phase.

## Schedule options

| scheduleType | scheduleConfig |
| --- | --- |
| `ONE_TIME` | `{}` |
| `RECURRING` | `{ "frequency": "DAILY", "interval": 2 }` |
| `RECURRING` (weekly) | `{ "frequency": "WEEKLY", "interval": 1, "weekdays": ["MONDAY", "WEDNESDAY"] }` |
| `CRON` | `{ "expression": "0 0 9 * * MON-FRI" }` |

Recurring frequencies: `MINUTELY`, `HOURLY`, `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`.
Intervals must be positive integers. Only weekly rules accept `weekdays`; weekly rules require
at least one unique day name. Weeks start Monday and are anchored to the week containing `startsAt`.
Monthly and yearly rules keep the original day and time, skipping invalid dates.
Minute/hour intervals measure elapsed time; longer intervals follow the local calendar.
Daylight-saving gaps are skipped; repeated local times use the earlier offset and run once.
A one-time start in a daylight-saving gap has no occurrence. An elapsed-time rule whose
initial anchor is in such a gap also has no occurrence; choose an existing local start time.

Cron accepts exactly six Spring cron fields: second, minute, hour, day of month, month, day of week.
Five-field expressions and macros are rejected. Impossible calendar rules may have no next run.

`startsAt` and `endsAt` are inclusive. `endsAt` cannot precede the start.
`maxOccurrences` is a positive limit for recurring/cron schedules only.
Dates must fall within MySQL's supported years 1000–9999.

The server computes `nextRunAt` on creation and timing edits. Overdue one-time schedules
are due now; recurring/cron definitions begin at their first eligible occurrence at or after now.
Disabled schedules have `nextRunAt: null`; enabling recalculates it. Null can also mean no
eligible occurrence remains. Metadata/payload edits preserve scheduling progress; changes to
schedule type, time zone, start, settings, end, or occurrence limit reset `dispatchedCount`.

For the future dispatcher: an already-stored overdue schedule should dispatch **one** catch-up
occurrence, then advance to the next future occurrence. Increment `dispatchedCount` when dispatching,
stop at `maxOccurrences`, and clear `nextRunAt` on completion. This behavior is documented here,
not implemented by CRUD. Nothing increments the counter or dispatches tasks yet.

## CRUD and errors

- `GET /api/schedulings`: array ordered by creation time and UUID.
- `GET /api/schedulings/{id}`: retrieve one scheduling.
- `PUT /api/schedulings/{id}`: full replacement of writable fields.
- `PATCH /api/schedulings/{id}`: partial top-level update. Supplied `payload` and `scheduleConfig`
  replace their entire object. Explicit null is allowed only for optional fields.
- `DELETE /api/schedulings/{id}`: permanent deletion, returning `204`.

`id`, `nextRunAt`, `dispatchedCount`, timestamps, and `version` are read-only.
Unknown fields, malformed UUIDs/JSON, and validation errors return `400`; missing records return
`404`; conflicting updates return `409`. Errors use `application/problem+json`.
Send the last response's `ETag` as `If-Match` on PUT/PATCH/DELETE to reject stale edits.
The header is optional; JPA also detects overlapping database transactions.

The scheduling API permits anonymous access. CORS allows `http://localhost:5173` and
`http://127.0.0.1:5173`; override with the comma-separated `CORS_ALLOWED_ORIGINS` variable.

## Tests

Run `./gradlew test` in this directory. Tests cover recurrence, validation, CRUD, persistence,
optimistic locking, and CORS using H2 in MySQL mode. The same API suite also runs against an
isolated real MySQL 8.4 instance using Testcontainers, including migration/index checks.
MySQL tests are skipped when Docker is unavailable.
