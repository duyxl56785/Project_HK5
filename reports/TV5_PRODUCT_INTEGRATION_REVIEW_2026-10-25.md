# TV5 Product/Seller integration review — 25/10/2026

Refs #5 #12 #83 #88 #89 #90 #91 #94 #95. **Technical review completed; formal human approval remains separate.** Source snapshot and later publication receipts have distinct timestamps. This report supersedes only the corresponding earlier waiting/source labels, preserving previous reports as history. No agent APPROVE/Merge/Ready, owner production edit, deployment or flag change.

## Current reviewed scope

| PR | Exact HEAD | CI | Independent TV5 runtime | Disposition / next action |
|---|---|---|---|---|
|88|199211beb55656f88c7b33850289e887d1390fbe|SUCCESS (historical head)|Not rerun this session; prior TV5 437 unit +61PG historical|MERGED to main ed85f6290de7f611c82e5c0ad973f98180f04df4; Level B gate already satisfied.|
|89|17f98f4fc96b19f784fe39eeb5fd0c90e49915f6|SUCCESS,40182643630|440 unit +98PG PASS, exit0|READY_FOR_HUMAN_APPROVAL for entire internal lifecycle/persistence PR. TV1 explicitly re-approved final17f98f4; human TV5 request remains.|
|94|96d59ca320845495dd2ce6acf42d0b10ee0286a6|SUCCESS,40122424378|246 unit +138PG PASS, exit0|READY_FOR_HUMAN_APPROVAL for entire Seller-local facts PR. TV1 APPROVED exact head; human TV5 request remains.|
|90|6a290fffc35e6795f9e25f4a79b2b6fe74aa757f|SUCCESS,40197447319|447 unit +107PG PASS, exit0|READY_FOR_HUMAN_APPROVAL for internal source only; TV1 final-head re-review and TV3 same-tree consumer scope APPROVED. No Catalog HTTP permission.|
|91|4915c3768a310b519e218674e405b132103cd024|SUCCESS,40197452631|448 unit +96PG PASS, exit0|READY_FOR_HUMAN_APPROVAL for private candidate search only; TV1 final-head APPROVED. Not public eligible pagination.|
|83|c831edb4d7c610b76e434222d51b115cfb863d81|SUCCESS,40197534885|437 unit +70PG PASS, exit0|Old TV5 integration finding FIXED_VERIFIED: explicit VND fixture + exact migrations[1,2], current2/no-op. Own thread resolution is recorded separately after publication.|
|95|53c2a1baf8988ef8878524e9aa742d4a78a690cf|No checks found; not CI PASS|NOT_RUN by TV5 this session|OPEN/Draft; TV1 source-only APPROVED and own previous thread resolved. Author198 tests remain author evidence, not TV5 or protected HTTP/E2E acceptance.|

All runtime rows are separate invocations; no sum across PRs, authors, CI or snapshots. [Exact source/base/reviews/CI and synthetic recipe](evidence/tv5-product-integration-20261025/review-context.json). No actionable P0/P1/P2 defect found in the current bounded #89/#94/#90/#91 diffs. Missing future adapters are explicit non-goals, not invented bugs in these internal PRs. Human approval applies to the whole PR; all changed paths were reviewed before proposing it. PRs remain Draft except83; owner handles normal readiness/merge gates.

## What was verified

- **89:** actual ProductApplicationService → lifecycle → fresh JPA adapter. DRAFT/edit/submit/moderation/deactivate/read state rules; category/price input validation; exact immutable SKU/Store identity; authority missing/ambiguous stops resource access; ownership/current APPROVED+OPEN and no self-moderation under trusted-policy fixtures. Fresh state+expectedVersion is bound before adapter refresh/@Version, including cached/deleted/stale/no-op/concurrent cases and REQUIRED companion-write rollback. Create duplicates fail uniqueness; command replay with stale version is rejected, not a promised public idempotency-key API. No delete API or client authority is invented. No new migration/constraints/config. Actual SELLER/current Auth verification remains the unconfigured policy adapter's obligation.
- **94:** private single parameterized scalar Store→Seller join, no cross-service SQL/N+1/entity-cache dependency. Store/Seller UUIDs differ from exact private ownerReference (not proven Account ID/current caller). READ_COMMITTED explicit transaction guard, one-statement committed status/version consistency, local omission only after successful query, invalid batches do not query, timeout becomes AUTHORIZATION_UNAVAILABLE, concurrent update/delete/rollback/FK tests. No House classification, caller auth or HTTP response contract added; never expose ownerReference as wire DTO.
- **90:** Product UUID→exact inventorySku, 1–100 IDs, explicit VND/price/category/state projection; successful Product read alone proves ABSENT. Missing/conflicting/stale/unknown House/current Store/Seller facts → UNAVAILABLE, never a usable READY subset. One local Product observation and separate Store observation are not a distributed snapshot, lease or commit-time guarantee. Source is deliberately unregistered.
- **91:** bounded filters/page/sort, literal LIKE escaping, exact VND bounds, UUID tie-break and local repeatable-read count/rows consistency under real concurrent changes. candidateTotal is Product-only, not Store-eligible public total; no public endpoint or invented post-page eligibility proof.
- **83:** original constructor regression is repaired by explicit currency, not a default-VND compatibility constructor. Candidate SQL remains test-only; test checks actual V1/V2 history and no candidate migration. Original assertions/boundary regressions preserved. Full current build passes; original synthetic RED remains unchanged in prior report.

[89 evidence](evidence/tv5-product-integration-20261025/89.json), [94](evidence/tv5-product-integration-20261025/94.json), [90](evidence/tv5-product-integration-20261025/90.json), [91](evidence/tv5-product-integration-20261025/91.json), [83](evidence/tv5-product-integration-20261025/83.json).

## Combined source — independent actual run

Safe private `git merge-tree --write-tree` recipe: final89@17f98f4 →90@6a290ff →91@4915c37, no manual changes, Repository retains both batch projection and search fragment. Exact tree **29d2904c9a6b4bc5676687da938885c3852fc5cd**, equal to TV2's reported integration tree; TV5 private synthetic commit **b53e51b3c98561eb1f0d55de09417bb3e4b68374**, never pushed as a PR/release commit.

**Fresh TV5 execution: 640 PASS /0 FAIL/errors/skip, exit0 =461 unit +179 tagged PG in one invocation.** PG179 =36 lifecycle +46 batch +35 search +23 persistence +1 migration +14 Category +24 separately labelled SQL candidate. This independently corroborates the bounded combined-tree claim; it is not a run of one PR head, GitHub CI, live services or a release total. [Commands/time/cases/raw hashes](evidence/tv5-product-integration-20261025/combined.json).

All builds: Java21.0.7/Gradle8.10/Docker29.8.1/PostgreSQL16.6-alpine Testcontainers, process-only `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`. Command from each source checkout: `backend/gradlew.bat -p backend :product-service:build --rerun-tasks --no-daemon --console=plain` (94 uses`:seller-service:build`). Fresh XML copied before any later invocation; raw logs/XML private, byte-preserved and SHA256 verified, public JSON explicitly derived. No existing Docker volumes/data removed.

## TV3 handoff — available now vs blocked runtime

| Contract/capability | Actual state | Next responsible owner |
|---|---|---|
|Private Product persistence/lifecycle/batch/search code|TECHNICALLY_VERIFIED at exact heads/combined tree;88 MERGED. Internal PR89/90/91/94 ready for human review, no remaining introduced defect found.|Human TV5 formal review; TV2 owner readiness/normal integration and final actual merge-head tests.|
|Product price slice MD01/02/unit MD03|APPROVED business #84, TV5 Level A +TV2 mapping ACK;88 Level B/merge complete. Explicit VND, integral1..1e9, exact string/no rounding.|No new business signature required for this unchanged slice. Quantity1..1000 and aggregate1e12 are NOT signed by it.|
|C01/E01 catalog.checkout.v1 v0.2 wire|REVISE/PENDING shared wire, privacy/status, current authority and freshness approval; internal semantic tests verified, not a callable API.|TV1+TV2+TV3 exact method/path/credential/allowlist/current actor, complete DTO/status/error/privacy/revalidation contract; TV4 exact SKU/reservation compatibility.|
|Seller current facts94 →Product90|TECHNICALLY_VERIFIED local Seller source; remote adapter NOT_IMPLEMENTED/NOT_VERIFIED.94 has no authoritative House field,90 requires explicit verified House.|TV1+TV2 authenticated current Account/Seller/Store source/pair; TV2/business House authority. Do not default missing House to false.|
|Public search91|PRIVATE candidates only; eligibility-before-count/page/freshness design PENDING.|TV2+TV1/TV3 current eligibility/count completeness; not filtering a candidate page and reporting its total as public.|
|Last-verified snapshot/quote|PROPOSED last-verified double revalidation with accepted residual race; no cross-source lease implemented/approved.|TV1+TV2+TV3+human/business and TV5 Finance scope agree MD07 risk/recovery; no decorative quote/TTL guarantee.|
|Inventory ownership/recovery|Exact Product SKU persistence exists, but authenticated provider/pair, per-SellerOrder reservation ownership, C06/source/version/reconciliation remain separate.|TV3+TV4+TV1; no ProductId-as-SKU, blind RELEASE or new ID after UNKNOWN.|

**TV3 may use these as reviewed internal implementation building blocks after normal merges, but cannot yet call a positive live Catalog HTTP provider.** Do not add a second prototype merely to avoid the missing signed runtime boundary. The only direct TV5 PR gate remaining here is the human formal review, not another technical/business review of already accepted Product unit price.

## Finance/COD answers, without assuming approval

1. **Whole M01 is PARTIAL/PENDING**, Finance production BLOCKED; the #84 human receipt signs MD01/MD02/unit-price MD03 only. MD04–MD10, House/rates/rounding/COD/refund/operator are not implicitly signed. [Business receipt](https://github.com/duyxl56785/Project_HK5/pull/84#issuecomment-6364386964), packet463338e/digest a02370d995ea2950b39d737c6a41c47f09da448a600b335905c080a8798b008a; [LevelA](https://github.com/duyxl56785/Project_HK5/pull/84#pullrequestreview-5637148973), [TV2 ACK](https://github.com/duyxl56785/Project_HK5/pull/84#issuecomment-6381447408), [LevelB88](https://github.com/duyxl56785/Project_HK5/pull/88#pullrequestreview-5639341936). Signed packet file is not edited.
2. **COD PAID source:** proposed committed Order-owned per-SellerOrder delivery/payment record+atomic outbox, not Inventory/notification/request/parent-global paid. Current SellerOrder has no delivery/payment/action/settlement fields; producer/version/transaction still awaits TV3+TV4+TV1/human Finance/business confirmation. [TV3 concrete handoff](https://github.com/duyxl56785/Project_HK5/issues/12#issuecomment-6282489759).
3. **COD actor:** existing requirement verified current ADMIN after DELIVERED; current role/account/service-pair procedure, action/event version/audit/idempotency/refund semantics remain a signed implementation gate. JWT role or body actor alone is insufficient. TV1 Auth authority and TV4 command/TV3 storage own their decisions.
4. **Settlement:** DELIVERED AND COD PAID is a documented Finance requirement/pure guard, not complete business policy or runtime acceptance. Unique sellerOrder settlement +atomic eligibility/insert+replay/store-scoped query, immutable policy snapshots/refund/reversal remain NOT_IMPLEMENTED/BLOCKED. Payment/journal dedupe is not settlement uniqueness. TV5 may implement only after signed relevant policy and shared source/security design.
5. **FIN-01/FIN-06 technical existing-storage fixes are FIXED_VERIFIED, not pending TV3 fixes.** On current main ed85f62 TV5 freshly ran109Orderunit +25OrderPG +3unchanged explicit FIN diagnostics PASS, exit0. Source is identical to original RED diagnostic8cc5ce4; positive control preserved. Normal build/CI does not substitute for this explicit task. [Fresh commands/cases/hashes](evidence/tv5-product-integration-20261025/finance.json). No new currency/Checkout business cap/House/Finance approval from these fixtures.
6. **Two-Store live E2E remains BLOCKED:** current Auth/Store/House/pair, signed Catalog C01/E01/MD07 and Cart/Order snapshots, reservation ownership/recovery, authorized persisted delivery/COD/payment, atomic settlement/refund and Store isolation tests. Reuse existing owner proposals; do not ask again for baseline S01/OD09 or Product unit-price business receipt.

Finance flags remain unchanged/default OFF. Pure49Finance tests within109Orderunit do not prove settlement DB/HTTP isolation. Current Ledger follow-up links this evidence without replacing earlier historical snapshots.

## Human review text and remaining actions

**89 suggested review:** “Reviewed entire six-file diff at17f98f4. No actionable blocker in internal Product lifecycle→persistence. Independent TV5 full Product build538PASS (440unit+98PG), combined89/90/91tree640PASS separately. Missing/ambiguous authority stays closed; current Product/version/ownership/state and transaction/concurrency guards verified. This accepts internal implementation only, not HTTP/JWT/current Store adapter/Checkout/Finance/E2E/deploy/flags. READY_FOR_HUMAN_APPROVAL; human submits their own whole-PR review.”

**94 suggested review:** “Reviewed entire four-file diff at96d59ca. No actionable blocker in Seller-local bounded current Store/Seller owned-facts projection. Independent TV5 full Seller build384PASS (246unit+138PG, including21newfactsPG). Exact owner string/current statuses+versions/READ_COMMITTED and DB-outage/concurrency/absence behavior verified. Private ownerReference is not caller identity or House classification; no remote API/authentication/lease acceptance. READY_FOR_HUMAN_APPROVAL; human submits their own whole-PR review.”

| Member | Waiting for TV5 | Delivered | Remaining blocker |
|---|---|---|---|
|TV2|Independent89/94 and final90/91 QA;83own thread|Fresh exact-head full builds, combined640, no introduced blocking finding;83FIXED_VERIFIED, current price ledger|Human TV5 formal whole-PR decisions; TV2 normal Draft/integration workflow. Runtime prerequisites are separate tasks.|
|TV3|Catalog source compatibility and current Finance/COD state|Exact batch/search+combined source QA, explicit missing live-wire/current authority/House; unchanged FINdiagnostic GREEN|TV1/TV2/TV3 signed wire/privacy/freshness; TV3/TV4 reservation/COD/snapshot source; business full policy.|
|TV1|Current QA/receipt and no stale Product price gate|Current scoped review/evidence and minimal ledger correction|Current Auth/role-sync/service-pair/security runtime; human/operator enablement outside this task.|
|TV4|No false Inventory→payment/settlement inference|COD/source/dedupe/isolation handoff references actual owner proposal|Versioned dispatch/delivery/COD producer/action/outbox/recovery and refund decisions with TV3/business/Finance.|

- [x] Current source/reviews/CI and explicit TV1 final-head re-review verified; #88 merge recognized.
- [x] Seven actual independent source-bound runtime invocations and raw hash reconciliation; no test/assertion change.
- [x] #83 source fix and unchanged regression verified; closure/publication receipt follows separately.
- [x] Finance price-only acceptance ledger corrected append-only; signed packet/history preserved.
- [ ] Human TV5 formal whole-PR review89/94/90/91 (and83 test-only if desired). Agent submits COMMENT only.
- [ ] Owner normal integration/readiness, runtime contracts/Auth/COD/atomic settlement/live E2E; Issues5/12 remain OPEN/PARTIAL.


## Published review and own-thread disposition

After evidence commit76e99572cce68a43a45dd764b3b303d7c4d00557 was pushed and GitHub head verified, TV5 submitted COMMENT reviews [#89](https://github.com/duyxl56785/Project_HK5/pull/89#pullrequestreview-5643550058) · [#94](https://github.com/duyxl56785/Project_HK5/pull/94#pullrequestreview-5643550440) · [#90](https://github.com/duyxl56785/Project_HK5/pull/90#pullrequestreview-5643550842) · [#91](https://github.com/duyxl56785/Project_HK5/pull/91#pullrequestreview-5643551253) · [#83](https://github.com/duyxl56785/Project_HK5/pull/83#pullrequestreview-5643551596). No APPROVE submitted; human TV5 whole-PR decisions remain pending, not silently ticked by technical QA.

- [x] #83 own thread PRRT_kwDOVGqk_M6v6eDr is **RESOLVED_FIXED_VERIFIED**, [verification reply](https://github.com/duyxl56785/Project_HK5/pull/83#discussion_r4371081982); original review and synthetic RED remain unchanged. No other reviewer's thread resolved.
- [x] Issue5/12 own checkpoint comments updated with evidence-backed subtasks, all history retained; both OPEN/PARTIAL. [Issue5](https://github.com/duyxl56785/Project_HK5/issues/5#issuecomment-6282711041) · [Issue12](https://github.com/duyxl56785/Project_HK5/issues/12#issuecomment-6282712138).
- [x] PR93 updated in place; TV1 and default TV2 re-requested **once** for substantial new source-bound QA/Finance-ledger delta after their oldbffe reviews. No duplicate, self-review or restored deliberate removal. This later receipt-only commit does not request again.
- [ ] Human decisions89/94/90/91/83; TV2 integration/readiness; TV1/TV2/TV3 runtime wire/current-authority/House/freshness and TV3/TV4/Finance/business COD/refund/settlement gates as above. No new execution inferred from docs-only publication.

[Publication receipt](evidence/tv5-product-integration-20261025/publication.json) records actual review/Issue URLs and reviewer request state at its timestamp, not future live state.
