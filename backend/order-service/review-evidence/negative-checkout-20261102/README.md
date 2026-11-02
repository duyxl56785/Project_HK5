# TV3 negative Checkout acceptance — 2026-11-02

Refs #3 #16 #145. **6 executed / 6 PASS / 0 FAIL / 0 SKIP**, Playwright exit **0**. Real Gateway/API + PostgreSQL read-only oracle; **not browser E2E**. No mocked network, fake JWT or fault injection.

Source baseline: `99979a75efa043b746704e38800db902cd9c3363`; executable tests and config SHA256 in source.json. Existing approved stack `tv3-live-145-20261101` remains deployed at `bf8eb3a027c72ad9fde81d0aa5dabc4e73bebacf`. Source diff from deployed version in Order/Cart/Inventory is only the owner Javadoc correction in OrderEvents.java; executable logic unchanged. No redeployment/build was done. This is live evidence against that existing deployment, not a claim to have deployed 99979a75.

## Per-case evidence

| Case | Result | HTTP / error message | Cart revision | Orders | SellerOrders | Inventory operations |
| --- | --- | --- | --- | --- | --- | --- |
| replay-body | PASS | 409 / IDEMPOTENCY_PAYLOAD_MISMATCH | 15 → 15 | 4 → 4 | 8 → 8 | 7 → 7 |
| replay-shipping | PASS | 409 / IDEMPOTENCY_PAYLOAD_MISMATCH | 15 → 15 | 4 → 4 | 8 → 8 | 7 → 7 |
| expected-items | PASS | 409 / Cart and expectedItems differ | 17 → 19 | 4 → 4 | 8 → 8 | 7 → 7 |
| catalog-price | PASS | 409 / Product price changed since adding to cart | 23 → 25 | 4 → 4 | 8 → 8 | 7 → 7 |
| insufficient-stock | PASS | 409 / Not enough stock for TV3-NEG-b6396d50-B | 29 → 31 | 4 → 4 | 8 → 8 | 8 → 8 |
| product-inactive | PASS | 409 / Product, seller or store not available | 35 → 37 | 4 → 4 | 8 → 8 | 9 → 9 |

Each case has `before.json`, `after.json`, `http.json`, `comparison.json`, `result.json` under execution/. `results.json` includes per-SKU available/reserved before/after. Stock and version remain exactly unchanged across every rejected Checkout, including the healthy first Store in the insufficient-second-Store case. Cart rows were independently verified with read-only SQL as well as Gateway GET. Replay mismatch leaves revision unchanged; known-safe preflight/stock rejection increments revision twice (claim + release), retaining all items and clearing the Cart claim.

Preflight cases add no Inventory journal/draft or committed Inventory operation. Stock rejection creates exactly one durable draft and one generation-1 RESERVE journal, **REJECTED / INSUFFICIENT_STOCK**; no committed Inventory operation, no partial stock mutation, no Order/SellerOrder/outbox. That definitive rejection is separate from UNKNOWN, not evidence for a 409 claim-conflict/timeout.

## Fixture and side effects

All writes used existing public APIs through frontend → Gateway; no internal/fault endpoints or SQL writes. Logged in with real seeded CUSTOMER, both SELLER owners and seeded ADMIN. Read local demo credentials in memory only; credentials never appear in publication.

Created two NEW synthetic Products through Seller APIs, one in each approved demo Store, initial available=8 each; submitted and approved through actual moderation APIs. Existing seed Products/stock were not changed. Setup deliberately placed one baseline Order `2b119e70-cac0-453e-b122-8f0a91e6e5fa` with two SellerOrders, quantity 1 each, to test successful-key replay mismatch. Setup changed global counts **3→4 Orders / 6→8 SellerOrders** and each new SKU **8 available/0 reserved → 7 available/1 reserved**. This setup is not an additional acceptance PASS; it is disclosed in setup snapshots. The six negative requests created no further Order. Baseline is retained for inspection; no automatic cancellation/RELEASE.

New SKU A: `TV3-NEG-b6396d50-A`; new SKU B: `TV3-NEG-b6396d50-B`. For price: add to Cart at 10000, deactivate B, edit to 11000, submit/reapprove ACTIVE, then Checkout with the untouched Cart snapshot. For stock: add qty2 of both Products, use owner stock API to set B available=1, snapshot, submit Checkout, snapshot, restore B available=7 through the same API. The two INITIALIZE stock-setting operations occur OUTSIDE the Checkout measurement and explain total Inventory operation count changes **7→8→9** across cases. During every individual Checkout, the operation count is unchanged. For sellability: add valid Products, then deactivate B through its Seller API before Checkout. Only this run's unclaimed Cart lines are removed afterward via public API. B remains INACTIVE at11000; A remains ACTIVE at10000; both have available7/reserved1 for the baseline Order. No real customer PII or original key is exported (only key SHA256).

## Actual commands, exits and harness issue

See commands.json. `node --check` both files: exit0. Playwright discovery:6 tests/exit0. Playwright run:6 PASS/exit0, JUnit and JSON reporters retained. Full backend build **NOT_RUN**, production source unchanged and existing green CI not rerun manually.

Outer local wrapper returned **exit1 AFTER the successful tests**: its Docker preservation assertion compared positional Mounts arrays; Docker returned the identical mounts in another order. Saved before/after raw snapshots were independently normalized by destination+source and compare equal for all15 containers, IDs, running state, StartedAt, RestartCount and mount contents. No service restarted, no volume removed, no acceptance rerun to hide this wrapper failure. The wrapper was corrected locally for future use; correction was not needed to change any test assertion or result. Both original and corrected wrapper hashes and raw snapshot hashes are retained.

## Limits and owner gates

Original live suite remains **3 historical PASS / 2 UNKNOWN NOT_RUN–BLOCKED, 0 executed UNKNOWN**. These six tests do not replace the two missing real-fault cases. TV4/TV5 still must supply owner-approved active claim-conflict/timeout fixture, original operation identity, receipt/evidence and an isolated fault window. No generic UNKNOWN retry/RELEASE, crash recovery resolution, production rollout, Auth revocation-vs-write or Finance policy acceptance claimed. Issue #3/#16 not marked DONE.

No new actionable TV3 bug observed in these six executions. No production code/migration/runtime flags changed. No changes/review/comment to #144 or #69, no thread resolved, no merge. PR #145 description read/update currently **BLOCKED**: normal `gh api .../pulls/145` returns `403 Forbidden` (exit1); append-only owner text is prepared in PR_DESCRIPTION_UPDATE.md to avoid overwriting unseen owner edits.

## Integrity/privacy

Raw reporter/logs and privacy-projected snapshots are copied byte-for-byte; SHA256SUMS covers publication, raw-input-sha256.json binds local captured inputs. Oracle exports fixed SELECT facts only (no DB dump, shipping, contacts, Auth bodies, raw draft/outbox payloads, token or key). API mutation logs record paths/status and approved safe response projections, not request credentials/shipping. Stack raw inspect stays local; only preservation verification and hashes are published.
