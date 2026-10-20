# TV4 — Order → Inventory pair-gate implementation note

**Status:** isolated implementation proposal; default OFF; not enabled for production Checkout/Order traffic.
**Design reference:** TV1-S01-OD09-20261018-v1, PR #48.
**Owner:** TV4. **Scope:** Inventory callee gate only, not full caller authorization.

## Implemented in this branch

- `POST /api/inventory/operations` accepts the new `X-Internal-Token` header only through a separately configured `inventory.order-pair-token` verifier.
- The independent `inventory.order-pair-enabled` feature flag defaults to `false`; setting a token alone does not open the pair path. Disabled returns 503.
- When explicitly enabled, the pair credential is limited to exact `POST /api/inventory/operations` and operation types `RESERVE`, `CONSUME`, `RELEASE`.
- The pair credential is denied on initialize/items, GET balance, wrong methods/paths, malformed/untyped operation bodies, `INCREASE`, and `DECREASE`.
- Order operation JSON is read with a streaming hard cap of 64 KiB, independent of Content-Length; the filter reads at most cap+1 bytes and returns 413 if oversized, including unknown-length/chunked requests.
- Missing/wrong configured pair credential returns 401; absent pair verifier configuration or default-off gate returns 503; authenticated pair requests outside the allowlist return 403.
- Comparison uses `MessageDigest.isEqual`; the request body is replayed to the controller after the filter reads the operation type.
- Forged `X-User-Id` / `X-Store-Id` headers do not change the pair-gate decision; this filter does not treat these headers as identity.
- The existing `X-Inventory-Internal-Token` path remains for existing internal Inventory workflows. It is intentionally separate and broad; **never give it to Checkout/Order**.

## Configuration

- `INVENTORY_ORDER_PAIR_TOKEN` maps to `inventory.order-pair-token`.
- `INVENTORY_ORDER_PAIR_ENABLED` maps to `inventory.order-pair-enabled` and defaults to `false`.
- Do not commit secrets or log token values. Keep the feature flag false until owner approval, caller/callee authorization, actor/system provenance, secret rotation and integration evidence are complete.

## Required follow-up before positive integration

1. TV1 confirms concrete pair credential provisioning/rotation and exact runtime configuration.
2. TV3 confirms the caller sends only the approved header and allowed operation types, with persisted immutable operationId/type/payload and durable intent before HTTP.
3. TV1/TV3/TV4 agree system provenance for asynchronous CONSUME/RELEASE; customer Bearer or caller-supplied actor headers are not a substitute. Pair authentication alone is not delegated actor authorization.
4. Add HTTP-level tests proving deployed filter ordering, exact path normalization behavior, no Gateway exposure, and current/previous secret rotation/retirement.
5. Run Inventory unit and PostgreSQL integration suites on the final source SHA, then record CI evidence. No positive pair integration or live Checkout E2E tests are claimed.

## Current boundary / known limitations

- This is a scoped shared-secret gate, not full caller identity, delegated customer authorization, current Store/Seller authority, durable reservation journal, or Order state-machine validation.
- It does not authorize production CONSUME/RELEASE business semantics by itself. TV3's durable operation intent and approved dispatch/cancellation provenance remain mandatory.
- The legacy token remains a broad internal credential for compatibility. Migration/removal requires separately coordinated owner changes; this branch does not weaken or replace its existing paths.
- The new pair path is default OFF. Do not set `INVENTORY_ORDER_PAIR_ENABLED=true` until all owner gates and the independent authorization implementation are reviewed.
- Current pair-gate verification is filter-unit only; deployed HTTP, PostgreSQL pair auth, positive service-pair integration, rotation, and E2E remain NOT_RUN.
