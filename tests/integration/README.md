# TV5 Product lifecycle → Catalog PostgreSQL composition

Refs #5 #12 #90 #97. QA-only sources outside owner production/test trees. Requires reviewed
Product lifecycle #89 and batch source #90; original base is #90/head273a44d with maina705052.
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
| Create/moderate Product | Fixture Category, exact SKU/VND strings; real create/submit/approve persisted here | #97 protected routes/authority, TV1+TV2 403-vs404 visibility decision |
| Catalog | DRAFT/PENDING/REJECTED/SUSPENDED withheld; ACTIVE visible; price and Store mapping rechecked | #90 merge, C01/E01 wire, House/current-facts provider, MD07 freshness |
| Customer Cart | One unit of each fixture Product; compare authoritative IDs/prices, never client SKU/House authority | TV3 approved Cart adapter/current Customer; quantity1 is fixture data, not aggregate policy approval |
| Checkout | One parent/two distinct SellerOrders, preserve per-line/Store price/SKU/currency; no partial reserve | TV3/TV4 C06 durable owner tuple/operationId/reconciliation and applicable signed money snapshots |
| Errors/recovery | Cross-Store mutation denied; changed price or incomplete facts withhold every snapshot; local rollback | Remote UNKNOWN must be reconciled, never blind RELEASE/new key; compensation/Order policies need owners |

Live HTTP E2E remains **BLOCKED/NOT_RUN** until the exact paths/DTO/errors/credentials/current actors,
authority, independent downstream authorization, House/freshness and reservation/recovery contracts
are approved and deployed in an authorized test environment. A deny-default403 test is not positive
HTTP authorization. An internal expected-item record is not a signed Cart/Checkout HTTP DTO.
No TTL/quote/lease, replacement endpoint or test-only production adapter is invented by this harness.
