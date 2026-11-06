# TV4 Return → Inventory restock handoff (#156)

**Source:** `main@9ceddbadf1f93cac380078837ccacbb038dc08ad` plus the TV4 Seller Return workflow branch.  This note is a request for review, not an approval to enable an integration flag or Inventory migration.

## Implemented and fail-closed

- A Store-scoped Seller can transition a Return `REQUESTED → APPROVED|REJECTED` and `APPROVED → RECEIVED` while holding the same `seller_orders` row lock used by Return creation and Finance settlement.
- `RECEIVED` requires a persisted, nonblank `receipt_reference`; it is the Seller's auditable confirmation that goods were physically received.  Replaying or racing the transition has one winner.  No new customer notification is emitted because only `RETURN_REQUESTED` has an approved event contract.
- Neither `APPROVED` nor `RECEIVED` emits Inventory `RELEASE` or `INCREASE`.  This deliberately prevents a duplicate or unauthorised stock mutation when an HTTP/outbox outcome is unknown.

## Why Inventory restock is not connected

The current Order-pair credential allowlist explicitly denies `INCREASE` and `DECREASE`; see `backend/inventory-service/ORDER_INVENTORY_PAIR_GATE.md`.  The broad legacy Inventory token must not be given to Order/Checkout.  Consequently, creating an operation ID or publishing an `INCREASE` now would be an unauthorised production contract change, even though generic Inventory operations themselves have idempotency support.

## Contract required before a restock implementation

TV2, TV3, TV4 and TV5 need to approve all of the following before a new integration PR:

1. A caller-specific, least-privilege Return-restock credential and exact endpoint/operation allowlist; it must not reuse the Checkout reservation pair credential.
2. A durable Order-side restock intent containing immutable `returnRequestId`, Store/SellerOrder ownership, canonical SKU lines, receipt reference, stable `operationId`, payload fingerprint and delivery/reconciliation state before any HTTP call.
3. A provider status/receipt lookup authorized for that caller, so timeout/lost-response is recorded as `UNKNOWN` and retried only with the same operation tuple; no new operation ID and no blind compensation.
4. Exactly-once ownership checks tying `RECEIVED` Return to the original consumed SellerOrder lines, plus PostgreSQL concurrency and replay evidence.
5. TV5 Finance sign-off that restock does not imply refund, COD reversal, payout reversal or settlement.

Until those approvals and tests exist, `RECEIVED` remains settlement-blocking, `REJECTED` is the only state that reopens settlement, and the two flags remain OFF.
