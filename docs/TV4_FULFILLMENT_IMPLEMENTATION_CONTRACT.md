# TV4 Fulfillment/COD implementation contract — draft

**Status:** DRAFT / integration blocked pending TV3 + TV5 + TV1 confirmation.  
**Owner:** TV4.  
**Purpose:** Make implementation and acceptance criteria explicit without inventing a second SellerOrder schema or claiming unimplemented behavior is working.

**QA revision (18/10/2026):** the matrix below contains 21 uniquely namespaced `TV4-W1-*` cases (12 FUL, 4 COD, 5 NOT). These are TV4 W1 scenario IDs, not the legacy IDs in `WEEK1_TEST_BASELINE.md`; legacy baseline meanings remain unchanged. All scenarios remain `NOT_RUN`. The prior PR39 draft IDs are superseded by the namespaced IDs below to avoid reusing a baseline ID for a different scenario.

## Current repository boundary

At the current baseline, `backend/order-service/README.md` describes order-service as a scaffold: no business endpoints, migrations, events, or authentication are implemented. TV3 owns checkout/order creation; TV4 owns fulfillment/COD/stock restoration; TV5 owns finance/settlement. Therefore this document is a proposal and test plan, not a production API contract.

Inventory currently addresses stock by SKU and supports `INCREASE`, `DECREASE`, `RESERVE`, `CONSUME`, and `RELEASE` with a stable `operationId` and payload-conflict protection. It does not itself establish Product/Store identity, look up reservations by order ID, or prove checkout-to-inventory integration.

## SellerOrder state naming — unresolved cross-owner decision (C06)

**No state vocabulary is selected or approved by this document.**

**TV4 proposal for C06 owner decision (not silently mapped):** adopt TV3's candidate `PLACED → ACCEPTED → PREPARING → READY_FOR_HANDOFF → DISPATCHED → DELIVERED` as the candidate DB/wire vocabulary, with `seller-order.status-changed.v1` event semantics. Treat `READY_FOR_HANDOFF → DISPATCHED` as the only candidate dispatch/handoff boundary for `CONSUME`, after Order status + outbox commit durably succeeds. This is a proposal for TV3 to confirm/revise; TV1/TV2/TV5 must still approve actor/Store authority, event publisher/payload, cancel/reject cutoff, and COD separation before implementation. The older `PACKING → SHIPPED` vocabulary remains a distinct proposal, not an alias.

 The earlier TV4 proposal used `PENDING_CONFIRMATION → CONFIRMED → PACKING → SHIPPED → DELIVERED`; TV3's current proposal uses `PLACED → ACCEPTED → PREPARING → READY_FOR_HANDOFF → DISPATCHED → DELIVERED`. These are not silently interchangeable. TV3 + TV4 must propose one canonical DB/wire/event vocabulary and obtain the required TV1/TV2/TV5 approvals for actor authority, ownership, event publisher, cancellation/rejection cutoff, and COD separation before implementation. Any migration must be V2+ and must not rewrite V1. The following table records only the earlier TV4 candidate for discussion; it is not an implementation contract:

| Current state | Action | Next state | Required guard |
|---|---|---|---|
| `PENDING_CONFIRMATION` | Seller confirms | `CONFIRMED` | Verified seller identity owns the Store for this child order |
| `CONFIRMED` | Seller packs | `PACKING` | Same Store ownership; valid current state |
| `PACKING` | Seller ships / carrier handoff | `SHIPPED` | Same Store ownership; valid current state |
| `SHIPPED` | Mark delivered | `DELIVERED` | Authorized delivery actor; valid current state |
| `PENDING_CONFIRMATION` | Customer cancels | Cancelled terminal state | Authenticated customer owns the parent order; cancellation policy confirmed by TV3 |
| `PENDING_CONFIRMATION` | Seller rejects | Rejected terminal state | Same Store ownership; non-blank rejection reason |

Rules:
- Reject invalid transitions; never silently coerce a state.
- Derive customer/seller/admin identity from the verified authentication boundary, not a caller-supplied owner/store header.
- A seller may only read or mutate SellerOrders belonging to their verified Store. Cross-Store requests must be denied without leaking another Store's order details.
- State mutation and durable event/outbox write must commit atomically once the shared event contract is agreed.
- Exact enum names, actor rules for delivery, terminal-state representation, and cancellation/rejection policy require TV3 confirmation before production implementation.

## Inventory lifecycle proposal — NOT APPROVED YET

**Quantity/identity constraints:** every quantity is a positive signed 64-bit integer (`long`), with one canonical SKU per operation. Reject zero, negative, decimal, or out-of-range quantities; do not coerce them. Inventory's key is SKU-only; provider currently trims leading/trailing SKU whitespace and remains case-sensitive. Consumers should send canonical, unpadded SKU. `productId` is never a substitute for SKU. Product owns the target immutable globally unique SKU, but Product DB/API enforcement and authoritative Product→SKU lookup remain pending. Store isolation requires TV1 verified identity plus TV2's authoritative current Store→Seller facts; never infer ownership from JWT `sub`/role, body, headers, or event payload.

Each inventory action must have its own deterministic, persisted operation ID. A retry of the same logical action reuses the same ID and same canonical payload; reusing the ID with a different payload must fail as a conflict.

| Business point | Candidate operation | Preconditions / notes |
|---|---|---|
| Checkout has passed catalog, price, and finance checks, before order confirmation | `RESERVE` | Requires authoritative productId-to-SKU mapping and a durable reservation line set; TV2/TV3 must confirm identity scope |
| Confirmed dispatch / carrier handoff | `CONSUME` | Proposed at handoff, not at `DELIVERED`; TV3 and TV5 must approve lifecycle semantics |
| Proven cancellation/rejection before dispatch | `RELEASE` | Only release quantities known to have been reserved and not consumed; exactly-once behavior comes from stable operation IDs |
| Verified physical return after dispatch/delivery | Separate `INCREASE` | Only after physical receipt and inspection; do not reuse pre-dispatch `RELEASE`. Return/refund eligibility, quantity and finance handling need TV3/TV5 approval |

Do not release stock on an ambiguous timeout by blindly issuing a new operation ID. Persist the intended operation and retry with the same ID. If the outcome remains unknown, reconcile using the inventory operation ledger / agreed query mechanism before choosing a compensating action. The current Inventory API has no orderId reservation lookup, so a recovery adapter needs an agreed durable reservation journal or a new reviewed API.

## COD and Finance boundary — TV5 confirmation required

- Only a verified ADMIN can confirm COD, and only after `DELIVERED`; preserve actor/time audit and keep COD payment status separate from delivery status. Duplicate action must be idempotent.
- TV5's scoped Finance boundary is settlement only when both `DELIVERED` and COD `PAID`, based on trusted immutable money/House snapshots, unique per SellerOrder, atomic/race-safe, and Store-scoped for reads. Inventory dispatch, `CONSUME`, replay, or balances are not delivery/payment/settlement evidence.
- Currency, numeric bounds, scale/rounding stage, fees, shipping/discount/refund and settlement snapshots remain M01/business-owner decisions; do not infer currency or round money based on Inventory.

- Only a verified ADMIN may record COD payment.
- COD payment is allowed only after the child order is `DELIVERED`.
- Repeated confirmation must not create a second payment/settlement effect; use a stable payment/action identifier and a durable idempotency record.
- Keep COD payment status separate from fulfillment delivery status.
- Do not trigger seller settlement until TV5's approved finance policy and required COD-paid condition are satisfied.
- Currency, scale, rounding, settlement snapshot, COD action ID, and transaction boundary are still pending Finance owner confirmation. Do not infer these from Inventory or checkout data.

## Event and notification proposal — TV3/TV1 confirmation required

Candidate event: `seller-order.status-changed.v1`.

Minimum candidate fields (not a final shared schema): eventId, eventType/version, occurredAt, sellerOrderId, parentOrderId, storeId, previousStatus, currentStatus, actorType, and correlationId. Do not include secrets or unnecessary customer PII.

- Publish through a transactional outbox in the same transaction as the state change.
- Consumers must deduplicate by eventId and tolerate at-least-once delivery.
- Notify only the appropriate customer and the owning Store's seller; ADMIN visibility must follow the approved authorization policy.
- Agree the source of truth for recipient identity, event field names, retry/backoff, poison-message/DLT behavior, and retention with TV1/TV3 before wiring a consumer.
- Do not claim Kafka/outbox delivery is implemented or tested from this proposal.

## Legacy baseline traceability — unchanged IDs and meanings

`WEEK1_TEST_BASELINE.md` on branch `tv4/week1-analysis` is the authoritative original baseline. This PR does not edit that file, relabel its rows, or transfer evidence from a legacy case to a supplemental scenario. Every legacy case remains `NOT_RUN` until independently evidenced.

| Legacy baseline ID | Original scenario | Original expected result | Baseline status |
|---|---|---|---|
| TV4-INV-01 | Consume lượng hàng còn đủ | Tồn giảm đúng số lượng một lần | NOT_RUN |
| TV4-INV-02 | Consume lượng lớn hơn tồn | Không trừ kho; trả lỗi thiếu hàng | NOT_RUN |
| TV4-INV-03 | Consume nhiều item, một item thiếu | Toàn operation rollback, không trừ một phần | NOT_RUN |
| TV4-INV-04 | Consume lại cùng operation/order | Không trừ kho lần hai | NOT_RUN |
| TV4-INV-05 | Release lặp cùng operation | Không cộng kho lần hai | NOT_RUN |
| TV4-INV-06 | Hai request đồng thời tranh cùng tồn | Không oversell; tồn không âm | NOT_RUN |
| TV4-FUL-01 | Seller xem đơn của store mình | Chỉ trả đơn thuộc store đó | NOT_RUN |
| TV4-FUL-02 | Seller thao tác đơn của store khác | Bị từ chối, không đổi trạng thái | NOT_RUN |
| TV4-FUL-03 | Chuyển trạng thái theo thứ tự hợp lệ | Chỉ chuyển trạng thái được phép | NOT_RUN |
| TV4-FUL-04 | Từ chối đơn không có lý do | Validation từ chối request | NOT_RUN |
| TV4-FUL-05 | Hủy đơn không còn trạng thái được phép hủy | Từ chối; không hoàn kho | NOT_RUN |
| TV4-FUL-06 | Hủy/từ chối và retry | Hoàn kho đúng một lần | NOT_RUN |
| TV4-COD-01 | Admin xác nhận COD trước `DELIVERED` | Từ chối; không đánh dấu `PAID` | NOT_RUN |
| TV4-COD-02 | Admin xác nhận COD sau `DELIVERED` | Ghi nhận người và thời điểm xác nhận | NOT_RUN |
| TV4-COD-03 | Lặp lại xác nhận COD | Idempotent; không ghi nhận lần hai | NOT_RUN |
| TV4-COD-04 | Seller hoặc non-ADMIN gọi API xác nhận COD | Bị từ chối; không đổi trạng thái thanh toán | NOT_RUN |
| TV4-NOT-01 | Nhận event status hợp lệ | Tạo thông báo cho recipient đúng | NOT_RUN |
| TV4-NOT-02 | Consumer nhận event trùng | Không tạo thông báo trùng | NOT_RUN |
| TV4-NOT-03 | Người dùng đọc notification của người khác | Bị từ chối | NOT_RUN |
| TV4-NOT-04 | Kafka tạm dừng rồi hoạt động lại | Retry/recovery không mất hoặc nhân đôi xử lý | NOT_RUN |
| TV4-NOT-05 | Event không hợp lệ hoặc lỗi vĩnh viễn | Được ghi nhận và chuyển xử lý lỗi theo contract | NOT_RUN |

## Supplemental acceptance test matrix

Every case remains **NOT_RUN** until there is an executable test and its log tied to a commit SHA.

| ID | Scenario | Expected result | Status / blocker |
|---|---|---|---|
| TV4-W1-FUL-01 | Own Store seller confirms a pending child order | One valid transition; durable state change | NOT_RUN — SellerOrder API/schema absent |
| TV4-W1-FUL-02 | Seller from another Store reads/updates child order | Denied; no state mutation or data leak | NOT_RUN — verified identity + Store mapping absent |
| TV4-W1-FUL-03 | Valid fulfillment sequence under the finally approved state machine | Each transition accepted exactly once | NOT_RUN — shared state machine absent; C06 unresolved |
| TV4-W1-FUL-04 | Invalid or repeated transition | Conflict/validation error; no extra side effect | NOT_RUN — endpoint/error contract absent |
| TV4-W1-FUL-05 | Customer cancels outside allowed state | Denied; stock unchanged | NOT_RUN — cancellation policy/state model pending TV3 |
| TV4-W1-FUL-06 | Customer cancels pending child order | Ownership checked; release once for reserved lines | NOT_RUN — parent ownership + reservation journal pending |
| TV4-W1-FUL-07 | Seller rejects pending child order without reason | Validation error; no state/stock change | NOT_RUN — rejection contract absent |
| TV4-W1-FUL-08 | Seller rejects pending child order with reason | State transition and release once | NOT_RUN — TV3/Inventory lifecycle agreement pending |
| TV4-W1-FUL-09 | Retry same release operation ID and payload | No second stock restoration | NOT_RUN — no integrated order adapter |
| TV4-W1-FUL-10 | Reuse operation ID with different payload | Conflict; no stock mutation | Inventory module regression has evidence; order E2E NOT_RUN |
| TV4-W1-FUL-11 | Seller is `SUSPENDED` after an order was placed | Apply only the explicit owner-approved policy; never infer permission from a role claim | NOT_RUN — TV2 policy and current Seller status provider pending |
| TV4-W1-FUL-12 | Store is closed after an order was placed | Apply only the explicit owner-approved policy to existing orders | NOT_RUN — TV2 Store status provider and policy pending |
| TV4-W1-COD-01 | Non-ADMIN attempts to mark COD paid | Denied; no payment/settlement write | NOT_RUN — verified runtime auth pending |
| TV4-W1-COD-02 | ADMIN marks COD paid before DELIVERED | Denied; no payment/settlement write | NOT_RUN — SellerOrder/COD endpoint absent |
| TV4-W1-COD-03 | ADMIN marks COD paid after DELIVERED | One payment action; settlement only under approved Finance policy | NOT_RUN — shared COD/Finance contract pending |
| TV4-W1-COD-04 | Duplicate COD confirmation | Idempotent response; no duplicate settlement | NOT_RUN — durable COD idempotency absent |
| TV4-W1-NOT-01 | Status changes successfully | One durable outbox event with agreed schema | NOT_RUN — publisher/schema absent |
| TV4-W1-NOT-02 | Outbox event delivered more than once | Consumer effect deduplicated | NOT_RUN — consumer absent |
| TV4-W1-NOT-03 | Event recipient belongs to another Store/customer | No unauthorized notification | NOT_RUN — recipient/permission contract pending |
| TV4-W1-NOT-04 | Temporary consumer failure then retry | Retry/recovery follows approved policy | NOT_RUN — consumer/retry policy absent |
| TV4-W1-NOT-05 | Permanent malformed event | Isolated and observable; no infinite retry | NOT_RUN — DLT/alerting policy absent |

## Required owner decisions before implementation/integration

- **TV3:** SellerOrder persistence, authoritative state names, transition ownership, parent/customer relationship, cancel/reject policy, dispatch boundary, reservation journal, and operation IDs.
- **TV2 + TV3 + TV4:** TV2's target invariant is **globally unique SKU owned by Product**; `productId` is not `inventorySku`. Inventory remains SKU-keyed. Product DB/API enforcement and authoritative Product→SKU/current Store↔Seller facts remain PENDING; do not seed fixtures or enable live integration until the provider is authoritative.
- **TV2 + TV1 + TV4:** current Seller status (`SUSPENDED`), current Store status (including closed), and recipient account resolution Store→Seller→account must come from approved provider data. Do not infer Store/Seller ownership or notification recipients from JWT `sub`/role, request body/header, or event payload. TV2 must decide whether a suspended Seller or closed Store may continue processing orders placed before the status change; the new W1-FUL-11/12 cases remain NOT_RUN until that policy is signed off.
- **TV1:** verified identity/roles, seller-to-Store authorization source, service-to-service authentication, and event/notification permissions. Inventory's shared `X-Inventory-Internal-Token` covers initialization and INCREASE/DECREASE as well as RESERVE/CONSUME/RELEASE; do not provide that broad token to Checkout. TV1 must approve caller-specific credentials and endpoint+operation allowlists before consumer wiring.
- **TV5:** COD action/idempotency contract, finance policy approval, money representation/rounding/snapshot, and settlement condition.
- **TV3 + TV4 + TV5:** event publisher ownership, final event schema, outbox/retry/deduplication behavior.
- **TV3 + TV4 — retry/recovery proposal awaiting agreement:** persist the exact immutable canonical line set and a distinct stable `operationId` for each RESERVE/CONSUME/RELEASE before HTTP; persist each outcome. TV4 proposes at most 3 retries after the initial attempt, delays 250 ms, 1 s, then 4 s with jitter (target overall budget ≤10 s), only for timeout/connection uncertainty, 5xx, or `IDEMPOTENCY_CLAIM_CONFLICT`, and only with the exact same saved operationId/type/payload. This is NOT agreed until TV3 confirms/revises. On exhausted budget or unknown commit, mark UNKNOWN, alert and hold for reconciliation/manual ops; never blind RELEASE. Do not retry `IDEMPOTENCY_KEY_REUSED` with changed payload. `CONSUME` only after committed dispatch; `RELEASE` only for verified pre-dispatch cancellation/rejection while reservation is held; post-dispatch returns require received/inspected goods then separate `INCREASE`.
- **TV3 + TV4 + TV1 + TV2 + TV5:** canonical state enum/wire/event names, dispatch actor/Store authority, cancel/reject cutoff, and Order-transition/outbox commit before any Inventory CONSUME. The two state vocabularies above are unresolved alternatives, not aliases.

## Completion rule

Do not mark F11–F14 or the order-service checklist complete based on this document. Completion requires reviewed production code, executable tests, test logs tied to a commit, and relevant owner/consumer approval. Until then, overall TV4 status remains **PARTIAL / integration NOT_VERIFIED**.
