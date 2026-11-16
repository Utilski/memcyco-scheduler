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
