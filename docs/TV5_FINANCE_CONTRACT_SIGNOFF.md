# TV5 Finance/COD and six-contract sign-off checkpoint

2026-10-18, Asia/Bangkok. Refs #5, #12, #28, #37, #38, #39.
Version **TV5-FIN-v0.1 — FOR OWNER SIGNATURES**. This is a proposed decision package,
not approval by the project owner or another team member. No production policy flag,
Checkout source, Inventory source, shared DTO, entity or migration changes here.

## Source and decision evidence

- Checkout #28: `bd5ff09e89a50fea198fac38967d1565215fe2fe`.
- Catalog #37: `9c283386f791cc3eb3284650da8b1f356d128835`.
- Inventory #38: `9b8ae3557d1b1b50ee27d1579c7cd740576e0648`.
- Fulfillment #39: `bf137327cc9431020fa8eb3e280c9b2604a86493`.
- [TV2 S01/C01/M01/E01 and global SKU proposal](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6238333126).
- [TV3 batch consumer response](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6238558286).
- [TV3 six-contract checkpoint](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6238918032).
- [TV4 latest six-contract response](https://github.com/duyxl56785/Project_HK5/issues/4#issuecomment-6244373601).
- [TV4 C03–C07 provider scope decisions](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6237964074).
- [TV1 Auth v1.0/OD-07/OD-09 baseline](https://github.com/duyxl56785/Project_HK5/pull/24).
- [TV5 prior business requirements confirmation](https://github.com/duyxl56785/Project_HK5/pull/28#pullrequestreview-5561476940).

No evidence above contains all required signatures for the same six versioned contracts.
TV4's new response preserves PENDING/REVISE, including M01; it does not approve currency.
Baseline Auth decisions remain valid, distinct from concrete service-pair implementation.
Review approvals for a module do not sign another owner's contract.

## Decisions, owners, signatures and implementation permission

| ID / overall state | Specific technical choice being reviewed | Accountable owner / required signers | Confirmation evidence and remaining decision | Modules permitted after relevant sign-off and prerequisites |
|---|---|---|---|---|
| **S01 PENDING** | TV2 proposes Store in Seller DB/service, local Seller FK + UNIQUE(seller_id), canonical unique slug; Product keeps logical Store ID, no cross-DB FK. Current Seller/Store ownership, APPROVED/OPEN/House facts from trusted provider. | TV2 Store; TV1 trust boundary; signatures TV1+TV2, TV3+TV4 consumer isolation, TV5 Finance read scope. | TV2 proposal + TV3 response + TV4 checkpoint above; TV1 final S01/current-authority contract absent. Role/sub/body/header is not Seller/Store proof. | TV2 Store persistence/current-facts read and Product logical reference; TV3 Cart/Order consumer; TV4 fulfillment and TV5 Store-scoped Finance only with verified actor adapter. |
| **C01 REVISE** | Candidate POST /internal/catalog/checkout/validate, 1–100 distinct Product IDs; expected Store/Seller/price/currency comparison only. One matching response per ID, READY snapshot only; any missing/duplicate/non-READY/timeout aborts whole Checkout before RESERVE. Snapshot needs authoritative productId, inventorySku, storeId, sellerId, unitPrice, currency, sellability and houseStore. | TV2 provider + TV3 consumer, TV1 caller/delegation, TV5 currency; TV4 SKU compatibility. | TV2/TV3 direction agreed as discussion basis only. Versioned wire/UUID-vs-opaque IDs, freshness and error response not accepted. Current proposed route is denied 403, no production provider. | TV2 authenticated Catalog batch controller/adapter; TV3 CatalogPort/Cart DTO V2+ and contract tests after provider authority and S01/Security/M01. |
| **E01 REVISE** | READY / PRODUCT_NOT_FOUND / PRODUCT_NOT_SELLABLE / OWNERSHIP_MISMATCH / PRICE_CHANGED / CURRENCY_MISMATCH; INVALID_REQUEST / DEPENDENCY_UNAVAILABLE reject batch. Missing backend facts are not proof of product absence. No private snapshot on rejection. | TV2 domain errors, TV1 auth/privacy, TV3 consumer mapping; TV5 currency/privacy boundary. | TV3 explicitly requests HTTP/envelope/masking/timeout revision. TV1 baseline 401 invalid caller, 403 forbidden operation, 503 verifier unavailable does not choose all Catalog 404/409 cases. | TV2 Advice/HTTP tests, TV1 service-pair/privacy tests, TV3 no-partial-side-effect tests after exact status+envelope signed. |
| **M01 PENDING** | Concrete TV5-FIN-v0.1 candidate below: VND, scale0 calculations, explicit DB bound, per-SellerOrder HALF_UP fee, immutable monetary/currency/House/rate snapshots. | TV5 Finance; TV2 price/currency, TV3 Cart/Order representation/grouping, TV4 COD/refunds; business decision maker/project owner and TV1 for enable authority. | Existing 5%/House0/net/eligibility requirements confirmed by TV5, but no all-owner approval of currency/precision/stage/bounds/refund. TV4 explicitly still requests this. | TV2 Product monetary schema/DTO; TV3 V2+ Cart/Order snapshots and compatible calculation; TV5 settlement only after M01 + COD source/security signed. |
| **Inventory PENDING; C06 REVISE** | Product owns exact immutable global SKU target; no productId=sku. T1 durable original SKU/positive-long qty + separate stable RESERVE/CONSUME/RELEASE IDs before HTTP; T2 persists result. Exact-payload bounded retry; UNKNOWN stays hold, never blind RELEASE. CONSUME after committed dispatch, RELEASE only proven pre-dispatch cancellation; inspected returns separate INCREASE. | TV2 SKU/Store; TV3 journal/consumer; TV4 provider/lifecycle; TV1 operation scope; TV5 delivery/COD separation. | TV4 confirms provider scope only. Global Product enforcement absent. PACKING→SHIPPED (#39) vs READY_FOR_HANDOFF→DISPATCHED (TV3) requires one state/event version; budget/backoff/manual owner unresolved. | TV2 authoritative SKU source; TV3 V2+ journal/recovery/adapter; TV4 restricted provider/lifecycle; TV5 receives delivery/payment facts only after event/source approval. |
| **Security PENDING (OD-09 baseline DECIDED)** | Distinct per-caller→callee credential, method/path/operation allowlist and independently verified delegated/current actor. Order→Catalog batch, Order→Cart claim/finalize/release, Order→Inventory reservation-only. Never give broad init/INCREASE/DECREASE credential to Checkout. | TV1 security; TV2/TV3/TV4 provider-consumer pairs; TV5 current ADMIN COD + Finance actor policy. | TV1 baseline is settled; concrete route+operation/pair/version/current-actor contracts and positive live proof absent. JWT P1 fix does not prove service-pair authorization. | TV1 pair utilities; TV2 secured Catalog; TV3 scoped consumers; TV4 restricted Inventory/fulfillment and TV5 Finance/COD adapters after pair-specific approval. |

**APPROVED requires explicit confirmation by every responsible signer of the same revision.**
An agent may author the TV5 proposal and document given requirements; it cannot sign for humans.
All rows currently forbid runtime release of their dependent behavior. Independent tests/docs
and existing fail-closed behavior may continue. Recheck GitHub responses before implementation.

## M01 / TV5-FIN-v0.1 — concrete candidate for decision

This candidate resolves ambiguity for discussion; **none of these new choices is production approval**.
Do not infer VND from Vietnamese UI, fixture labels or the old report.

1. **Currency candidate:** VND only, exact uppercase code explicitly persisted and returned by Product,
   Cart, Order and SellerOrder. Reject missing/mismatched/unsupported currency; no default or conversion.
   Unit price and all payable amounts must be integral VND (calculation scale0), reject fractional input
   with UNNECESSARY rather than silently rounding a Product price. TV2 must accept/revise its existing
   high-precision price draft; if fractional prices are required, revise this version before schema work.
2. **Precision candidate:** retain DECIMAL(19,2) storage compatibility with exact .00 values and explicit
   upper bound 99,999,999,999,999,999 VND per unit/line/Store subtotal/parent total. Validate quantity
   multiplication and aggregate bounds before write; no DB cast used as validation. Existing V1 columns
   do not impose integral-VND checks. Reviewed V2+ constraints/DTOs needed, never rewrite V1.
3. **Fee candidate:** ordinary Store rate 0.0500, House Store 0.0000 (rate DECIMAL(5,4)); House status
   from authoritative Store facts, captured at placement, never supplied by client or recalculated later.
   Compute exact lineTotal=unitPrice×quantity; group one SellerOrder per Store with consistent Seller/House
   facts. Parent total=sum(Store merchandise subtotal), no cross-Store fee pooling.
4. **Rounding candidate:** commission=HALF_UP(subtotal×rate, scale0) **once per SellerOrder**, then
   seller_net=subtotal−commission exactly. Do not round each line or pool parent fees.
   Example two ordinary Stores each subtotal10: fees1+1=2, not pooled fee1. One Store with two lines10:
   subtotal20, fee1, not sum of line fees2. House subtotal10: fee0/net10. These are proposed examples,
   not approved UAT data. Existing helper scale2 analogues are tested independently.
5. **Snapshots candidate:** sellerOrderId, Store/Seller refs, currency, unitPrice/qty/lineTotal, subtotal,
   rate, commission, seller_net, House classification + trusted fact/version reference and policyVersion.
   Avoid ambiguous `gross`: name merchandise subtotal explicitly. Shipping/discount/tax/refund allocation
   remains a business decision. Candidate initial scope excludes those amounts from commission base and
   rejects workflows that require an unapproved adjustment; no assumed zero charges in production.
6. **Cancellation/refund:** no settlement before delivery/payment; UNKNOWN inventory outcome is not proof
   of cancellation. Post-settlement return/refund requires signed reversal/adjustment model and audit;
   do not mutate original snapshots or silently net a refund into another Store. TV4/business owner must
   accept/revise exclusion and return/COD model before enablement.

### COD / settlement / isolation candidate and existing requirements

- Existing requirement: verified **current ADMIN** confirms COD only for **DELIVERED** SellerOrder,
  with payment state separate from delivery; audit actor/time, stable action ID and payload binding.
  TV1 OD-07 and TV5 earlier confirmation support this principle, not the missing endpoint/event schema.
- Candidate source: Order DB owns authoritative SellerOrder delivery and COD payment facts; TV4 owns
  transition/confirmation rules; TV3 owns local persistence/outbox coordination. Seller request, Inventory
  CONSUME/balance or caller event alone cannot prove payment/delivery. TV3/TV4 must sign producer,
  command/event version, dedupe, ordering, transaction boundary and paid_by/paid_at reference.
- Settle only **DELIVERED AND COD PAID**, persisted approved snapshots and policyVersion. Unique
  seller_order_id settlement plus atomic local eligibility check+insert/state transition; retries return
  the original settlement, changed payload conflicts. No in-memory `alreadySettled` precheck as race proof.
  Cross-DB facts require explicit consistency design, not a pretend distributed transaction.
- Finance read/list/detail/aggregate must filter by authoritative Store membership derived from verified
  current identity. Client storeId is a filter, never a grant. ADMIN capabilities must be explicit, and
  suspended Seller/closed Store access to existing orders remains D09/business-owner PENDING.
- Enable `policy-approved` only after this exact revision or its reviewed replacement has explicit
  TV5/business-owner, TV2, TV3, TV4 and TV1 acknowledgements for their responsibilities, compatible
  V2+ snapshots/bounds, COD source/security and per-SHA unit+PG/contract evidence. TV1's designated
  configuration/deployment owner applies the flag after recorded approval; a test fixture/env variable
  alone is not authorization. Keep current flag false and both external adapters fail-closed.

## Integrity findings at current source

| ID | Finding / impact | Owner / gate |
|---|---|---|
| FIN-01 | Cart and all Order monetary columns use NUMERIC(19,2); MoneyRules/FinanceRules accept scale0..4 and have no DB precision ceiling. Values 1.001 and 1.005 cast to 1.00 and 1.01 on real PG. Accepted helper scale does not guarantee durable exactness; higher-precision Catalog is incompatible without agreed validation/schema. | TV3 consumer/schema + TV2 representation + TV5 M01; BLOCKED before live enable. No unilateral Checkout fix. |
| FIN-02 | SellerOrder persists gross/platform_fee only. No currency, commission rate, House flag, policy version or seller_net snapshot; gross-fee can be derived but does not prove snapshot completeness. | TV3 shared entity/migration + TV5 Finance, TV2 House facts, TV4 COD; V2+ only after signatures. |
| FIN-03 | No settlement table/service/atomic unique settlement or COD PAID/DELIVERED fields in this Order scaffold. Unit settlement guard and checkout-key DB uniqueness are different properties. | TV5/TV4/TV3; production settlement, race protection and Store-scoped Finance queries NOT_RUN/BLOCKED. |
| FIN-04 | Two Store arithmetic/PG persistence with fixture identities does not authenticate either Store. Catalog/Inventory ports remain fail-closed; standalone InventoryOperationIntent is not durable journal/recovery. | TV1/TV2/TV3/TV4; positive live service-pair/Store isolation/E2E NOT_RUN. |
| FIN-05 | Fee stage changes totals at half-minor-unit boundaries (line vs SellerOrder vs parent). Existing Checkout groups by Store and rounds per SellerOrder; required final M01 signatures absent. | TV5 proposal; TV3/TV2/TV4 and business owner confirmation. |

## QA matrix and evidence boundaries

See [current run report](../reports/TV5_FINANCE_QA_2026-10-18.md) for commands, counts,
source hashes and raw evidence. Parent-head verification and supplementary-test verification
are separate executions and are never added into a release total.

| Requirement | Verification | Limit / status until implementation |
|---|---|---|
| JWT environment/YAML, missing-key fail-closed, HS256 contract | Existing JwtEnvironmentConfigurationTest + ContractJwtDecoderTest rerun at exact parent | Local verifier/config only; Auth-issued token/Gateway live NOT_RUN |
| Fee/House/net, rounding and exact subtotal | FinanceRules tests + new FinanceConsumerParityTest at scale0..4 | Existing helper compatibility; no currency/policy approval |
| Finance policy configuration | New FinancePolicyConfigurationTest reads real YAML and HK5_FINANCE_* environment | Default closed; approved=true exists only in synthetic test context |
| Two Store persistence / current DB precision | New FinanceSnapshotPostgresIT with actual Flyway/JPA/PG | Amount rows + scale characterization only; no Store authorization or snapshot completeness |
| Replay/unknown commit/scanner concurrency | Existing PG CheckoutReplayPostgresIT/CheckoutScannerPostgresIT rerun | Mock external ports; no live reconciliation worker |
| DELIVERED AND COD PAID, duplicate settle, Store isolation | Existing exhaustive pure guards rerun | Runtime COD/settlement/query NOT_RUN/BLOCKED (FIN-03/04) |
| Price freshness/currency, Catalog batch HTTP | Source audit against TV2/TV3 proposals | Live contract NOT_RUN/BLOCKED (C01/E01/S01/M01) |
| UAT one Customer/two Stores | Existing UAT plan; above arithmetic/PG fixtures are separate | Live E2E NOT_RUN/BLOCKED, external providers not ready |

## Review checkpoint

- [x] Read latest provider/consumer comments, source and sign-off ledger; identify missing signatures.
- [x] Draft concrete M01 candidate and six-row owner/module/evidence map without claiming approval.
- [ ] All responsible humans sign the same version; resolve C01/E01/C06 revisions.
- [ ] Implement dependent runtime within ownership only after relevant approvals and explicit phase scope.
- [ ] Production settlement/Store-query/COD/live two-Store acceptance and Issue #5 DONE.

Request replies in the form `contract + TV5-FIN-v0.1 (or replacement version) +
APPROVED/REVISE/PENDING + responsible scope + exact PR/comment + missing condition`.
Keep Issue #5 OPEN/PARTIAL and Issue #12 unclosed; no PR approval/merge in this task.

## Follow-up after PR41 integration (no new signature)

Owner merged41 into28 at55bf5ff. [New regression/checkpoint](../reports/TV5_FINANCE_FOLLOWUP_2026-10-18.md)
reruns current source and records FIN-06: aggregate precision rejection occurs at DB write after the
point where Checkout could request Inventory reserve. TV3+TV5 must agree pre-reserve monetary bounds
and any reviewed V2+ schema changes; this test-only handoff changes no runtime or proposed M01 terms.
All contract statuses above remain PENDING/REVISE. Merge41 is acceptance of a QA patch, not a signature
on VND/scale0/rate/rounding, service pairs or production settlement.

## Latest provider/consumer response and Checkout RED evidence

[TV2 candidate v0.2](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246199255) is now
technically compatible with TV5-FIN-v0.1 integral VND/17-digit representation; this is not a business
signature. [TV3 response](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246255122)
accepts canonical UUID Product/Store/Seller strings (separate from JWT.sub), plain decimal money strings,
ordered complete batch/allReady and candidate200/400/401/403/503/top-level error envelope **for further
sign-off**, still C01/E01REVISE until TV1/current authority/remaining signatures. No HTTP adapter adopted.

PR37 source63668b8 repairs internal FOUND/ABSENT/UNAVAILABLE and rejected-batch snapshot behavior;
TV5 targeted50unitPASS verifies that source only. New Checkout diagnostic atparent28dd0cf gives
1PASS/2FAIL (FIN-06 pre-reserve bounds, FIN-01 first-response/replay precision). Full details, updated
seven-row contract matrix and owner fix acceptance in [RED report](../reports/TV5_FIN06_CHECKOUT_RED_2026-10-18.md).
TV3 explicitly confirms fixNOT_IMPLEMENTED and conditional owner proposal, not permission to edit Checkout.
All overall PENDING/REVISE states above remain; no deploy flag/production behavior change authorized here.

TV2 subsequently corrected the stale prototype paragraph and handoff at9e1794a (five Markdown/CSV files
only); the Catalog Java source remains63668b8. TV5 verified that delta and treats the documentation finding
as corrected, with no new runtime execution/contract approval inferred. Provider-reported367PASS is
separate from TV5's targeted50unit run and the Checkout1PASS/2FAIL diagnostic.

## Human choice packet / latest owner proposals

[TV5-M01-DECISION-v1](TV5_M01_HUMAN_DECISION_PACKET.md) makes MD01–MD10 explicit: currency alternatives,
unit/line/Store/parent bounds, fee rounding examples, snapshots/adjustments/refund/COD, freshness risk,
enable authority and reporting groups. It is a companion toTV5-FIN-v0.1, **not a new approval**.
TV2 provider42cc4e7 recommends last-verified snapshot only after human/TV1/TV5 race acceptance; no quote/lease.
TV3ee15851 adds an unwired storage utility,6official JUnit tests verified byTV5; original diagnostic remains
1PASS/2FAIL at merged TV5 sourcec4d517e. TV4 docs7a8d18e/ec2ae5a repaired source semantics/21+21ID history,
but retry budget/state/event/current authorization still need same-version sign-off. [Current QA/RTM evidence](../reports/TV5_MONEY_DECISION_CHECKPOINT_2026-10-18.md).

## Current M01 owner ledger / partial implementation gates — 18/10/2026

**M01 overall PENDING; no new business signature.** Canonical choices/approval ledger now live in
[the existing decision packet, §§8–11](TV5_M01_HUMAN_DECISION_PACKET.md), checkpoint `M01-OWNER-LEDGER-20261018-r2`.
Keep `TV5-FIN-v0.1 + TV5-M01-DECISION-v1@commit` in replies; Catalog v0.2 is unchanged. Existing reports are source-specific history,
not competing contract versions. The packet records MD05–MD10 choices, benefits/risks/consumer impacts and scoped required signers.

- TV2 [Product technical compatibility](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246199255) for VND/integral positive17digits/exact strings/BigDecimal is recorded; no repeat confirmation requested for the same values.
- TV3 [C01/E01 consumer design/mapping APPROVED own-scope](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6248646211) and [technical preflight/snapshot needs](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6248595524) are recorded. Overall C01/E01 REVISE, M01 PENDING; no redundant DTO-shape request.
- TV4 COD/delivery/Inventory boundary is a provider/lifecycle proposal, not same-version business approval. TV1 Auth baseline remains settled; concrete pair, M01 final business signer and enable-operator designation are missing.
- Human userTV5 confirms proposal/synthesis only; **business approver: chờ TV1/chủ dự án xác nhận**. Agent cannot sign for TV5/business or turn technical compatibility into APPROVED MD01/02.

**Product price slice:** if human approvers explicitly sign MD01/MD02/unit-cap MD03 and applicable TV2/TV1 schema/S01/auth gates,
TV2 may implement that reviewed price/currency slice without waiting for unrelated reporting-group MD10. No such slice is approved now.
This does not authorize Catalog exposure/House assignment, enable Finance, or remove Checkout/COD prerequisites. TV3 similarly implements
only the money preflight/snapshot scope for which decisions, trust sources and ownership authorization are complete. See packet§10.

PR43/44/45 are integrated into PR28 **5a3788b34ec4a2a91611d88020442a27b9dc7948**. Latest QA report/raw61assets are
[already merged evidence](../reports/TV5_FINANCE_ACCEPTANCE_2026-10-18.md); Order production/diagnostic unchanged from the stored
de1f5c6 run (16PG PASS; explicit diagnostic1PASS/2FAIL). This update is docs-only: runtime NOT_RUN, no newly claimed PASS, Finance flagfalse.
FIN-01/06 remain TV3 fixes: exact unit/line/Store/fee/net/parent validation beforeRESERVE, immutable reviewed V2+ snapshots,
unchanged original tests GREEN +positive controlPASS at actual fixSHA. Production settlement/Store queries/currentCOD/liveE2E remain BLOCKED.
