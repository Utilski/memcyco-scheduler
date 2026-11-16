# Project working agreements

- Write readable Java with descriptive names. Avoid unnecessary abbreviations
  and single-letter variables. Use braces for conditionals, explicit imports,
  and focused methods for distinct validation and scheduling rules.

- When the user agrees to implement a plan, save the agreed plan in
  `docs/plans/` before starting implementation. Use a dated, descriptive filename.
- Record scope, acceptance criteria, implementation steps, validation, and deferred
  work. Keep completion status and verification results current so work can resume
  after an editor reload or a new conversation.
- Distinguish reconstructed plans from original agreed plans. Do not claim a
  reconstruction preserves decisions that cannot be recovered.
- Scheduling backend tests run from `backend/scheduler` with `./gradlew test`.
  That task includes native Quartz/unit tests, H2 API tests, MySQL API tests,
  and MySQL/RabbitMQ execution integration tests. The current scheduling and
  execution contract is in `backend/scheduler/README.md`; its approved plan is
  `docs/plans/2026-11-16-quartz-execution.md`.
  Check skipped counts explicitly; a skipped MySQL suite does not verify MySQL.
- Docker is installed via Snap. The `prometheus` account is already a member of
  the `docker` group, but a new agent session may lack that supplementary group.
  If Docker socket access fails, check `id` and `getent group docker`, then use
  `sg docker -c './gradlew test --rerun-tasks --no-daemon'` from the backend
  directory with the required execution approval. Do not re-add the account or
  change socket permissions when existing group membership suffices.

- Frontend conventions: functional React, strict TypeScript, Zod-validated API
  boundaries, and atomic components. Follow the dashboard plan in
  `docs/plans/2026-11-16-scheduling-dashboard.md`.
- Run frontend build/lint/tests with `npm --prefix frontend run build`,
  `npm --prefix frontend run lint`, and `npm --prefix frontend test`.
  Whole-project Playwright tests live in root `e2e/`; run `npm run test:e2e`
  (through `sg docker -c` if needed). That runner owns a separate disposable
  Compose stack and must never use development database/broker volumes.

- Keep backend scheduling code in responsibility packages: api, domain,
  persistence, service, query, execution, quartz. Moving a persisted Quartz job
  class requires a migration of QRTZ_JOB_DETAILS.JOB_CLASS_NAME.
- Keep frontend scheduling code in feature folders: api, model, hooks, utils,
  components, pages. Shared UI stays in components/atoms, molecules, templates;
  generic React hooks belong in src/hooks. Tests follow their subject folders.
