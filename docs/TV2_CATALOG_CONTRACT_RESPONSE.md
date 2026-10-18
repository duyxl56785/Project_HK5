# TV2 response — Catalog contract candidate v0.2

2026-10-18, Asia/Saigon. Refs #2, PR #37, PR #28, PR #40.

**TECHNICAL PROPOSAL / WAITING_OWNER_CONFIRMATION.** This responds to TV3's [C01/E01/M01 revision](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246111226) and TV5's [TV5-FIN-v0.1 candidate](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6245987112). It is TV2's technical position, not a human business-policy signature, shared-contract approval or authorization to deploy. No common contract, production source, schema or endpoint is changed.

## Decision response

| Contract | TV2 response | Remaining owners / gate |
|---|---|---|
| S01 | **PENDING** overall; retain Store-on-Seller-DB proposal, local Seller FK + UNIQUE(seller_id), canonical unique slug. Product holds logical Store ID, never joins another DB. | TV1 confirms boundary/current authority; consumers confirm Store isolation; then TV2 implements reviewed V2+ persistence. |
| C01 | **REVISE**, proposed `catalog.checkout.v1`, document revision v0.2 below. Support 1–100 distinct Product IDs, expected values as comparisons, matching ordered results, all-or-nothing Checkout. | TV3 confirms exact wire including decimal strings and canonical UUIDs; TV1 route/delegation; TV5 M01; TV4 SKU mapping. |
| E01 | **REVISE**, domain result versus request/auth/dependency errors distinguished below. Explicit authoritative absence is required; omitted data never proves nonexistence. | TV1 confirms envelope/status/privacy and caller policy; TV3 consumer mapping/no partial reserve. |
| M01 / TV5-FIN-v0.1 | **PENDING overall; technically compatible from Product side** with explicit VND and integral positive unit prices up to 99,999,999,999,999,999. Product has no demonstrated business requirement for fractional prices; prototype precision was an exactness experiment, not that requirement. | TV5 + human business decision maker confirm currency/fees/rounding/adjustments; TV3/TV4 confirm consumer/COD bounds. No production restriction or Finance flag changes until written agreement. |
| SKU / Inventory | Retain exact-case immutable globally unique Product-owned SKU target; `productId != inventorySku`; Inventory keeps SKU-only key. | Runtime unique constraint/provider absent; TV4/TV3 map the authoritative SKU, never guess. No Inventory operation/auth/lifecycle change. |
| Security | **PENDING concrete pair**, preserve OD-09 baseline. | TV1 specifies Order→Catalog pair credential, POST-only path allowlist and independently verified delegated/current actor. No broad Inventory credential, client ownership header or public Gateway internal route. |

## C01 concrete wire candidate

Proposed method/path: `POST /internal/catalog/checkout/validate`. **Not currently callable as a Catalog provider.** Existing fallback still returns 403 `PRODUCT_ROUTE_AUTH_NOT_CONFIGURED`, including for mocked ADMIN. Proposed future statuses below do not replace that boundary.

Request envelope: `contractVersion` equals `catalog.checkout.v1`; `items` has 1–100 entries. Each entry has exactly `productId`, `expectedStoreId`, `expectedSellerId`, `expectedUnitPrice`, `expectedCurrency`. Unknown fields, missing/null fields and repeated Product IDs are invalid. Catalog does not combine quantities or accept caller-declared SKU/status/House facts.

Candidate ID representation: JSON strings in lowercase UUID `8-4-4-4-12` form for Product/Store/Seller IDs issued by those providers. Strictly require round-trip `UUID.fromString(value).toString().equals(value)` at the future boundary; reject whitespace, uppercase aliases, shorthand UUIDs and noncanonical inputs. TV3 may retain Java String fields but must preserve the canonical provider string and validate before comparison; it must not generate replacement IDs. This does not normalize or redefine Account/JWT.sub/ownerReference, which retains its separate Auth exact-string semantics. Existing internal UUID records alone do not implement HTTP validation.

Candidate monetary representation: `expectedUnitPrice` and returned `unitPrice` are **plain decimal JSON strings**, not IEEE-754/JavaScript numbers. Under the still-pending VND candidate, use positive digits without exponent, sign, leading zeros or decimal point, at most 17 digits. Currency is explicitly `VND`; never default it when missing. This avoids losing a 17-digit value in a JSON number consumer. TV3 must explicitly confirm this wire choice; it differs from an implicitly serialized BigDecimal. If M01 instead permits fractional units, revise this candidate before release rather than silently rounding.

Example request (synthetic identities, not live records):

```json
{
  "contractVersion": "catalog.checkout.v1",
  "items": [{
    "productId": "50000000-0000-4000-8000-000000000001",
    "expectedStoreId": "40000000-0000-4000-8000-000000000001",
    "expectedSellerId": "30000000-0000-4000-8000-000000000001",
    "expectedUnitPrice": "10000",
    "expectedCurrency": "VND"
  }]
}
```

Complete evaluation response: `contractVersion`, explicit boolean `allReady`, and `items` with exactly one result for every requested ID in request order. Each item contains `productId`, `code`, `snapshot`. Only READY has a non-null snapshot with `productId`, `inventorySku`, `storeId`, `sellerId`, `unitPrice`, `currency`, `available`, `sellerApproved`, `storeOpen`, `houseStore`. READY flags are true for sellability; House may be false. Do not return ownerReference, moderation reason or rejected current price/identity details. Non-READY snapshot is null.

`allReady` is a required HTTP field in this candidate, computed server-side from all item codes; the current Java `Result.valid()` method is not proof that this field is serialized. TV3 must also check expected IDs/count/uniqueness and every item. **Any non-READY, incomplete/duplicate response, unsupported version, untrusted facts, error or timeout aborts the entire Checkout before any Inventory RESERVE or Order creation; never reserve a READY subset.**

## E01 statuses, privacy and source completeness

This is a candidate for TV1/TV3 review, not an implemented common error contract:

| Future HTTP | Candidate meaning |
|---|---|
| 200 | Fully evaluated authorized batch, possibly `allReady=false`. Per-item READY / PRODUCT_NOT_FOUND / PRODUCT_NOT_SELLABLE / OWNERSHIP_MISMATCH / PRICE_CHANGED / CURRENCY_MISMATCH. No extra per-item HTTP 404/409. |
| 400 | INVALID_REQUEST for malformed JSON/IDs/price/version/unknown fields, missing fields or 0/>100/duplicate items, after applicable authentication checks. |
| 401 | Missing/invalid authenticated service or delegated user under TV1's pair/JWT error profile; no hand-invented JWT claims or new credentials. |
| 403 | Valid actor/service lacks operation scope or actual authorization/ownership. This differs from an authorized comparison of stale expected Store/Seller IDs, which is an item OWNERSHIP_MISMATCH. |
| 503 | DEPENDENCY_UNAVAILABLE for incomplete/stale/unverifiable Product/Store/Seller facts or source timeout; Auth verification/current-authorization failures retain TV1's corresponding dependency codes. No request/cart/fixture fallback. |

For Catalog-owned 400/503 errors propose `{ "code": "INVALID_REQUEST", "message": "...", "errors": [] }`, consistent in shape with Auth v1.0's top-level `code`/`message` convention; validation entries contain field/message only, never rejected values. **This rejects a silent switch to `error.code` nesting** and requires TV1/TV3 agreement. Existing Category error-map contract is unchanged. Auth challenges/protocol responses follow TV1, not this DTO proposal. Unknown/unauthorized resource concealment and exposing result codes to delegated customers require TV1 privacy confirmation; no public catalog enumeration route is enabled.

Provider rule: an authoritative Product lookup must explicitly establish a result for each requested ID: found, confirmed absent, or unavailable. Only confirmed absent permits PRODUCT_NOT_FOUND. Timeouts, missing result entries, stale/unverified facts, downstream partial responses and malformed data are DEPENDENCY_UNAVAILABLE for the **whole batch**, with no successful snapshot response. An unavailable Store/Seller dependency must never become Product absence or ordinary not-sellable.

Known gap in the current internal prototype: `CatalogSnapshotSource.load(Set<UUID>)` returns a list of found facts, so omission currently becomes PRODUCT_NOT_FOUND without a distinct absence proof. **This prototype must not be wired as an authoritative adapter unchanged.** Before wiring, revise the internal source result to distinguish confirmed absence from unavailable/incomplete facts and add regression cases for omitted response, explicit absent result, timeout, stale status and mixed batches. That internal correction is independent of the final HTTP envelope; no production readiness is claimed here.

## Price freshness, House and M01 boundary

TV2 supports trusted Product as the source for price/currency/SKU; trusted Store authority owns Store→Seller/current status/House classification. House must be a persisted privileged classification from the agreed provider, not Store slug/name, owner role, JWT/body/header or a missing-value default. Existing Store domain has no authoritative House persistence/assignment API; that authority and permitted assignment operation are still S01/Security decisions.

Checkout validates the current facts against the Cart expectation and rejects price/currency/status changes. A successful response describes the checked facts; it is not a price lock, Inventory reservation or guarantee that eligibility remains unchanged at Order commit. TV2/TV3/TV1 must agree fact versions/freshness and the revalidation/commit race policy before implementation. No invented cache TTL, cross-database atomicity or guaranteed expiry window is signed here.

For TV5-FIN-v0.1 compatibility: validate positive integral Product unit price with exact `setScale(0, UNNECESSARY)` and the 17-digit bound before persistence, then retain exact `.00` values if reviewed storage uses NUMERIC(19,2). TV3 must bound unit×quantity, line/Store/parent totals without silent overflow/rounding, add explicit currency and approved immutable snapshots with V2+ migrations, and never edit applied V1. The present Product prototype remains unrestricted NUMERIC/exact BigDecimal; it is not the proposed VND schema. Product does not choose Finance commission/rounding stage, shipping/tax/discount/refunds or enable authority on behalf of their owners.

## What was verified in this response task

- PR #40 exact HEAD `e91312c8d897b8faf12fed151022508025719de1`: independently recomputed **49/49 public SHA256 checks**, parsed 32 public XML suites plus 3 explicitly derived summaries (41 withheld testcases), reconciled all published counts and checked 18 relative Markdown links; documentation diff check PASS. No high-confidence generated-password/private-key/GitHub-token marker found in the public logs/XML scan. The three withheld originals are not public, so their original hashes cannot be independently recomputed. Artifact checks do not rerun TV5's tests or certify all possible secret formats.
- PR #28 delta `bd5ff09` → `55bf5ff086633eddd37c245abc809f5e0ce57be8`: three Finance test classes plus docs/evidence; **no production Java/config/build/Flyway change**. Runtime regression results are reported separately in the delivery comment; historical PR #40 runs are not added to those totals.
- Current TV2 source remains `1f2ebf5`, delivery before this docs response `9c28338`; no runtime change or new Product/Seller test run is required by this documentation-only response. Previous 340 PASS is historical here, not a new run.

Issue #2 remains OPEN/PARTIAL. Review comments and technical proposals do not complete Store/Product DB/API, live JWT/service-pair integration, durable seed or Checkout E2E. Read the [handoff](TV2_CATALOG_HANDOFF.md), latest owner replies and Git status before implementation. Do not treat this response as approval/merge of any PR or a signature for another owner.
