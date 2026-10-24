# TV4 reservation ledger candidate

Status: **provider implementation candidate; not an approved shared wire or deployable contract**. Refs #4 and #11, and TV3's owner-principle response on [Issue #4](https://github.com/duyxl56785/Project_HK5/issues/4#issuecomment-6330217083).

## Candidate behavior implemented

- One opaque `reservationId` binds to one Store, Seller, future SellerOrder, immutable initial SKU/quantity set, and fingerprint. A database uniqueness constraint permits only one reservation for a `(storeId, sellerOrderId)` pair.
- `RESERVE`, `CONSUME`, and `RELEASE` have distinct operation IDs. An operation ID binds to one reservation, operation type, owner tuple, and canonical line fingerprint. Reusing it with a changed owner/type/SKU/quantity conflicts.
- Reservation lines track initial, consumed, released, and held quantities; the database requires `initial = consumed + released + held` for every SKU.
- Partial CONSUME/RELEASE is limited to the reservation's current held quantity. A multi-SKU update, its operation claim, and stock balance changes commit or roll back together.
- Operation status lookup is scoped by `(operationId, reservationId)`. A persisted row reports `COMMITTED` with type, fingerprint, commit time, and canonical lines. An absent row reports `UNKNOWN`; it never implies permission to issue a new operation ID or RELEASE.
- Candidate V3 resides at `db/candidate-migration` and is excluded from default production Flyway discovery. PostgreSQL candidate tests opt in explicitly; do not add it to production migration locations until the shared contract and rollout are approved.
- SKU identity is preserved exactly. Leading or trailing Unicode whitespace is rejected instead of being trimmed into another stock key.
- Same-SKU reservations for two Stores remain separately accounted. The aggregate Inventory balance still reflects the total reserved quantity.

## Candidate Java API shape (no HTTP route)

The current Java-only request is `ReservationOperationCandidateRequest`:

```text
operationId, reservationId, storeId, sellerId, sellerOrderId, type, items[{sku, quantity}]
```

Supported types are `RESERVE`, `CONSUME`, and `RELEASE`. The candidate status lookup is `lookup(operationId, reservationId)` and returns:

```text
operationId, reservationId, status, operationType, requestFingerprint, committedAt, items[{sku, quantity}]
```

`status` is `COMMITTED` for a persisted operation and `UNKNOWN` when no scoped record is visible. Proposed HTTP path, exact status vocabulary, error envelope, and authorization are deliberately not implemented pending TV1/TV3 approval. Do not infer `DEFINITIVE_NO_COMMIT` from absence.

## Retry and lifecycle boundaries

- Persist the exact owner tuple, operation ID, type, and canonical lines before any eventual outbound call.
- On timeout, connection loss, or 5xx, retain the same immutable tuple and reconcile by the approved authoritative lookup. If still unknown, keep the operation pending for bounded retry/manual reconciliation; never blind-release or mint a replacement key.
- TV3's suggested lifecycle is RESERVE once, CONSUME after the durable dispatch boundary, and RELEASE only for a proven eligible pre-dispatch cancellation/rejection with no conflicting UNKNOWN/CONSUME. The exact state/outbox boundary is pending TV3, and cancellation/partial/COD policy is pending TV5/business approval.
- Store/Seller values passed to this Java candidate are compared with the persisted owner tuple. This is not caller authentication. A caller with guessed identifiers is not authorized by this service; no route is exposed and the pair gate remains OFF until TV1 signs and verifies the caller/actor/system policy.
- Product `productId → SKU`, current Store/Seller authority and global SKU guarantees remain TV2-owned. This candidate consumes SKU; it does not invent Product semantics.

## PostgreSQL verification required

`InventoryReservationLedgerCandidatePostgresIT` uses the existing disposable PostgreSQL 16 Testcontainers fixture, Flyway, and Hibernate validation. It covers two Stores reserving the same SKU, owner mismatch, operation-key owner/type/payload conflict, partial held budgets, multi-SKU rollback, failed reserve rollback, lost-response lookup/replay, UNKNOWN lookup, and concurrent releases against one reservation. These tests must execute against PostgreSQL; an initialization failure is NOT_RUN/BLOCKED, not a pass.

Even a green candidate suite proves only provider-side PostgreSQL behavior. It does not prove HTTP transport, JWT/Gateway/service-pair authorization, Order T1 durability, Product mapping, SellerOrder transitions/outbox, COD settlement, Notification, or two-Store E2E.

## Decisions required before merging/exposing V3

1. TV1: exact path/method authorization, caller/pair role per operation, operation-query permission, original actor proof and asynchronous recovery-system provenance.
2. TV2: authoritative Store/Seller and Product→SKU source, and whether SKU uniqueness is enforceable globally.
3. TV3: durable T1 reservation-to-SellerOrder mapping before RESERVE, exact operation/status/error contract, and transaction/outbox timing for dispatch, cancellation, and rejection.
4. TV5/business: eligible partial consume/release, cancellation/refusal rules, COD authority and settlement/event boundary.

Until these decisions are signed, treat V3 and these Java interfaces as a review candidate. Do not turn on the pair gate, expose the lookup route, or call this Checkout integration complete.
