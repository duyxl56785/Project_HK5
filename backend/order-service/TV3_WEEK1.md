# TV3 Week 1 — Checkout & Idempotency (Issue #3)

**Owner:** Trần Văn Chính (`@tranvanchinhbg2006-alt`). **Status: PARTIAL / NEEDS CROSS-TEAM REVIEW.** Prototype baseline prepared from 16/10/2026 ZIP; not merged or tested end-to-end.

## Implemented baseline (only TV3 checkout/order creation area)

- `POST /api/orders` with mandatory `Idempotency-Key`, JSON `{ "expectedItems": [{"productId":"...","quantity":1}], "expectedRevision":null }`.
- Authenticate bearer JWT (signature, issuer, audience) and require CUSTOMER role; derive customerId from verified `sub`.
- `RequestFingerprint` SHA-256 of canonical request, `(customerId,key)` unique in DB, same-key + different payload = HTTP 409, same-key success returns existing order, concurrent duplicate processing = HTTP 409.
- Cart claim → exact expectedItems validation → authoritative Product/Seller/Store/Price snapshot; giá đổi so với snapshot Cart bị từ chối → Inventory reservation → persist 1 `orders`, 1 `seller_orders` **per Store**, order line snapshots, idempotency state, outbox row atomically → finalize Cart.
- `MoneyRules` uses BigDecimal and HALF_UP; assumed VND whole-dong rounding, platform fee 5%, house store fee 0. **TV5 MUST APPROVE rounding/currency before merge.**
- Compensating release cart/inventory when an intermediate step fails; failed release marks attempt `NEEDS_RECONCILIATION` to block unsafe retries. Successful order but failed cart finalization can be retried with the same key. **Not a fully automated recovery worker**.
- Flyway migration `V1__checkout_baseline.sql`: `orders`, `seller_orders`, `order_items`, `checkout_attempts`, `outbox_events`.
- Persist `order.placed.v1` outbox record atomically; **no Kafka publisher/consumer yet**. No fabricated delivery or COD implementation.

## Strict integration blockers

- **TV1:** approve JWT verifier (issuer/JWK/audience), gateway route `POST /api/orders`, internal pair credential; no public `/internal/**`.
- **TV2:** `CatalogPort.verify(productId)` adapter not connected. MUST verify authoritative sellerId/storeId, seller/store status, current price, house store policy. Default `UnconfiguredCatalogAdapter` returns 503.
- **TV4:** `InventoryPort.reserve/release(orderId,items)` contract and implementation not connected. Must ensure idempotent reserve/release per operationId, prevent negative stock, guarantee reconcile for ambiguous timeout. Default adapter returns 503. Shared order state/fulfillment entity and migration require TV4 review.
- **TV5:** approve money rounding, snapshot semantics, status enum/order schema, ownership of finance and outbox. Shared migration requires TV4+TV5 review.
- **V2 original:** contract is NOT included in repo. Existing tracked code has only scaffold; do not claim backward compatibility with original V2.

## Local requirements and tests

```sql
CREATE DATABASE hk5_order OWNER hk5;
```

Dev env: `HK5_ORDER_JDBC_URL`, `HK5_ORDER_DB_USER`, `HK5_ORDER_DB_PASSWORD`, `JWT_ISSUER_URI`, `JWT_JWK_SET_URI`, `JWT_AUDIENCE`; `APP_CART_INTERNAL_URL`, `APP_CART_INTERNAL_TOKEN`, Cart service `APP_INTERNAL_CHECKOUT_TOKEN` must match, with secrets provided at runtime only.

```bash
cd backend
gradle :order-service:test
gradle :order-service:bootRun
```

| Test | Intended result | Status (offline) |
|---|---|---|
| 2 sellers / 2 stores → 1 order / 2 seller orders | Test with mocked ports | JUnit **NOT_RUN** |
| Same key, same payload → same orderId | Test with mocked ports | JUnit **NOT_RUN** |
| Same key, different payload → 409 | Requires real coordinator test | **NOT_RUN** |
| Inventory exception → cart release / attempt fail | Test with mocked ports | JUnit **NOT_RUN** |
| Failed inventory release during timeout | NEEDS_RECONCILIATION, manual reconciliation | **NOT_RUN** |
| Real Postgres transactions, HTTP, JWT, TV2/TV4 services, Kafka | End-to-end | **BLOCKED** |

**Release/Retry caution:** reserve timeout may have succeeded remotely; compensation must be idempotent. Automated background reconciliation and outbox relay are outstanding; do not label checkout production-ready.

## PR review requirements

1. Request review TV3 (owner), TV4 (inventory/status/migration), TV5 (finance/fee/schema), TV1 (security/gateway), TV2 (Product/Store contract).
2. Sync origin/main and inspect diff/conflicts **before** merge (not possible from this offline copy).
3. Execute real Gradle tests, Postgres migration, mocked + live contract tests, security regression and verify outbox relay before closing Issue #3.
4. Do not push directly to main or force-push. Branch `feat/tv3-cart-checkout-week1`.
