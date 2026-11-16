# Scheduling dashboard with server-side pagination and project E2E

Approved 2026-11-16. Dark React dashboard, guided atomic-design CRUD dialogs,
backend-enum task-type options, server-side pagination/search/filter/sort, and
whole-project Playwright tests in root e2e/. Save before implementation.

## Contract

- GET /api/schedulings returns {items,page,size,totalItems,totalPages}, page=0,
  size=20 (1..100), optional literal case-insensitive search across name/description,
  optional enabled boolean, sort=name/taskType/scheduleType/enabled/startsAt/
  nextRunAt/createdAt/updatedAt (default createdAt), direction=asc/desc (default asc).
  Append UUID tie-breaker. Invalid parameters 400, beyond-last-page empty items.
- Spring Data JPA specifications and Pageable push filtering/count/order/limit to
  the database. Dedicated response DTO, indexes for default/enabled ordering.
- GET /api/schedulings/task-types returns TaskType.values enum names. Frontend
  select loads it, shows loading/error/retry, rejects unsupported new types for edit.
- Create button above table; View/Edit/Enable/Disable/Delete row dialogs. Guided
  task payloads and all four Quartz types. Only DB parameters use JSON textarea.
- All user-relevant fields visible via neat summary columns and full View dialog.
  Search debounced 300ms, page size 10/20/50/100, filters/sort reset page0, deleting
  last row clamps page. No inference of success from a null next run.
- startsAt is local IANA-zone time, endsAt explicitly UTC; preserve milliseconds.
- GET fresh ETag when opening existing-record action; If-Match for mutations.
  Preserve drafts on errors/409 and offer explicit reload, never silent overwrite.
- TanStack Query keys include collection parameters; abort obsolete requests,
  invalidate collection after mutations, refresh every10s visible without
  overwriting open forms. Keep previous data with updating feedback.
- Zod runtime validation, strict TS/discriminated domain+dialog types, pure shared
  helpers, small functional atomic components, immutable state, no any/casts of
  unvalidated API responses. Native accessible dialogs, keyboard focus restoration,
  responsive table scrolling, dark shared CSS tokens. API URL configurable, default8080.

## Implementation checklist

- [x] Save approved plan.
- [x] Paginated backend/enum metadata API, indexes, H2+MySQL contract tests.
- [x] Typed frontend API, schemas, helpers and TanStack Query hooks.
- [x] Atomic UI, guided dialogs and paginated dark dashboard.
- [x] Frontend unit/component tests, strict build and lint.
- [x] Root e2e/ Playwright suite and root execution command; isolated Compose stack
  with actual frontend/backend/MySQL/RabbitMQ, no mocked API responses.
- [x] E2E verifies CRUD, enum options, full-dataset search/filter/sort/pagination,
  last-page deletion, conflict via real second client, keyboard/mobile and actual
  one-time LOG execution. Unique fixtures cleaned; retain reports/traces on failure.
- [x] Run all suites, document startup/test commands and record final results.

## Defaults and boundaries

No UI framework; add TanStack Query and Zod, Vitest/Testing Library, Playwright.
Keep existing CRUD mutation shapes except collection response changes. Backend
remains final validator of Quartz syntax. Numbered pagination uses database counts;
substring search and arbitrary sorts may scan/sort, indexes do not remove that cost.
E2E data/broker volumes are separate from development; clean only test resources.

## Verification progress

- Backend: 61/61 passed, zero failures/errors/skips, including H2 and real MySQL
  collection metadata, filtering/search/sort/pagination plus execution tests.
  Log: `/tmp/memcyco-dashboard-backend-tests.log`.
- Frontend strict production build and ESLint pass. Eight initial unit/component
  tests passed; API cancellation/parameter/error tests added for the final run.
- Root E2E runner is TypeScript-checked. Stack setup corrected to copy read-only
  backend source into a writable Gradle workspace. Test volumes remain isolated.
- Browser verification complete: all 4 real-service Chromium E2E tests passed
  (18.3s). Includes CRUD/ETag conflicts, server paging/filter/sort and final-page
  deletion, narrow layout/keyboard focus, and a Quartz/RabbitMQ execution checked
  as SUCCEEDED in the test database. Test containers/volumes were removed.
- Final frontend: 11/11 unit/component/API tests passed; production build, ESLint,
  and strict root E2E TypeScript checking passed.
- Development frontend/backend were recreated to load new dependencies and V4.
  http://localhost:5173 was checked in Chromium with no page errors or alerts.
  Visual artifact: `e2e/artifacts/dashboard.png` (ignored).
- Runner uses typed Node 24 `run.mts`; early startup detects exited containers.
  Initial startup defects (read-only Gradle workspace and a missing JS import)
  were corrected before the successful run; no failed tests were skipped.

## Final conventions review and organization

User requested full review, fixes, final regression suites, and named folders.

- [x] Group frontend feature code into api/model/hooks/utils/components/pages;
  keep shared atoms/molecules/templates and generic debounce hook outside it.
- [x] Group backend into api/domain/persistence/service/query/execution/quartz.
  V5 updates Quartz's persisted class name; regression preserves unrelated jobs.
- [x] Split action-dialog fetching/mutations, content and confirmations; share
  test fixtures and date validation instead of duplicate inline logic.
- [x] Reject impossible dates before JavaScript can normalize them; validate
  known payload/configuration responses before editing, while allowing unknown
  future task types to remain viewable. Add date/type/missing-record regressions.
- [x] Run final backend, frontend, strict build/lint/type checks and root E2E.

Final review results: **62/62 backend**, **22/22 frontend**, and **4/4 root
real-service Chromium E2E tests passed**, with zero skips. Frontend production
build, ESLint, and root runner/test TypeScript checks passed. Logs:
`/tmp/memcyco-final-review-backend.log` and `/tmp/memcyco-final-review-e2e.log`.
The first post-move backend check exposed three incorrect test imports, which
were fixed. The first browser run exposed a test synchronization race: the test
navigated pages before its debounced search applied. It now waits for the matching
real search response; all four tests pass without retries or skipped cases.
V5's MySQL regression verifies only the renamed Quartz job is updated. The
local backend is reloaded to pick up the reorganized classes and migration.
