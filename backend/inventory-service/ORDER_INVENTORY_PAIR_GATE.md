# TV4 — Order → Inventory pair-gate implementation note

**Status:** isolated implementation proposal; not enabled for production Checkout/Order traffic.
**Design reference:** TV1-S01-OD09-20261018-v1, PR #48.
**Owner:** TV4. **Scope:** Inventory callee authorization only.

## Implemented in this branch

- `POST /api/inventory/operations` accepts the new `X-Internal-Token` header only through a separately configured `inventory.order-pair-token` verifier.
- The Order pair credential is limited to exact `POST /api/inventory/operations` and operation types `RESERVE`, `CONSUME`, `RELEASE`.
- The pair credential is denied on initialize/items, GET balance, wrong methods/paths, malformed/untyped operation bodies, `INCREASE`, and `DECREASE`.
- Missing/wrong configured pair credential returns 401; absent pair verifier configuration returns 503; authenticated pair requests outside the allowlist return 403.
- Comparison uses `MessageDigest.isEqual`; the request body is replayed to the controller after the filter reads the operation type.
- Forged `X-User-Id` / `X-Store-Id` headers do not change the pair-gate decision; this filter does not treat these headers as identity.
- The existing `X-Inventory-Internal-Token` path remains for existing internal Inventory workflows. It is intentionally separate and broad; **never give it to Checkout/Order**.

## Configuration

Set `INVENTORY_ORDER_PAIR_TOKEN` through the deployment's secret mechanism (maps to `inventory.order-pair-token`). Do not commit secrets or log token values. Keep the variable absent until the caller/callee contract, credential provisioning, rotation, network boundary and both sides' tests are reviewed. An unconfigured pair verifier denies requests with 503.

## Required follow-up before positive integration

1. TV1 confirms concrete pair credential provisioning/rotation and exact runtime configuration.
2. TV3 confirms the caller sends only the approved header and allowed operation types, with persisted immutable operationId/type/payload and durable intent before HTTP.
3. TV1/TV3/TV4 agree system provenance for asynchronous CONSUME/RELEASE; customer Bearer or caller-supplied actor headers are not a substitute.
4. Add HTTP-level tests proving deployed filter ordering, exact path normalization behavior, no Gateway exposure, and current/previous secret rotation/retirement.
5. Run Inventory unit and PostgreSQL integration suites on this exact source SHA, then record CI evidence. No runtime, PostgreSQL, positive pair integration or live Checkout E2E tests are claimed by this document.

## Current boundary / known limitations

- This is a scoped shared-secret gate, not full caller identity, delegated customer authorization, current Store/Seller authority, durable reservation journal, or Order state-machine validation.
- It does not authorize production CONSUME/RELEASE business semantics by itself. TV3's durable operation intent and approved dispatch/cancellation provenance remain mandatory.
- The legacy token remains a broad internal credential for compatibility. Migration/removal requires separately coordinated owner changes; this branch does not weaken or replace its existing paths.
- `INVENTORY_ORDER_PAIR_TOKEN` has no value in source control. Do not activate it until secret distribution is reviewed.
- Unit tests in this branch are source additions only until a CI run reports results. All integration/E2E status remains NOT_RUN.
