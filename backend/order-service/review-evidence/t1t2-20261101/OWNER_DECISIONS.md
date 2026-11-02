# Owner review / remaining decisions

Refs #3 / #10 / #16. Source is proposed for review; this table grants no runtime or recovery authority.

| Owner | Evidence available | Decision / gate still required |
|---|---|---|
| TV2 / leader `duyxl56785` | Integration starts at main after #138/#139; Order-only delta; real PostgreSQL + HTTP fixture tests | Accept exact T1/T2 delta, Product/Store/Seller authority boundary, coordinate review. GitHub CI must run at final PR HEAD. |
| TV4 `phamthecuong2022006-alt` | Atomic PENDING claim, stable operation ID/payload, UNKNOWN quarantine, generation fencing; non-RESERVE HTTP path and DISPATCHED cutoff retained | Review shared generic caller's quarantine for all types; approve migration/FK/purge consequences; define authoritative UNKNOWN resolution DTO/API, actor and held-stock evidence before any future recovery. No speculative endpoint implemented. |
| TV5 `nguyenminhduy090-alt` | Frozen BigDecimal facts/outbox survive faults, Finance/COD regression; migration data preservation | Review draft facts including House/fee/net semantics; money/currency/policy authority for future recovery, UAT and evidence requirements. Current tests do not authorize production Finance policy or COD settlement. |
| TV1 + TV4 | No Auth/Gateway changes; draft does not grant current authority | Define fresh authorization/write-fence for recovery writes and revocation vs business-write evidence. Other owners' durable-authority branches are not imported into this PR. |
| TV1 / DBA + TV4/TV5 | 35-check V4→V5 rehearsal on PG16.6; FK lock and clean timeout rollback; separately provisioned runtime role denied until fixture grant | Verify actual Order schema/Flyway history and unused V5 on target, role ownership/SELECT+INSERT grants, backup/restore, maintenance window/session timeout, deployment/rollback owner. Do not modify V1–V4 or repair checksums to bypass drift. |
| TV1 + maintainer | Shipping already resides in Orders; new private draft stores it before success | Approve abandoned-draft PII access, logging/backup retention, no automatic TTL, and parent-purge ordering due to FK. |
| TV2 / leader + TV5 | Existing five acceptance tests published separately | Provide an explicitly authorized isolated stack/image SHAs, real Customer/two Seller accounts and Store/Product/SKU mapping, starting available/reserved stock, SQL/Inventory oracle access. |
| TV4 + TV5 | Loopback fault fixtures and DB race tests only | Supply real claim-conflict/timeout response-loss mechanism and receipts; physical reservation/replay/release/CONSUME and Playwright browser UAT remain NOT_RUN. |

Publish authorization after #138 is already given by the user; no additional permission is being inferred from earlier handoff tables. This does not replace mandatory owner source/schema review or permit merging/deploying. Five live acceptance tests remain **NOT_RUN — 0 executed**. Full Checkout/website recovery and E2E remain **PARTIAL/BLOCKED**.
