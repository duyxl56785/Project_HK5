# TV3 — live acceptance on approved isolated demo

**2026-11-01: 3 PASS / 0 FAIL / 2 NOT_RUN–BLOCKED, 3 executed, 0 skipped.** This supersedes the earlier five-NOT_RUN status for the three executed cases only. No complete Checkout/website, production recovery, Finance policy or deployment approval is claimed.

## Source and authorization

- Tested application HEAD: **`bf8eb3a027c72ad9fde81d0aa5dabc4e73bebacf`**, branch `feat/tv3-checkout-durable-draft-journal`.
- Included main: `72a12d7536d2644a0f2fbae6dd9e07928162cb1d`. This merge was already present locally; the fresh merge check returned `Already up to date`. No conflict/rebase/force push.
- TV2 [review 5728410521](https://github.com/duyxl56785/Project_HK5/pull/145#pullrequestreview-5728410521) is COMMENT at `593ac8d`, accepts Product/Store/Seller boundary, private draft/PII boundary and the local demo stack for five acceptance cases. It does not APPROVE the PR; TV4/TV5 conditions remain. [Visible review readback](TV2_REVIEW_READBACK.md) was obtained from GitHub HTML; REST/GraphQL remained 403.
- Backend/frontend/Docker/demo application source at `bf8eb3a` is identical to the reviewed `593ac8d`; intervening main changes were QA/docs/test scripts. Backend JARs were rebuilt from `bf8eb3a`. The frontend image was reused from the identical tracked frontend Git tree; see [frontend-image.json](frontend-image.json). No production code was modified for these tests.
- The five TypeScript files came byte-for-byte from acceptance branch `feat/tv3-two-store-acceptance-review` at `ecdb03c3b5180d2c055b99a673bb93ee0ee221cc`. Assertions/test bodies/helpers were not changed. [Provenance](provenance.json) records hashes. Only the copied README is updated with current results.

## Executed cases

| Existing acceptance case | Result | Exit | Evidence |
|---|---|---:|---|
| Browser: two Stores, exact stock delta, same-key replay | **PASS** | 0 | [console](cases/two-store/console.log), [JUnit](cases/two-store/junit.xml), [before](cases/two-store/before/), [after](cases/two-store/after/) |
| Claim-conflict UNKNOWN | **NOT_RUN / BLOCKED** | N/A | Missing TV4/TV5 approved real fault fixture/receipt; [prerequisites](fault-prerequisites.json) |
| Timeout UNKNOWN | **NOT_RUN / BLOCKED** | N/A | Missing TV4/TV5 approved real fault fixture/receipt; [prerequisites](fault-prerequisites.json) |
| Customer cancel + Seller reject: RELEASE once | **PASS** | 0 | [console](cases/release/console.log), [JUnit](cases/release/junit.xml), [before](cases/release/before/), [after](cases/release/after/) |
| DISPATCHED: CONSUME once, no extra CONSUME on delivery | **PASS** | 0 | [console](cases/consume/console.log), [JUnit](cases/consume/junit.xml), [before](cases/consume/before/), [after](cases/consume/after/) |

All three ran once with retries=0; there was no failed acceptance assertion or hidden failed rerun. The two UNKNOWN cases were not selected/executed, not skipped into a green total. Discovery found all five. No fault endpoint/response, SQL mutation, mocked route, fake JWT or synthetic fault receipt was used.

The browser case used **system Chromium 154.0.8037.57 and Playwright**, real Auth/Gateway/Cart/Catalog/Order/Inventory/PostgreSQL. It created one root Order/two SellerOrders, verified available −2 / reserved +2 for both real SKUs, then sent three concurrent successful replays using the actual browser submission/key and verified no new Orders, operations or stock changes. This is real browser E2E for that positive demo case, not HappyDOM or an HTTP fixture.

Lifecycle cases use real browser login + real HTTP and PostgreSQL. Cancellation/rejection each release exactly the corresponding held quantity; repeated terminal actions create no extra operation. Dispatch is observed as DISPATCHED, one CONSUME is awaited before delivery, and delivery leaves operations/stock unchanged. The existing consume case does not inject response loss or perform a separate repeated-dispatch request; do not extend its proof to those scenarios.

## Order / Inventory proof

| Case | Root Order ID | New Inventory operations |
|---|---|---|
| two-store | `b0c34d99-98ac-4aed-9830-0e4a9bd1f0da` | 1 RESERVE |
| release | `cf77f2cf-863d-40ea-b849-7403a2d588dd` | 1 RESERVE + 2 RELEASE |
| consume | `cc4c3284-02ac-428e-af8c-e0d1b59afe7b` | 1 RESERVE + 1 CONSUME |

[results.json](results.json) contains complete operation IDs, SellerOrder IDs and per-SKU before/after balances. The readonly [initial/final snapshots](snapshots/) and per-case snapshots export only synthetic IDs, statuses, money, stock, Flyway history and hashes. They omit shipping fields, idempotency keys, passwords/tokens and raw draft/outbox payloads. [SQL selectors](READONLY_ORACLE_QUERIES.sql) run inside `BEGIN READ ONLY` / `ROLLBACK`; they are not DB dumps.

Additional [readonly frozen-fact checks](frozen-facts-readonly.json) are true for all three orders: root/child/item IDs, Product/SKU/quantity, exact money, fingerprint/customer binding, payload SHA256, persisted shipping equality and exact frozen outbox payload. Only a boolean shipping comparison is exported. Each Order has two SellerOrders and exactly one order.placed outbox row. Current source retains all three successful drafts, consistent with the outstanding TV2 retention observation; no automatic cleanup was added.

## Environment / commands

Codex Terminal, Linux, Docker 28.4.0 on the managed local socket; Node 24.19.0, Temurin Java 21.0.8+9. Live database: **PostgreSQL 16.15**. No Windows Docker Desktop, SSH/VPN or exposed Docker API.

Disposable Compose project: **`tv3-live-145-20261101`**, created only after verifying no container/volume with that project label existed. The previous TV3-created `hk5-demo` containers were stopped/removed for capacity after verifying IDs/working-directory labels; its volumes were preserved. No shared database/volume was overwritten or deleted. New worktree: `/workspace/tv3-pr145-live-acceptance`. Only its local demo opt-ins are enabled.

The official `scripts/demo/up.mjs --approved-inventory-pair --approved-finance-policy` startup logic is used with a temporary external runner that fixes the worktree path and passes a Compose override to reuse the already-built identical frontend image. Actual startup command:

```bash
COMPOSE_PROJECT_NAME=tv3-live-145-20261101 GATEWAY_URL=http://localhost:18080 \
node /tmp/tv3-live-acceptance-145-20261101/up-runtime.mjs \
  --skip-build --approved-inventory-pair --approved-finance-policy
```

Exit 0; readiness + official API seed completed. Backend build had already passed from this HEAD; `--skip-build`/`--no-build` avoid duplicate builds, not failures. Managed-cloud adaptations were kept outside source: writable Buildx cache, public proxy CA for the original frontend image build with TLS verification on, readable non-secret init-file modes, ignored JARs stored in `/tmp` with hashes to fit the 32 GiB filesystem. No runtime flag outside this demo was changed. [up.log](up.log), [JAR hashes](built-jars.json), [previous-stack preservation](prior-stack-preserved.json).

The external credential-safe driver reads only the newly generated local `.env.demo`, verifies real Auth roles and the empty dedicated Customer Cart, and selects two real Product IDs/SKUs in different seed Stores. Seller2 manages the second Store. It never prints/commits passwords or JWTs. [Public fixture metadata](fixtures.public.json).

Actual separate invocations (exit 0 each):

```bash
node /tmp/tv3-live-acceptance-145-20261101/run-live.mjs two-store
node /tmp/tv3-live-acceptance-145-20261101/run-live.mjs release
node /tmp/tv3-live-acceptance-145-20261101/run-live.mjs consume
```

Each invokes the existing suite using the recorded [runtime config](runtime.config.used.ts), `--project two-store` or `--project tv4-lifecycle --grep ...`; [command.json](cases/two-store/command.json) and corresponding case files contain exact CLI arguments, exit code and source binding. Runtime config only selects `/usr/bin/chromium`, report paths and trace/video/screenshot off; it does not alter assertions/network. Owner reproduction with installed bundled Chromium may use the original `live.config.ts` and documented `npx playwright test` commands.

## Regression and remaining gates

Fresh backend command, exit 0:

```bash
./gradlew :order-service:test :order-service:postgresIntegrationTest \
  :cart-service:test :cart-service:postgresIntegrationTest \
  :inventory-service:test :inventory-service:postgresIntegrationTest \
  bootJar --rerun-tasks --no-daemon --console=plain
```

**329 PASS / 0 FAIL / 0 SKIP**: Order 156 unit + 103 PostgreSQL; Cart 11 + 13; Inventory 27 + 19. See [regression results/XML/log](regression/). Unit tests may use mocks; PostgreSQL tests are Testcontainers and some Order peers are loopback fixtures. This is separate from the three live cases and the older candidate 329 PASS. `npm run typecheck` also passed, exit 0, including the copied acceptance TypeScript.

- **TV4 + TV5:** supply separate claim-conflict and timeout fixture files, original customer/key/body/attemptId/operationId/type/SKU/quantity, active real fault mechanism, settled initial outcome, approved receipt + evidence file/SHA256. Then run those two cases; do not invent a production fault API or use SQL to force state.
- **TV4 + TV5 / maintainer:** independent PR #145 source/migration/Finance/UAT review still required. TV2 COMMENT is not blanket APPROVE.
- **TV2 observation:** draft retention after success remains unchanged; a cleanup/retention decision needs owner review. Inventory response contains total available/reserved balances, not echoed per-operation quantity; no invented quantity comparison or DTO change was added. Exact live stock deltas are independently verified here.
- **TV1:** current-authority/write-fence remains as implemented. Ordinary real login/role/business-write paths were exercised; revoke races, stale credentials, cross-service recovery authority and broader Auth acceptance were not proved.
- Require fresh CI at published PR HEAD. Updating PR description/reading CI through REST/GraphQL may remain blocked by 403; publication status is reported separately, never inferred from old SUCCESS.
- PR #144 was not reviewed, approved, commented on, resolved or otherwise changed. No PR merge or production deployment.

## Integrity and privacy

`SHA256SUMS` covers published evidence except itself. `provenance.json` maps raw to published hashes and records projections. Private reporter files, error contexts, credentials, `.env.demo`, Docker secrets, binaries and DB dumps remain excluded. Published console logs have known runtime credentials/JWTs redacted if present; XML strips properties/system-out/err/attachments/hostnames. Synthetic stock/financial facts are deliberately retained for review. Original raw evidence remains private and unchanged.

The generated backend console log preserves Gradle's original trailing space on its daemon message; whitespace checks report that evidence-only line. Source, SQL, JSON, Markdown and non-log artifacts pass checks. Background Kafka/shutdown warnings in regression output are retained; they do not change the test XML outcomes or exit code.
