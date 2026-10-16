# inventory-service

**Owner:** TV4.

## Implemented first increment (partial)
- In-memory available/reserved stock model.
- Reserve, consume, and release operations with replay protection by `operationId` per action.
- Prevents negative quantities and rejects reuse of an operation ID with a different payload.
- Proposed internal REST API under `/internal/inventory`.
- Unit test source in `src/test/java/.../InventoryOperationServiceTest.java`.

## Run
From the `backend/` directory, on a machine with Gradle installed:

```bash
gradle :inventory-service:test
gradle :inventory-service:bootRun
```

## Limitations — not DONE/integrated yet
- State and idempotency records are in-memory; restart loses them.
- Internal endpoint authentication/authorization is not implemented. Do not expose this service directly to the public network.
- Endpoint names/payloads are a proposal pending review by TV3 and relevant TV5 consumers.
- No database transaction/outbox or Kafka event publishing yet.
- The JUnit suite has not been run in this environment; a manual Java smoke check of the core service passed. See `docs/tv4/INVENTORY_W1_IMPLEMENTATION.md`.
