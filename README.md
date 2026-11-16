# Memcyco scheduling workspace

## Run the whole-project E2E tests

Run these commands from the repository root. Requires **Node.js 24+**, Docker
Compose, and access to the Docker socket.

First-time setup:

```sh
npm ci
npm --prefix frontend ci
npx playwright install chromium
```

**Watch the browser and step through the tests** (requires a graphical desktop):

```sh
PWDEBUG=1 npm run test:e2e
```

Chromium and Playwright Inspector open. Click **Resume ▶** to run, or **Step Over**
to advance one action at a time.

For a normal headless run:

```sh
npm run test:e2e
```

If your account belongs to the Docker group but this shell has not inherited it,
use these commands instead:

```sh
sg docker -c 'PWDEBUG=1 npm run test:e2e'
sg docker -c 'npm run test:e2e'
```

After either run, open the HTML report:

```sh
npx playwright show-report e2e/artifacts/report
```

The runner starts a separate frontend/backend/MySQL/RabbitMQ stack and removes
its test containers and volumes afterward. Development data is unaffected.
Default test ports are **15173** and **18080**. See [E2E details](e2e/README.md)
for port overrides, artifacts, and troubleshooting.

## Project overview

Spring Boot + Quartz + RabbitMQ backend and React scheduling dashboard.

- Run locally: `docker compose up` (UI http://localhost:5173, API port 8080).
- [Backend contract and execution flow](backend/scheduler/README.md).
- [Frontend structure and development](frontend/README.md).
- [Whole-project browser tests](e2e/README.md).
- [Standalone Dockerfiles and docker run commands](docker/README.md).
- [Approved dashboard plan](docs/plans/2026-11-16-scheduling-dashboard.md).

Install frontend dependencies with `npm --prefix frontend ci`. Run its checks with
`npm --prefix frontend run build`, `npm --prefix frontend run lint`, and
`npm --prefix frontend test`.

Backend tests: `cd backend/scheduler` and `./gradlew test`. Integration tests need
Docker; verify skipped counts. Root E2E: `npm ci`, install Chromium with
`npx playwright install chromium`, then `npm run test:e2e`. It builds a separate
Compose stack with disposable test data and removes it afterward.
