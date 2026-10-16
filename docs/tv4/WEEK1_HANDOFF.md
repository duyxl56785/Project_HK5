# TV4 — Week 1 handoff

## Current status: PARTIAL / NOT_VERIFIED
- Added an initial in-memory Inventory implementation and unit-test source.
- Test source exists, but test execution has not been verified in this workspace.
- The proposed `/internal/inventory` API is not yet agreed with TV3/TV5.

## Follow-up before claiming DONE
1. Agree request/response and error contract with TV3 checkout/order owner and relevant TV5 consumer.
2. Replace in-memory maps with database entities/repositories and transaction-safe idempotency records.
3. Add authentication for internal service calls and enforce network boundary.
4. Run Gradle tests; attach logs; add integration tests for restart/timeout/replay.
5. Implement/test fulfillment, cancellation/rejection stock release, COD idempotency, and notification event handling.
6. Open PR for review and request TV3/TV5 review where their contracts are affected.
