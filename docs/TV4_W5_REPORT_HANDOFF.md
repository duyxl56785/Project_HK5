# TV4 W5 handoff — Inventory, Fulfillment, COD, Notification

**Owner:** Phạm Thế Cường (TV4)
**Prepared:** 10/11/2026
**Purpose:** Source material for the group report. This document does not replace
TV5 QA/Finance sign-off or final UAT.

## Delivered work

| Area | Delivered scope | Evidence |
| --- | --- | --- |
| Inventory | Reservation ownership ledger candidate, idempotency by operation ID and request fingerprint, multi-SKU/concurrency regressions, and default-OFF pair gate. V3 remains candidate-only. | [PR #69](https://github.com/duyxl56785/Project_HK5/pull/69), source `f2d56d7`, merge `3b4cf7d` |
| Fulfillment | Store-scoped Seller actions, legal state guards, one transaction boundary with the SellerOrder row, RELEASE on cancel/reject and one CONSUME at DISPATCHED. | merged #135/#136 evidence; selected tests at `f2d56d7`: 14 unit + 14 PostgreSQL PASS |
| COD | Collection is guarded by DELIVERED and pending COD state; duplicate collection is rejected. Inventory CONSUME is not COD PAID. | Fulfillment/COD module evidence recorded in Issue #11; Finance settlement remains TV5-owned |
| Notification | Unproven persistence failures are sent to retry/DLT rather than acknowledged as duplicates. | [PR #168](https://github.com/duyxl56785/Project_HK5/pull/168), `d290559` |
| Seller Return | Seller Store scope and `REQUESTED → APPROVED|REJECTED`, `APPROVED → RECEIVED`; receipt evidence is enforced in PostgreSQL. Return/settlement share the SellerOrder lock. | [PR #182](https://github.com/duyxl56785/Project_HK5/pull/182), merge `9de2697` |
| Return / Finance boundary | A received return leaves `gross`, `platform_fee`, COD state and settlement unchanged. An open return blocks settlement under the same SellerOrder lock; this does not approve refund, payout reversal or restock. | [PR #191](https://github.com/duyxl56785/Project_HK5/pull/191), merge `f0b5090` |
| Voucher C2 review boundary | Reviewed the TV4 scope of voucher cancellation/rejection, Return and Inventory RESERVE receipt. Cancellation/rejection after a legal pre-dispatch transition releases voucher usage through the durable recovery journal; Return preserves the voucher and uses recorded post-discount `gross`. | [PR #211](https://github.com/duyxl56785/Project_HK5/pull/211), TV4 approval at `4f3152b` |

## Tests with bounded claims

| Exact source | Command or workflow | Result | Boundary |
| --- | --- | --- | --- |
| `f2d56d7` | `:inventory-service:test :inventory-service:postgresIntegrationTest` | 27 unit + 38 PostgreSQL PASS | Inventory module/provider candidate, not Checkout E2E |
| `f2d56d7` | selected Fulfillment lifecycle and PostgreSQL suites | 14 unit + 14 PostgreSQL PASS | Unit/MockMvc and database scope, not live Gateway actor acceptance |
| `d290559` | `:notification-service:test` | 4 unit + 3 Kafka/PostgreSQL PASS | Notification consumer persistence/retry scope |
| `539cf3b` | Return unit, PostgreSQL, Gateway and frontend checks | 12 unit + 24 PostgreSQL; Gateway 5/5; frontend 165/165 PASS | [Seller Return live E2E](https://github.com/duyxl56785/Project_HK5/issues/153#issuecomment-6719477452) PASS on clean `main@78de5c0` (seller-return, return-access); restock remains BLOCKED |
| `6d45172` | `FulfillmentPostgres16IT`, `ReturnConstraintPostgresIT`, Return rules | 24 + 5 PostgreSQL and 5 unit PASS | Return receipt preserves money/COD snapshots; explicit COD collection still cannot settle an open return. Published test evidence, not a new runtime or Finance policy approval. |
| `27c65f3` | UNKNOWN fake-loopback and config-only tests | 12/12 + 7/7 PASS | Fixture safeguards only; no real Inventory provider, Docker fault or database |
| `bbf1bf9` | `npm test`, `npm run typecheck`, `npm run build` | 162 PASS; typecheck/build PASS | Draft Inventory UI candidate only |

## Acceptance limits

- **UNKNOWN lost-response:** `BLOCKED_DESIGN` for W5/UAT and `NOT_RUN` as a
  live execution fact. The isolated stack is preparation only; no
  executor/teardown, provider COMMIT/status receipt, durable Checkout replay
  oracle or case-specific execution approval exists.
- **UNKNOWN claim-conflict:** `BLOCKED_DESIGN` and `NOT_RUN`; no approved real
  conflict generator exists. No synthetic HTTP 409 or direct database
  modification is used to manufacture a PASS.
- **Return restock:** `BLOCKED`. `INCREASE` is not available through the
  default-off Order pair gate. A caller-specific credential, durable restock
  intent, owner-bound provider lookup/receipt, exact replay recovery and TV5
  Finance sign-off are required before runtime wiring.
- **Return / Finance:** `RECEIVED` is deliberately an auditable physical
  receipt, not a refund, COD reversal, payout reversal or Inventory movement.
  An active Return blocks settlement; whether `payableNet` means merely
  unsettled or eligible-to-pay remains a TV2/TV5 business decision.
- **Inventory UI:** [PR #190](https://github.com/duyxl56785/Project_HK5/pull/190)
  merged only into the Inventory-UI candidate; [PR #169](https://github.com/duyxl56785/Project_HK5/pull/169)
  was subsequently closed without merge. There is no approved Store-scoped
  inventory read/list contract or SellerProductsPage integration point; this
  remains post-submission backlog.

## Safety boundaries retained

- `INVENTORY_ORDER_PAIR_ENABLED` remains OFF by default.
- `HK5_FINANCE_POLICY_APPROVED` remains OFF by default.
- Inventory candidate V3 is not promoted into default Flyway migrations.
- No automatic RELEASE occurs after an UNKNOWN outcome; retry uses the same
  operation identity only when the approved recovery contract exists.
- No automatic Return refund or Finance settlement policy is introduced by TV4.

## Required owner follow-up

| Owner | Needed decision/evidence |
| --- | --- |
| TV2 | Authoritative Store/Product→SKU facts, Store-scoped Inventory read contract, and future UI integration placement (post-submission backlog). |
| TV3 | Immutable checkout tuple, send generation and replay/quarantine oracle for Inventory UNKNOWN/recovery. |
| TV5 | Finance boundary for Return restock and QA approval for any future isolated live UNKNOWN case. |

## Report wording recommendation

Describe module-level and PostgreSQL/Kafka evidence as **PASS within the stated
scope**. Keep two-Store recovery, live UNKNOWN and exactly-once restock as
**NOT_RUN/BLOCKED**, never as production acceptance.

## Pre-final checkpoint — 19/11/2026

- **Current observed main:** `4b5bcb4ceb5a6a636e4c943db2d41c8a4c1bb14f`.
  It merges [PR #212](https://github.com/duyxl56785/Project_HK5/pull/212) after
  [PR #211](https://github.com/duyxl56785/Project_HK5/pull/211). The backend
  tree at this main SHA matches the reviewed #211 head `4f3152b`.
- **C2-related CI evidence:** #211 Backend readiness reports Order PostgreSQL
  `156/156` PASS. #212 reports C2-on Playwright `25/25` PASS, C2-off `24/24`
  PASS, frontend `186/186` PASS, and Gateway/V7 smoke `FAILS=0` at #212 head
  `3483486`. These are CI receipts on their stated heads, not Final UAT on
  frozen main.
- **Existing TV4 raw evidence:** all `66/66` files listed in
  `reports/evidence/tv4-final-verification-20261116/SHA256SUMS.txt` verify.
  Its focused JUnit scopes total 313 test units with zero failures/errors;
  the bounded combined PostgreSQL run remains exit `124` and is not PASS.
- **Final UAT status:** `WAITING_FROZEN_SHA`. TV2 had not published a frozen
  SHA/receipt at this checkpoint. Run the two-Seller, two-Store Final UAT only
  on that SHA and only with its approved isolated demo flags. Do not use this
  checkpoint to claim live UNKNOWN recovery, RET-05 restock, Finance production
  settlement, or Final UAT acceptance.
