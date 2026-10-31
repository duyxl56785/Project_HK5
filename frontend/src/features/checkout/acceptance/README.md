# TV3 live two-Store acceptance

**Owner-review handoff: all 5 live cases are NOT_RUN — 0 executed, not PASS or SKIP.**
Test source comes from local `549d3f75e94c11da9ff93b9d2541d058e3019d61`,
published on top of #136 `7672fa394c580a47472b533991feb2d2608722a8`.
Journal implementation `9b08de1` is not included; it remains local awaiting owner acceptance.

Runs against an **already running, owner-configured disposable stack**. No seed,
server startup, runtime flags, fake JWT, mocked network or production code changes.
Use a dedicated Customer with an empty Cart and two real Products in distinct
Stores/SKUs. Tests leave their Orders/stock in the disposable stack for inspection.
Do not run concurrent activity against that stack; global Inventory ID comparisons
deliberately detect new IDs, including IDs unrelated to the expected reserve name.

From `frontend`, after the repository's `npm ci` and Chromium installation:

```bash
npx playwright test --config src/features/checkout/acceptance/live.config.ts --list
npx playwright test --config src/features/checkout/acceptance/live.config.ts --project two-store
npx playwright test --config src/features/checkout/acceptance/live.config.ts --project unknown-replay
npx playwright test --config src/features/checkout/acceptance/live.config.ts --project tv4-lifecycle
```

Required operator-supplied configuration (never commit credentials):

- `TV3_E2E_BASE_URL`: loopback frontend origin, using its real Gateway proxy.
- `TV3_E2E_CUSTOMER_EMAIL`, `TV3_E2E_CUSTOMER_PASSWORD`: actual Auth account.
- `TV3_E2E_PRODUCTS`: JSON array of exactly two `{ "productId": "UUID", "quantity": 2 }` entries.
- `TV3_E2E_PG_CONTAINER`, `TV3_E2E_PG_USER`, `TV3_E2E_ORDER_DB`, `TV3_E2E_INVENTORY_DB`:
  actual disposable PostgreSQL oracle, accessed through the local Docker socket.
  All oracle queries run inside BEGIN READ ONLY; no schema/role/data changes.
- Lifecycle additionally needs `TV3_E2E_SELLER_B_EMAIL`/`TV3_E2E_SELLER_B_PASSWORD`
  for the Seller managing the **second** Product's Store.
- Dispatch additionally needs `TV3_TV4_CONTRACT_REFERENCE`: the approved contract
  reference. This test requires CONSUME at committed DISPATCHED, not DELIVERED/COD.
  #135 at `3ec0b340` has moved CONSUME to DISPATCHED. This live assertion remains
  NOT_RUN here; source correction and other CI do not execute this test.

UNKNOWN replay requires **real fault cases prepared externally**, not an invented
production fault API. `TV3_E2E_CLAIM_CASE_FILE` and `TV3_E2E_TIMEOUT_CASE_FILE` each
name a private JSON file with `kind` (`claim-conflict`/`timeout`), `attemptId`,
`operationId`, original `key`/`body`, and `lines` (`productId,sku,storeId,quantity`).
Also include `evidenceFile`/`evidenceSha256`, `faultStillActive:true`, and
`settledBeforeReplay:true`. Owner-controlled fault injection must remain active
while the prepared unresolved attempt is replayed. The runner hashes the supplied
fault evidence; it does **not** create the fault or independently certify its origin.
Use the dedicated Customer corresponding to that attempt, with the Cart still claimed.

UNKNOWN cases require durable T1 journal identity/lines/fingerprint and unchanged
IDs/stock/Cart on same-key replay. Current #135's direct reserve bypasses T1, so
these are acceptance gates pending journal integration, not claimed green tests.
Missing configuration is an explicit prerequisite failure, never a skipped PASS.

The browser case checks UI checkout, actual Auth/Gateway/Order, PostgreSQL root and
children, exact available/reserved deltas, and sequentially completed concurrent
same-key replays. Lifecycle cases use real HTTP plus PostgreSQL. Traces/video are
disabled because Auth responses contain access tokens. Typecheck/`--list` do not
count as E2E execution. Keep source SHA, owner-approved application SHA and run
evidence separate; the runner does not infer a deployed version from local Git.
