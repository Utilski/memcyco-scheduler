# Project working agreements

- When the user agrees to implement a plan, save the agreed plan in
  `docs/plans/` before starting implementation. Use a dated, descriptive filename.
- Record scope, acceptance criteria, implementation steps, validation, and deferred
  work. Keep completion status and verification results current so work can resume
  after an editor reload or a new conversation.
- Distinguish reconstructed plans from original agreed plans. Do not claim a
  reconstruction preserves decisions that cannot be recovered.
- Scheduling backend tests run from `backend/scheduler` with `./gradlew test`.
  That task includes unit tests, H2 API tests, and Testcontainers MySQL tests.
  Check skipped counts explicitly; a skipped MySQL suite does not verify MySQL.
- Docker is installed via Snap. The `prometheus` account is already a member of
  the `docker` group, but a new agent session may lack that supplementary group.
  If Docker socket access fails, check `id` and `getent group docker`, then use
  `sg docker -c './gradlew test --rerun-tasks --no-daemon'` from the backend
  directory with the required execution approval. Do not re-add the account or
  change socket permissions when existing group membership suffices.
