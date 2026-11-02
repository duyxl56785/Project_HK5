# Commands — Codex Terminal (Linux)

Working directory: `/workspace/tv3-t1t2-main-integration/backend`. Java 21.0.8+9, Gradle 8.10, Docker local Unix socket. Node 24.19.0. PostgreSQL Testcontainers: `postgres:16-alpine` (focused) and `postgres:16` (Auth); no shared/demo DB used for regression.

First invocation (exit 1, Auth initdb disk exhaustion):

```bash
source /workspace/.setup/java-proxy.sh
env -u DOCKER_HOST -u DOCKER_CONTEXT -u DOCKER_TLS -u DOCKER_TLS_VERIFY -u DOCKER_CERT_PATH DOCKER_HOST=unix:///var/run/docker.sock ./gradlew --init-script /tmp/tv3-pr145-sync-20261102/build-output.init.gradle :order-service:test :order-service:postgresIntegrationTest :cart-service:test :cart-service:postgresIntegrationTest :inventory-service:test :inventory-service:postgresIntegrationTest build --rerun-tasks --no-parallel --max-workers=1 --no-daemon --console=plain
```

Three subsequent invocations used the same command without `--rerun-tasks`:

```bash
source /workspace/.setup/java-proxy.sh
env -u DOCKER_HOST -u DOCKER_CONTEXT -u DOCKER_TLS -u DOCKER_TLS_VERIFY -u DOCKER_CERT_PATH DOCKER_HOST=unix:///var/run/docker.sock ./gradlew --init-script /tmp/tv3-pr145-sync-20261102/build-output.init.gradle :order-service:test :order-service:postgresIntegrationTest :cart-service:test :cart-service:postgresIntegrationTest :inventory-service:test :inventory-service:postgresIntegrationTest build --no-parallel --max-workers=1 --no-daemon --console=plain
```

Exit sequence: 1 (HBA copied file permission), 1 (Kafka disk exhaustion), 0 (full successful build). Already successful tasks were UP-TO-DATE, counted once. Test code, source flags and assertions unchanged. Output-only init script protects demo mounted JARs.

Frontend: copy exact merged `package.json` + lock to disposable node runtime; `npm ci` exit 0; temporarily link this worktree's absent `node_modules` to it, `npm run typecheck` exit 0; remove only the link created by this run. No frontend source changes.

Recovery: reclaimed only known TV3-owned unused Buildx cache records and unused prior network-probe Node image; relocated ignored, unmounted prior build JARs to `/tmp`, preserving bytes and logical symlink paths. Restored non-secret `docker/postgres/pg_hba.conf` file mode from 0600 to canonical readable 0644 (content unchanged). No Docker daemon/global setup, source, flags, assertions or demo volumes modified.
