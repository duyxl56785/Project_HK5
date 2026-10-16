# TV4 — Week 1 test baseline

Status vocabulary: `PASS`, `FAIL`, `NOT_RUN`; completion: `DONE`, `PARTIAL`, `NOT_VERIFIED`. Do not report PASS until the test has actually run and evidence is attached.

## Inventory service unit tests authored (NOT_RUN in this workspace)
1. Reserve decreases available and increases reserved quantity.
2. Reserve replay with the same operation ID does not reserve twice.
3. Insufficient stock is rejected and available quantity remains non-negative.
4. Release replay restores stock only once.
5. Consume replay does not consume reserved quantity twice.
6. Reusing an operation ID with a different payload is rejected.
7. Stock cannot be overwritten while a reservation exists.

## Still required for Issue #4
- DB-backed tests for timeout/retry/replay and persistence across restart.
- Fulfillment state transitions and Seller ownership checks.
- Cancel/reject flow and release stock exactly once per line item.
- COD confirmation only after DELIVERED and idempotent finance update.
- Notification/event routing, authorization and consumer replay.
- Capture command output / CI run and report each case PASS/FAIL/NOT_RUN with evidence.
