# TV5 Product lifecycle → Catalog PostgreSQL composition

Refs #5 #12 #90 #97. QA-only sources outside owner production/test trees. Requires reviewed
Product lifecycle #89 and batch source #90. Original base was #90/head273a44d with maina705052;
the TV5 branch first synchronized with main7d68e11 after #90/head0f9c0c8 merged,
then mainf888725 after #91 integrated Search. Earlier receipts retain their own exact SHAs.
It adds no controller, migration, production bean, shared Gradle edit, authority or feature flag.

## Run

From a checkout containing both source capabilities, Java21 and Docker Engine ready:

```powershell
$env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC -Dapi.version=1.44'
$qa=(Resolve-Path tests/integration).Path
./backend/gradlew.bat -p backend --init-script "$qa/product-catalog-composition.init.gradle" "-Ptv5CompositionSources=$qa" :product-service:tv5ProductCatalogComposition --rerun-tasks --no-daemon --console=plain
```

Five real PostgreSQL cases join **actual proxied ProductApplicationService writes** to actual
ProductCatalogSnapshotSource/CatalogBatchValidator reads. Existing #89/#90 tests cover those
components separately; #90's fixture writes Product states directly. This suite crosses that seam
without SQL state mutation: committed lifecycle visibility, deactivation/edit/reapproval price
change and whole-batch withholding, two-Store denied write rollback, unavailable House facts after
successful Product persistence, and an outer rollback after successful local approval/read.
The captured older Catalog value object staying unchanged is not a durable Order-money snapshot.

The JSON fixture supplies two distinct Store/Seller/actor identities and exact Unicode SKUs with
approved Product-only VND unit prices. Product UUIDs come from the real application; they never
stand in for SKUs. All identity, active Store/Seller and explicit false House values are **synthetic
test facts**, never authenticated accounts, registered House authority or business approval.
Unknown House is deliberately tested as unavailable, not defaulted to false. No commission/net,
quantity/aggregate cap, refund, delivery, COD paid or settlement policy is selected here.

## Seller → Product → Cart → Checkout live E2E preparation

Reuse this two-Store input and the separate Finance durability fixture in #99 when approved runtime
adapters exist. The executable harness above currently covers only the Product→Catalog PG boundary.
Do not label its PASS as HTTP, Cart, Order placement, Inventory or whole-flow E2E PASS.

| Stage | Prepared input / assertion | Remaining gate / owner |
|---|---|---|
| Seller login | Two separate current accounts/SELLER grants, Store A/B association | TV1 current principal and versioned Seller role-sync; TV2 trusted Store binding |
| Create/moderate Product | Fixture Category, exact SKU/VND strings; real create/submit/approve persisted here | #97 protected routes/authority; TV1 scoped 404 decision is recorded, TV2 confirmation/implementation and unchanged diagnostic acceptance remain pending |
| Catalog | DRAFT/PENDING/REJECTED/SUSPENDED withheld; ACTIVE visible; price and Store mapping rechecked | #90 is merged; C01/E01 wire, House/current-facts provider and MD07 freshness remain gated |
| Customer Cart | One unit of each fixture Product; compare authoritative IDs/prices, never client SKU/House authority | TV3 approved Cart adapter/current Customer; quantity1 is fixture data, not aggregate policy approval |
| Checkout | One parent/two distinct SellerOrders, preserve per-line/Store price/SKU/currency; no partial reserve | TV3/TV4 C06 durable owner tuple/operationId/reconciliation and applicable signed money snapshots |
| Errors/recovery | Cross-Store mutation denied; changed price or incomplete facts withhold every snapshot; local rollback | Remote UNKNOWN must be reconciled, never blind RELEASE/new key; compensation/Order policies need owners |

Live HTTP E2E remains **BLOCKED/NOT_RUN** until the exact paths/DTO/errors/credentials/current actors,
authority, independent downstream authorization, House/freshness and reservation/recovery contracts
are approved and deployed in an authorized test environment. A deny-default403 test is not positive
HTTP authorization. An internal expected-item record is not a signed Cart/Checkout HTTP DTO.
No TTL/quote/lease, replacement endpoint or test-only production adapter is invented by this harness.

## Product -> Catalog -> existing Checkout preflight (12 additional PostgreSQL cases)

`ProductCatalogCheckoutPreflightPostgresTest` joins actual proxied Product lifecycle/JPA writes,
the actual ProductCatalogSnapshotSource/CatalogBatchValidator and TV3's existing
`CatalogCheckoutBatchPreflight.requireFullyReady`. The record conversion exists **only in QA**:
it copies actual fields/codes, preserves order and null snapshots, and chooses no HTTP DTO,
endpoint, status mapping or approved shared wire. It is not a production adapter.

| Cases | Added seam coverage |
|---|---|
| A (1) | Commit deactivate/edit/reapprove at the approved 1b VND unit cap; reject stale expectation, accept fresh exact price |
| B (2) | Committed deactivate/suspend must fail consumer preflight; no individually READY companion snapshot escapes |
| C/F (5) | Confirmed absent Product versus missing Store/unknown House/stale facts/one Seller claimed by two Stores; incomplete facts never reach the consumer as verified results |
| D (3) | Positive reverse-request-order two-Store exact Unicode SKU/Store/Seller control and forged expected Store/Seller negatives |
| E (1) | A preflight exception propagates through an outer Product transaction, rolling back a prior lifecycle write |

Both Product and a separate Order database use fresh PostgreSQL16.6 Testcontainers. Product
uses its normal Spring/JPA/Flyway context; the second database applies **unchanged Order V1/V2
migrations directly through Flyway**. The init script adds only Order compiled classes, not
its Spring application/resources, avoiding colliding migrations/configuration in Product.
Tests compare all Product/Category rows and Order/child/item/attempt/outbox/journal rows before
and after validation. This proves observed local invariance in this explicit composition.
Order tables start empty and no CheckoutService/Order writer/Inventory transport is wired:
zero Order/outbox/journal rows do **not** prove production Checkout ordering, remote reservation
absence, compensation or live E2E. Inventory storage is not inspected. Current principal,
Store/House facts remain synthetic fixtures; selectors do not grant authorization.

Original five cases remain unchanged. The first expanded run passed immediately (17 total,
12 new); label it **PREVENTIVE/REGRESSION**, not an invented TDD RED->GREEN. Unit/PG/runtime
receipts are separately bound to source manifests/SHAs. Full M01/Finance gates stay unchanged.

TV1 has [decided the narrow owner-resource visibility rule](https://github.com/duyxl56785/Project_HK5/pull/97#issuecomment-6430723502): after independent current Seller/Store-scope verification, owner GET/edit/submit/deactivate must return the same generic404 for foreign and missing Products. This decision does not register a protected route or globally remap403. Do not request the same decision again; TV2 must confirm and implement its mapping. [Draft diagnostic #104](https://github.com/duyxl56785/Project_HK5/pull/104) retains the original four parity failures and four controls until the unchanged acceptance cases are GREEN on the actual owner fix.
