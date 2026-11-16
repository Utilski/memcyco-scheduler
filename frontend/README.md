# Scheduling dashboard

Dark React/TypeScript dashboard for the Spring scheduling API. Supports guided
create/edit dialogs, complete schedule details, enable/disable and delete
confirmations, backend-driven task types, and server-side pagination/search/sort.

## Run

From this directory: `npm ci` then `npm run dev`. Start the backend/dependencies
from the repository root with `docker compose up`. The UI defaults to
`http://localhost:8080`; override `VITE_API_BASE_URL` in the frontend environment
before starting Vite/building (see `.env.example`). This variable is public.

## Checks

- `npm run build`: strict TypeScript and production bundle.
- `npm run lint`: ESLint including React hooks checks.
- `npm test`: Vitest/Testing Library unit and component tests.
- `npm run format`: format source with Prettier.
- Whole-project E2E tests run from the **repository root**: `npm run test:e2e`.
  See `../e2e/README.md`; they use isolated real services, not mocked responses.

## Structure

- `components/atoms`: controls and badges.
- `components/molecules`: fields, accessible dialog shell and pagination.
- `components/templates`: workspace layout.
- `features/schedulings/api`: validated HTTP client and API tests.
- `features/schedulings/model`: domain schemas, discriminated types and form types.
- `features/schedulings/hooks`: remote query and action-dialog state orchestration.
- `features/schedulings/utils`: pure form, date-validation and formatting helpers.
- `features/schedulings/components`: table/form/dialog organisms and feature UI.
- `features/schedulings/pages`: dashboard composition.
- `hooks`: generic reusable React hooks; `lib`: shared error presentation.
- `test`: environment setup and shared fixtures.

TanStack Query owns remote data and invalidation. Collection keys include every
server-side query parameter. Requests are cancellable, results refresh every ten
seconds while visible, and previous pages remain visible with an updating state.
Dialogs fetch a fresh record/ETag. Open form drafts do not follow list refreshes;
stale edits show a conflict and allow explicit reload rather than overwriting.

Task select options come from `GET /api/schedulings/task-types`. Unknown future
types remain viewable but cannot be edited until a matching form adapter exists.
Zod validates API data and request construction; server validation remains
responsible for Quartz cron syntax and timezone-aware time bounds.

Starts are local times in the chosen IANA timezone. Optional end dates are
explicitly UTC; utilities preserve milliseconds without parsing local starts in
the browser timezone. Counts/version values must be safe JavaScript integers.
A null next run is never labeled as proof of execution success.

## Standalone image

Build from the repository root with
`docker build --build-arg VITE_API_BASE_URL=http://localhost:18080 -t memcyco-frontend:local frontend`.
The runtime is unprivileged Nginx on container port 8080, serving the production
bundle with SPA fallback. The API URL is public build-time configuration, not a
runtime environment override. See [standalone service commands](../docker/README.md).
