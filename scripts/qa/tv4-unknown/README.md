# TV4 UNKNOWN fixture preparation

Refs #4 #11 #17 #18 #152. NOT AUTHORIZED FOR LIVE EXECUTION.

`lost-response.mjs` exports a handler only: importing it starts no server, container,
migration or flag. Tests use an explicitly fake loopback HTTP harness, not Inventory.
`node --test scripts/qa/tv4-unknown/lost-response.test.mjs` proves proxy mechanics only.
The handler forwards exactly one request per incoming call, verifies an approved
operationId plus byte fingerprint, consumes the real upstream response then drops
the downstream connection. It does not invent a provider status or retry/release.
Its receipt deliberately claims neither COMMIT nor UNKNOWN acceptance. The transport
body SHA is distinct from Inventory's canonical payload fingerprint; retain both.

## Before an authorized run

TV2 must approve exact source SHA, disposable project/ports/volumes and network
attachment; no shared stack or Internet exposure. Current helper accepts only an
explicit loopback upstream; container-network wiring needs a separately reviewed
runner, not a silent expansion of this allowlist. TV3 confirms stable Checkout
attempt/key/body, operationId, Product→SKU lines and Store/SellerOrder owner tuple.
TV4 reviews target/receipt; TV5 approves QA execution. Both runtime gates remain OFF
until separately authorized. No automatic cleanup or volume deletion is included.

## Lost-response receipt and oracle

- Fake fixtures only. Record source and fixture SHA, before/after request timestamps,
  proxy receipt and redacted logs. Do not store tokens or shipping PII.
- Read-only provider transaction: operationId/type/canonical fingerprint and exact
  SKU quantities, stock available/reserved before and after; committed row and stock
  delta must match. A transport 200 or operationId string alone is insufficient.
- Order: durable draft, journal attempt/generation/state/immutable intent, outbox;
  Cart: lock and fingerprint. Preserve raw receipt bytes and SHA256.
- Expected: RESERVE committed once, Order quarantined UNKNOWN/needs reconciliation,
  Cart locked, no new ID or RELEASE/CONSUME. Same key/body replay must not mutate again.
- Main uses aggregate Inventory; candidate #69 ownership lookup is NOT an HTTP
  provider and V3 is not enabled. Record that gap; do not fabricate reservation rows.
- When evidence cannot bind the exact owner/intent/outcome: stop and reconcile
  manually. Live case remains NOT_RUN/BLOCKED until receipts and approvals exist.

## Claim-conflict

BLOCKED_DESIGN. PostgreSQL READ COMMITTED plus ON CONFLICT and SKU row locks normally
exposes the committed winner to the following lookup. A duplicate race is not evidence
of the special claim=0/lookup-absent branch. No fake 409, DB deletion, monkey patch or
production fault endpoint. TV3/TV4 must provide a legitimate mechanism or agree with
TV5 to retain this case as explicitly unproven. Proxy tests do not prove this case.
