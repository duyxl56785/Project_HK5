# Fulfillment & COD — TV4

`FulfillmentBoard` is a Store-scoped rendering component for SellerOrder
fulfillment and the visible COD record state. It has no HTTP client, route,
token handling, local state transition, payment write, Inventory call or API
contract.

The host supplies only data already authorized for one Store and the commands
currently allowed by an approved authority. The component rejects a malformed,
duplicate or mixed-Store collection as a whole and hides stale data for error
and unauthorized states. Commands are opaque callbacks: this UI does not map a
button to a SellerOrder transition or decide whether COD can be recorded.

Run its focused checks from `frontend` after dependencies are available:

```powershell
node --experimental-strip-types --test src/features/fulfillment/fulfillmentModel.test.mjs src/features/fulfillment/FulfillmentBoard.test.mjs
```

Runtime Store authorization, SellerOrder persistence/status, action DTOs,
COD-after-delivery policy, idempotency, API paths and event notifications remain
pending cross-owner contracts.
