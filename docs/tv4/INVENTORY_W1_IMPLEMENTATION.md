# TV4 — Inventory W1 implementation (PARTIAL)

## What is implemented in this branch draft
- In-memory stock state with available and reserved quantities.
- Reserve / consume / release operations.
- Operation idempotency per action + operationId; reusing an ID with a different SKU/quantity returns a conflict.
- Prevents available/reserved quantities from going below zero.
- REST proposal under `/internal/inventory`; validation and structured error responses.
- Unit tests for reserve, insufficient stock, replay, release replay, consume replay, payload mismatch, and replacing stock while reservations exist.

## Important limitations — do not mark integrated/DONE yet
- State is in memory and is lost on restart. This is not the required DB-backed timeout/replay implementation.
- Internal endpoint authentication/authorization is not implemented. Do not expose this service directly to the public network.
- API names and payloads are a proposal and must be agreed with TV3 (checkout/order) and TV5 where applicable before integration.
- This implementation does not yet persist operation records transactionally or handle process crashes between stock update and idempotency record.
- No Kafka outbox/event publishing is implemented.
- Tests are authored but have not been executed in this environment because the project has no Gradle wrapper and Gradle is not installed here. Run `gradle :inventory-service:test` from `backend/` in a machine with Gradle installed.

## Proposed API
- `PUT /internal/inventory/{sku}/stock` — set available stock for bootstrap/dev; rejects negative stock and replacement while reservations exist.
- `GET /internal/inventory/{sku}/stock` — read available/reserved quantity.
- `POST /internal/inventory/reservations` — body `{ "operationId": "checkout-123", "sku": "SKU-1", "quantity": 2 }`.
- `POST /internal/inventory/consumptions` — same body shape, consumes reserved quantity.
- `POST /internal/inventory/releases` — same body shape, releases reserved quantity back to available.

Error status proposal: 400 invalid request; 404 unknown SKU; 409 insufficient stock / idempotency key conflict. Contract is not final until reviewed by consumers.
