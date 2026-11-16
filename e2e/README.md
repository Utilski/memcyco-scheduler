# Whole-project E2E tests

From the repository root:

```sh
npm ci
npm --prefix frontend ci
npx playwright install chromium
npm run test:e2e
```

Requires Node.js 24+, Docker Compose, and the Docker socket. When your account is
already in the docker group but the current shell is not, use
`sg docker -c 'npm run test:e2e'`.

To watch Chromium and step through actions in Playwright Inspector, run
`PWDEBUG=1 npm run test:e2e` on a graphical desktop, or
`sg docker -c 'PWDEBUG=1 npm run test:e2e'` when this shell needs Docker group
membership. Use **Resume ▶** to run and **Step Over** to advance one action at a
time. The normal command runs headlessly.

The typed runner starts Compose project **memcyco-e2e**, waits for HTTP readiness,
runs Playwright, and tears down only that test project's containers and volumes
(including on failure). No development database/broker volume is mounted. Backend
source is mounted read-only and copied into a writable test build workspace.

Default host ports are **15173** for the UI and **18080** for the API. Override
`E2E_FRONTEND_PORT` and `E2E_BACKEND_PORT` if occupied. Do not run two copies of the
same test project concurrently. Initial execution downloads build dependencies.

Tests use the real React frontend, Spring API, MySQL 8.4 and RabbitMQ 4. They do
not intercept API calls. Coverage includes backend enum options, CRUD dialogs,
ETag conflict via a second real client, server pagination/filter/sort, deletion
from the last page, keyboard focus, a narrow viewport, and actual Quartz/RabbitMQ
execution (UI progress plus persisted SUCCEEDED status in the test database).

Browser specs, config, fixtures, runner and TypeScript config live under this
root `e2e/` directory. `npm run typecheck:e2e` checks both runner and tests.
Reports/traces/screenshots are kept in ignored `e2e/artifacts/` after teardown.
View a report with `npx playwright show-report e2e/artifacts/report`.

For debugging an already-running **test** stack, set `E2E_BASE_URL` and
`E2E_API_URL` and run `npm run test:e2e:existing`; that command does not start or
stop services. The execution test also requires the memcyco-e2e MySQL container.
Never point these mutation tests at a production environment.
