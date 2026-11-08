# Isolated UNKNOWN preparation — TV2

Refs #18, #152, #154, #170. Stacked on the TV4 helper; this does not modify that helper or the existing UAT Start/Stop scripts.

This command creates configuration only. It never starts Docker, calls a provider, seeds a database, changes shared credentials or runs cleanup. **It is not a live UNKNOWN runner or an execution approval.** Claim-conflict remains BLOCKED_DESIGN.

```powershell
node scripts/qa/unknown/prepare.mjs --case lost-response `
  --project hk5-unknown-YYYYMMDDHHmmss --operationId EXACT_OPERATION_ID `
  --bodySha256 EXACT_REQUEST_BYTES_SHA256 --output C:/private/hk5-unknown-YYYYMMDDHHmmss
```

Use an unused directory outside the checkout, named exactly after the project. Do not commit generated files: `.env` and `secrets/` contain synthetic per-project credentials. The original generator is reused with both Inventory/Finance gates OFF. `plan.json` and `metadata.json` record actual checkout SHA/ref/dirty state, helper/proxy byte hashes, request binding, ports 18082/5175 and isolated volume names. Request byte SHA is transport evidence, not the Inventory canonical fingerprint or proof of COMMIT.

Render with `docker/compose.demo.yml`, `scripts/qa/uat/compose.isolated.yml` and the generated `compose.unknown.yml`, using only the generated env/secrets and `HK5_UAT_PROJECT`, `HK5_UAT_GATEWAY_PORT=18082`, `HK5_UAT_FRONTEND_PORT=5175`, `HK5_UAT_SECRETS_DIR`. Validate with **`docker compose ... config` only** until execution is separately authorized. No `up` is performed by this tool.

The overlay keeps Inventory pair OFF and the original Order transport by default. It requires separate explicit overrides for pair/Order URL and the `unknown-fault` profile. The proxy shares Inventory's network namespace; it forwards only to loopback Inventory. No proxy host port is published. Its listener must be reachable on the private container network, so it binds inside that namespace only. Transport receipts contain no credentials or request bodies and explicitly state `providerCommitVerified=false`.

Prepared plans have `executionApproved=false`; the proxy refuses them before importing the helper or creating a listener. Clean `main`, pinned source hashes and an exact execution-artifact digest are also required. **The digest/environment switch is an integrity interlock, not authenticated proof of reviewer approval. Do not edit a prepared plan to grant permission.** A reviewed executor must first verify the relevant human approvals and current main, then issue the execution artifact. That executor is not implemented here.

## Still required before live execution

- Merge/review helper #170 and this preparation slice, then a case-specific executor/teardown. Existing `Stop-IsolatedUat.ps1` intentionally refuses `hk5-unknown-*`; do not loosen it or use global cleanup.
- TV3: immutable attempt/key/body/operation/Store/SellerOrder/SKU tuple or explicit scoped T1 waiver; exact replay/quarantine and two-retry oracle.
- TV4: read-only canonical provider fingerprint/stock/COMMIT receipt and network mechanics. An upstream HTTP response alone is insufficient.
- TV5: QA/PII approval of the final source, project, fixture, receipt collection and execution case. No synthetic 409, DB deletion, new operation ID or blind compensation.

## Verification

`node --test scripts/qa/unknown/prepare.test.mjs scripts/qa/tv4-unknown/lost-response.test.mjs`: 11 PASS / 0 FAIL / 0 SKIPPED with local loopback permission. This includes rejecting double-dot-prefixed checkout children and the primary checkout's secret tree from a linked worktree. The four TV4 tests use a deliberately fake upstream; no real Inventory or demo endpoint was called. Initial restricted loopback failures were EACCES and passed unchanged with local network permission.

Actual Docker Compose **config-only** rendering passed for isolated volumes, all secret bindings, 18082/5175 loopback-only host ports, pair OFF, unchanged default Order URL and Inventory network namespace. Runtime source was the working tree on #170 with uncommitted preparation files (`sourceDirty=true`), therefore not execution eligible. Live UNKNOWN, provider COMMIT evidence, stack startup and final UAT remain NOT_RUN.
