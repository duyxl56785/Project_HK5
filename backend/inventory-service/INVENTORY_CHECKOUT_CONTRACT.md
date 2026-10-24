# Inventory ↔ Checkout handoff (TV4 draft)

Refs #4, #11, PR #28, PR #31.

This document separates behavior present in Inventory from cross-service decisions that still need owner approval. It is not authorization to enable the Checkout adapter.

## Current Inventory provider API

### Operation request

`POST /api/inventory/operations`

```json
{
  "operationId": "stable-operation-step-id",
  "type": "RESERVE",
  "items": [
    { "sku": "authoritative-inventory-sku", "quantity": 2 }
  ]
}
```

- `operationId`: nonblank string, at most 128 characters.
- `type`: one of `INCREASE`, `DECREASE`, `RESERVE`, `CONSUME`, `RELEASE`.
- `items`: nonempty; each SKU is nonblank and at most 128 characters; quantity is a positive signed 64-bit integer; one item per SKU per operation.
- The Inventory key is currently SKU only. It does not contain `productId`, Store, SellerOrder, Order, buyer, or caller ownership.

### Success response

The service returns `operationId`, `type`, `replayed`, and `items[]`. Each item reports `sku`, `availableQuantity`, `reservedQuantity`, and `updatedAt`. A replay with the exact same request has `replayed: true` and does not apply stock a second time.

### Current error mapping

| HTTP | `code` | Meaning |
|---|---|---|
| 400 | `INVALID_REQUEST` | Bean/request validation failed or an `IllegalArgumentException` reached the handler. |
| 400 | `QUANTITY_OVERFLOW` | Quantity arithmetic overflow. |
| 409 | `INSUFFICIENT_STOCK` | RESERVE/DECREASE cannot be applied; includes `sku`, `available`, `requested`. |
| 409 | `INSUFFICIENT_RESERVATION` | CONSUME/RELEASE cannot be applied; includes `sku`, `reserved`, `requested`. |
| 409 | `INVENTORY_ITEM_NOT_FOUND` | An item in an operation is absent. The operation transaction rolls back all item changes. |
| 409 | `IDEMPOTENCY_KEY_REUSED` | The same `operationId` was committed with a different operation type or canonical SKU/quantity payload; do not retry with a new ID to hide this conflict. |
| 409 | `IDEMPOTENCY_CLAIM_CONFLICT` | The ID claim did not find a ledger row after a concurrent/uncertain claim; source message says to retry the operation. Retry only the exact persisted operation ID, type, and canonical payload. |
| 409 | `INVALID_OPERATION_ID`, `INVALID_SKU`, `INVALID_QUANTITY`, `DUPLICATE_SKU` | Service-level operation validation currently raises an operation conflict (HTTP 409), despite these being input validation errors. |
| 409 | `INVENTORY_CONFLICT` | Database uniqueness/integrity conflict not mapped to a more specific operation error. |
| 409 | `INVENTORY_ITEM_EXISTS` | `POST /api/inventory/items` attempted to create an existing SKU. |
| 401 | `UNAUTHORIZED` | Missing/incorrect temporary internal token. |
| 503 | `INVENTORY_AUTH_NOT_CONFIGURED` | Temporary internal-token configuration is absent; the provider fails closed. |

`GET /api/inventory/{sku}` returns 404 `INVENTORY_ITEM_NOT_FOUND` for a missing balance. This read does **not** report the outcome of an operation.

## Idempotency, atomicity, and retries

- Inventory trims leading/trailing whitespace from SKU before canonicalizing, locking, and hashing; SKU comparison remains case-sensitive (`sku-a` and `SKU-A` are distinct). Product's target contract rejects whitespace-padded SKU and preserves exact case, so consumers should persist and send an already canonical SKU rather than rely on Inventory's trim behavior.
- Inventory canonicalizes the operation type and SKU/quantity lines, sorts lines by SKU, and stores a request hash with the operation ID in the same transaction as the stock change.
- Same operation ID + same canonical payload is an exact replay. Same ID + changed type or payload conflicts. RESERVE, CONSUME, and RELEASE must each use their own operation ID; an Order/attempt ID is a correlation ID, not the Inventory operation ID.
- A successful replay reports `replayed: true`, but `availableQuantity`/`reservedQuantity` are the **current balances at replay time**, not a historical snapshot from the original commit. Do not treat replay response balances as the original operation's resulting balance.
- The provider checks that each SKU exists before deciding whether the operation ID's payload conflicts. Therefore an unknown SKU may return `INVENTORY_ITEM_NOT_FOUND` before an `IDEMPOTENCY_KEY_REUSED` comparison; consumers must not assume payload-mismatch errors always take precedence.
- All SKU rows in one operation are locked in sorted order and updated in a single Inventory database transaction. A failed line rolls back earlier lines in that operation. This does not create a distributed transaction across Cart, Order, Product, and Inventory.
- On timeout/unknown response, a consumer may retry the exact same operation ID, type, and persisted payload. `IDEMPOTENCY_CLAIM_CONFLICT` is retryable only with that same saved tuple; `IDEMPOTENCY_KEY_REUSED` is a payload conflict and must not be retried with a changed payload. It must not mint a new ID or compensate based only on a timeout. Retry count, backoff, SLA, and manual escalation are still pending owner/consumer agreement. TV4 proposes (pending TV3 approval) at most 3 retries after the initial attempt with 250 ms, 1 s, and 4 s jittered delays (target overall budget ≤10 s) for timeout/connection uncertainty, 5xx, or `IDEMPOTENCY_CLAIM_CONFLICT` only; every retry must use the exact persisted operationId/type/canonical payload. If the budget is exhausted or commit outcome remains unknown, mark UNKNOWN, alert and hold for reconciliation/manual ops. Never blind RELEASE. This is a proposal, not an approved runtime policy.
- On `main`, Inventory has no operation-status lookup, reservation ID, order-to-line ownership record, or release-by-Order API. The TV4 feature branch now contains an isolated V3 provider candidate and Java-only lookup, but no HTTP route. The consumer must retain the exact immutable line set and IDs durably. A status/recovery endpoint needs an agreed authorization scope and response contract before exposure.

## Proposed Checkout lifecycle — pending shared approval

The following is TV4's proposal, not an approved shared SellerOrder API/state machine:

1. **RESERVE** after authoritative Catalog and approved Finance checks, before Order confirmation; the consumer commits its intent, exact immutable SKU/quantity lines, type, and operation ID before the HTTP call.
2. **CONSUME** once, after a server-authorized and durably committed SellerOrder transition records dispatch/handoff. Do not wait for delivery or COD settlement.
3. **RELEASE** once only after a confirmed cancellation/rejection before dispatch while the reservation is still held.
4. After dispatch, a returned item is a separate workflow. **INCREASE** only after the goods are received and inspected; do not RELEASE already-consumed stock.

TV3 must confirm the authoritative SellerOrder status/outbox source and payload. TV5 must confirm COD/DELIVERED/settlement separation. TV2 must provide the authoritative Product→SKU mapping and current Store/Seller facts. TV1 must approve the user/service authorization boundaries.

TV2 has confirmed SKU-global-uniqueness as the target Product invariant, but has not claimed that Product persistence/API enforces it yet. Inventory will keep its existing SKU-only key; do not seed or enable a live adapter until TV2's authoritative mapping/ownership source is enforceable and available.

## Security gate

The current `X-Inventory-Internal-Token` gate is temporary and applies the same credential to all `/api/inventory/**` operations. Missing configuration fails closed with 503; a missing/wrong token is rejected with 401. The credential does not identify a caller-specific pair or restrict Checkout to reservation operations. **Do not provide this broad token to Checkout or expose initialize/INCREASE/DECREASE to a reservation-only caller.**

TV1 must confirm the per-caller service-pair authentication, delegated actor requirements, and endpoint/operation allowlist under Auth Contract OD-09 before an adapter or status query is exposed. Until then, Checkout integration remains fail-closed.

## Verification boundary

At Inventory source SHA `9b8ae3557d1b1b50ee27d1579c7cd740576e0648`, TV2 and TV5 reported a fresh successful run: **17 unit tests + 15 PostgreSQL integration tests PASS, 0 fail/error/skip**. The PostgreSQL test bodies did execute in Docker/Testcontainers; the earlier environment-only initialization failure was followed by a successful rerun with the documented Docker API compatibility/timezone options. This evidence includes the `RESERVE 4 → replay`, `CONSUME 3 → replay`, `RELEASE 1 → replay`, and changed-payload conflict regression. See [TV2 review](https://github.com/duyxl56785/Project_HK5/pull/38#pullrequestreview-5561434098), [TV5 review](https://github.com/duyxl56785/Project_HK5/pull/38#pullrequestreview-5561477307), and [QA audit PR #40](https://github.com/duyxl56785/Project_HK5/pull/40).

This is **Inventory module regression evidence only**. It does not prove a live Checkout→Inventory HTTP call, Product mapping, caller-specific service-pair authorization, durable Order journal, unknown-outcome recovery, Fulfillment transition, COD, Notification, or two-Store E2E. Checkout integration remains fail-closed and shared contract approvals remain pending.
