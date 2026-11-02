# TV5 review and owner unblock on 25 October 2026

Refs #1 #2 #3 #4 #5 #9 #12 #15 #18. **PARTIAL / WAITING_OWNER / WAITING_HUMAN_REVIEW**, not release DONE.

## 1. Scope and current facts

Read AGENTS.md/CONTRIBUTING.md/OWNERSHIP; root WIP `.vs/` and `reports/tv5-m01-local/` preserved. No checkout switch/reset/stash of root, ownerbranchpush, forcepush, mainpush, sourcefeature/deploy or existingvolume deletion.
Initial paginated audit **75PR =12OPEN (11Draft)+61MERGED+2CLOSED_UNMERGED**,230submittedreviews,16Issues. All API-visible commits/files/reviews/comments/threads/checks/removals/rules retrieved. Unpublished other-user drafts unavailable. Latest main **23f583dd7dae164b0a2932d1fe66b2d0eaffdd9d**. Metadata audit of all historicalPRs is not a claim of rerunning all historic source. Deep runtime/source focus:80/83/85/87/88/89/90/91; unchanged69/77/78 kept separate historical QA;86 retains existing ownerfinding.

Authdocs92 was a later publication event, not retroactively included in75. This report's own PR is another publication event; finalPR/Issue receipt supplies newest count/head/reviewer status.

## 2. Outstanding findings and work completed

| Priority / scope | Verified fact | Action / owner |
|---|---|---|
|80 ownP2|FIXED_VERIFIED atc8ebb98. TV5 actual14unit+28AuthPG and5unchanged authorityPG PASS; originalQAblobs identical, sourcefix integrated.|[Own thread resolved](https://github.com/duyxl56785/Project_HK5/pull/80#discussion_r4367965928); no otherreviewerthread touched.|
|80 importedQA P3|Stale runbook incorrectly treated parentf88/integration pending and bootstrap as always inert.|[TV5 supplement92](https://github.com/duyxl56785/Project_HK5/pull/92), currenthead `67a802bd247fe93fe7f9aa944a64abebe8748d9a` corrects current versus history and opt-inbootstrap. TV1acceptdocs+updatePRdescription; TV2requestedonce for actualnewbootstrapdelta.|
|83↔88 integration P2|Bothheads pass independently and textmerge succeeds, **but resulting test compilation fails**: new83 test calls old8argument ProductListing;88 requires explicitcurrency9argument constructor.|TV2 adapt on safesynchronization: `CatalogSchemaProposalPostgresTest.java` **PR83line73**, pass explicitVNDfixture; do not add default-VND compatibilityctor or change/skip assertion. Preserve prototype-vs-production distinction.|
|86 existing TV2P2|At unchangedec8d881, readySSR case-sensitive payment-copy assertion mismatches actualrendering. TV2original8PASS/1FAIL belongs to its invocation.|OPEN/NEEDS_TV3. TV5didnotrerunNode today or resolve TV2thread; wait fixedhead for targetedre-review.|
|88 oldreason finding|TV1DISMISSED its erroneousCR and acknowledged existing canonicalconstructor strips reason beforeDB. Newtest-only199211b adds matchingcontrols.|WITHDRAWN, not a remaining blocker. Do not ask TV2tofix it again.|
|88LevelB|ProductionV2/unboundednumeric/exactVND/immutableglobalSKU/UTF16/FK/version/rollback source and actualPGacceptance independentlyverified, no actionableintroduceddefect found.|READY_FOR_HUMAN_LEVEL_B_REVIEW in privatepersistence scope only; agenthasno blanketLevelBapproval. NotpositiveHTTP/provider/deploy/Finance approval.|

PR82 liveAPI: **MERGED into Auth feature**, merged_at2026-10-24T07:42:05Z, mergecommitc8ebb98, not main. Earlierownercomment sayingclosedunmerged was wrong as currentstate. TV5 corrected its own newrunbook/comment immediately using liveAPI; originalruntime/rawRED/GREEN unchanged. 79/81/84 and71/72/74/76 are MERGED; do not push/re-request closedPRs. Only60/62 remainCLOSED_UNMERGED.

## 3. All currently open PRs and reviewers

Snapshot `2026-10-24T18:01:28.293228+00:00`; after92publication/beforethisreportPR. Initial80P2 was theonlyTV5unresolvedthread andisnowresolved. **Only86has effectiveCHANGES_REQUESTED**, byTV2.69oldCRsuperseded byTV1approval;88invalidCRdismissed. No duplicate requests, authorrequests or deliberatelyremovedreviewers restored. Main stillrequires1approval+resolvedthreads; affectedownerreview perCONTRIBUTING remainsapplicable.

| PR | Head checked | Effective review | Requested reviewers | CI | Next action |
|---|---|---|---|---|---|
|[#69](https://github.com/duyxl56785/Project_HK5/pull/69) Draft|`4eeeb3843f2795168ca03cdd3cd2e8be9f825ec6`|APPROVED|—|readiness:success|TV4 owns Draft/C06/runtime co-signs; TV1 scoped approval already present.|
|[#77](https://github.com/duyxl56785/Project_HK5/pull/77) Draft|`cbbdb8667f689eddb75690274636504efc664706`|APPROVED|—|readiness:success|TV3 owns Draft/helper readiness; no new source or repeat QA needed.|
|[#78](https://github.com/duyxl56785/Project_HK5/pull/78) Draft|`ac5da331e8cf6128b26ee850f87eef18d99a3317`|APPROVED|—|readiness:success|TV3 owns Draft/test readiness; TV4 already COMMENTED, no repeat request.|
|[#80](https://github.com/duyxl56785/Project_HK5/pull/80) Draft|`c8ebb98ed77b4e0ec7a8ef9b00839c31523c9ea2`|APPROVED|duyxl56785|readiness:success|Own P2 resolved after newparentQA; TV1 accept92/update description, TV2 review newbootstrap, keepDraft.|
|[#83](https://github.com/duyxl56785/Project_HK5/pull/83)|`1018c2d7d8323aa0ba2a312fc278ee37c0b57a0c`|REVIEW_REQUIRED|nguyenminhduy090-alt|readiness:success|33candidatePG PASS. TV2 must adapt test currency on integration with88; synthetic merge compileFAIL, not baseline failure.|
|[#85](https://github.com/duyxl56785/Project_HK5/pull/85) Draft|`fc39c7100caa7ffb49f9baa27b18dde595b54d1f`|REVIEW_REQUIRED|—|readiness:success|16unit+14PG PASS; private handler only, TV1 runtime authority remains separate.|
|[#86](https://github.com/duyxl56785/Project_HK5/pull/86) Draft|`ec8d881e8da5f7497aef7c4ad6018b215cf4a059`|CHANGES_REQUESTED|lexuanbao258-collab|NO CHECKS|TV3 fix existing TV2 P2 SSR expectation; samehead, no reviewer-thread takeover.|
|[#87](https://github.com/duyxl56785/Project_HK5/pull/87) Draft|`1a67b01315cc328a9c28b4f728ad0cb17a7225a0`|REVIEW_REQUIRED|nguyenminhduy090-alt|readiness:success|121focusedPASS; private filesystem only, human scope review/Draft and HTTP/image-linkage gates remain.|
|[#88](https://github.com/duyxl56785/Project_HK5/pull/88) Draft|`199211beb55656f88c7b33850289e887d1390fbe`|REVIEW_REQUIRED|nguyenminhduy090-alt|readiness:success|437unit+61PG PASS; invalid TV1 P2 withdrawn. HumanTV5 LevelB and author Draft/merge workflow required.|
|[#89](https://github.com/duyxl56785/Project_HK5/pull/89) Draft|`036c80db61684a5c0a22379c6215ef8cb76917cf`|COMMENT/PENDING|nguyenminhduy090-alt|readiness:success|3unit+59PG PASS; parent88 acceptance/merge first, then safe retarget/review/CI.|
|[#90](https://github.com/duyxl56785/Project_HK5/pull/90) Draft|`6b5897956659439066500ccf6069b030ad8f7352`|COMMENT/PENDING|tranvanchinhbg2006-alt, nguyenminhduy090-alt|readiness:success|10unit+46PG PASS; TV3 consumer review requested; current source/House/security/freshness runtime gates separate.|
|[#91](https://github.com/duyxl56785/Project_HK5/pull/91) Draft|`0860dc940bba44b5eceee082db2d2ea784a565b1`|COMMENT/PENDING|nguyenminhduy090-alt|readiness:success|11unit+35PG PASS; private candidates/counts only, parent88 then final retarget/review/CI.|
|[#92](https://github.com/duyxl56785/Project_HK5/pull/92)|`67a802bd247fe93fe7f9aa944a64abebe8748d9a`|COMMENT/PENDING|lexuanbao258-collab, duyxl56785|NO CHECKS|TV5 Auth runbook/parent evidence supplement; TV1+TV2 requested, no main merge.|

ReviewCOMMENT is notAPPROVED. Existing ownerCOMMENTs85/87/88/89/90 are actualresponses, not unansweredrequests. Agent's newQAcomments and human decisions after this snapshot are later publication events, not retroactive reviews in230.

## 4. Independent fresh execution, one source per row

All below Java21.0.7/Gradle8.10/Docker29.8.1/UTC/API1.44, actual PostgreSQL16 Testcontainers for DB suites. Full commands/timestamps/exit/cases/rawhashes in each linked receipt. FreshXMLonly, no staleXML, failures/errors/skips0 in these eight source runs. Rawlogs/XML private, bytes andSHA256verified beforeexport; no fixturecredentials/systemoutpublished. No sum acrossrows/CI/ownerhistory.

|PR/head|Actual task scope|PASS|Evidence/limits|
|---|---|---|---|
|80/c8ebb98|Filtered codec/currentrole/bootstrap/AuthPersistence +explicit unchanged authorityregression|14unit+28AuthPG; separate5authorityPG|[80](evidence/tv5-audit-20261025/pr80.json); conditionalbootstrap tests, noHTTP/Gateway proof.|
|83/1018c2d|CatalogSchemaProposalPostgresTest|33PG|[83](evidence/tv5-audit-20261025/pr83.json); SQLcandidate, not ProductproductionDB.|
|85/fc39c71|SellerStoreReadHandlerTest +SellerStoreReadIntegrationTest|16unit+14PG|[85](evidence/tv5-audit-20261025/pr85.json); authorityfixture, ownreadDB, no realJWTHTTP.|
|87/1a67b01|Storage+ImageValidator+ProductSecurity focusedunit|121=31storage+67validator+23security|[87](evidence/tv5-audit-20261025/pr87.json); realtempFS/negativeguards, not imageDB/upload/E2E or hostile sameaccountFS guarantee.|
|88/199211b|Fullproduct-servicebuild|437unit+61PG|[88](evidence/tv5-audit-20261025/pr88.json);61PG=23productionProduct(T01–T15+regressions)+14Category+24SQLprototype.|
|89/036c80d|Applicationunit +Application/Persistence integration|3unit+59PG|[89](evidence/tv5-audit-20261025/pr89.json);59=36application+23persistence, fixtureauthority only.|
|90/6b58979|SnapshotSource unit+PG|10unit+46PG|[90](evidence/tv5-audit-20261025/pr90.json); realProductDB/fixtureStorefacts, notliveHouseauthority.|
|91/0860dc9|SearchQuery unit+SearchPG|11unit+35PG|[91](evidence/tv5-audit-20261025/pr91.json); localRRcount/rows, privatecandidateTotal, not eligiblepublictotal.|

**Separate compatibility probe FAIL:**88production + three-way merged83test/resource → `:product-service:compileTestJava` exit1, constructor lacks Stringcurrency. This is a synthetic source combination, **not a FAIL on either standalonePRhead**, and no branch was merged. [Probe/source/rawhash metadata](evidence/tv5-audit-20261025/repository-reviewers.json). RawlogSHA256 `fdff4290cd6e3aa77d3ed71147dc0c32cc000e6c904696b89bfd3d9e8a4f1d5c`. Statictextmerge90↔91Repository succeeds; combinedruntime **NOT_RUN**. 89 has no changedpath overlap with90/91. After parent88merge, owners must safe-retarget/synchronize andrerunthefinalcombinedhead; donotmergechildrenintoparentunreviewed.

## 5. Money, Store and integration boundaries

- [Humanbusiness84](https://github.com/duyxl56785/Project_HK5/pull/84#issuecomment-6364386964) +[explicitlyauthorizedTV5LevelA](https://github.com/duyxl56785/Project_HK5/pull/84#pullrequestreview-5637148973) +[TV2mappingACK](https://github.com/duyxl56785/Project_HK5/pull/84#issuecomment-6381447408) complete the **Productpriceimplementation gate**. Do not keep saying namedpriceauthority/mappingACK missing. Exactpacket463338e/digest a02370d9… preserved;84MERGED. MD01/MD02/unitMD03 approveVND/integral1..1e9/no-rounding/string only, notquantity/aggregate1e12/MD04–MD10/operator/Finance.
- 88 implements that mapping: no-typmodNUMERIC andintegral/rangeCHECK; required exactVND/noDEFAULT; Java originalBigDecimal; SQLdirectINSERT/UPDATE/COPY guards. GlobalexactSKU/UTF16/immutability andFK/version accepted by runtimechecks **on branch88**, not main/deployedauthority.
- 89 ties freshstate/ownership decisions to expectedversion then JPAwriteversion; REQUIREDrollback anddenyallwithoutauthority. Stringactor is not trusted by itself. 90batchsource preserves confirmedABSENT versusUNAVAILABLE, rejects incompleteStore/Seller/House/currentfacts andall-or-nothingconsumer snapshots. Missingcurrentauthority remainsunavailable.
- 91searchreturnsprivate Productcandidates; ACTIVE is not currentSeller/Storeeligibility. RepeatableReadcount/rows is local, not Cataloglease or publiceligiblecount. 90READ_COMMITTEDProduct andStorefacts are separateobservations. C01/E01/freshness/security must be signed before livewiring.
- ProductimmutableSKU supportsmapping, not permissionforInventorypair. 69candidateC06,77unwiredpreflight,78recoverytests, CODPAID/DELIVERED/refund/atomicunique settlement/currentStoreFinancequeries remainjointownergates. No StockCOMMITTED→PAID orinventoryconsumption→delivery inference. No commission/House/rounding/settlement enablement fromProductpriceapproval.

## 6. Member actions and priority

|Priority|Owner|Smallest next step|
|---|---|---|
|1|HumanTV5 +TV2/TV1|Decide formalLevelB88 basedoncurrent evidence; authorcontrolsDraft/readiness/merge. No autoApprove/merge.|
|1|TV2|Resolve explicitcurrency test mismatch when integrating83with88; do not interpret CIgreen/textmerge as combinedcompileproof.|
|1|TV3|Fix86existing SSRcase/wording andrerun; TV2owns itsCR/thread.|
|1|TV1 +TV2|Review92andnew80bootstrapdelta; update80description/active-status/secret/runtimegates. P2neednotwaitorbereopened.|
|2|TV2 +TV1/TV3|88first, thenretarget89/90/91toacceptedmain withfinalCI/review; TV3currentrequested90consumerreview, notanotheroldunitcapvote.|
|2|TV3/TV4|DisposeDraft77/78/69withinapprovedscope; C06/status/COD/source/recoveryco-signs separately, no duplicateoldreviewrequests.|
|3|TV1/TV2/TV3/TV4 +business +TV5QA|RealcurrentAuth/Store/House/pair/C01E01/MD07/COD/refund andfullFinancepolicy, thenauthorizedruntimeintegration/UAT. Keep broadIssue5/12/18PARTIAL.|

- [x] Audit75PR/16Issues; currentReviewers/CI/removal/ownerresponses reconciled.
- [x] Independent exactsourceQA for eightheads, preserved rawproof; onlyownAuthP2 resolved afterparentverification.
- [x] FixTV5runbook/importedQA handoff via92, branch`codex/tv5-auth-pg-handoff-20261025`; no ownerbranchpush.
- [x] Identify actualsynthetic83/88compilefailure; preserveRED, no assertionlowering/defaultVND workaround.
- [ ] HumanLevelB88/formalPRapprovals andauthorDraftdispositions; onlyA84waspreviouslyauthorized.
- [ ] TV2combinedtestfix,TV386SSRfix,TV1runtime/security/docsreview,ownerjointcontracts/liveUAT. No fullF15/F16/IssueDONE.

CurrentNOT_RUN: liveJWT/Gateway/Store/House/provider/CheckouttwoStore/COD/refund/atomicsettlement/Financequeries, physicaldevice/screenreader, actualimageupload/delivery, productiondeployment. Existing86TV2FAIL is historicalownerevidence; TV5didnotclaimnewNodeFAIL. CurrentruntimePASS is not livedeploymentpermission. No existingvolumes/data deleted, no credentials published, no newbusinesssignature or ignoredtoolapprovalbypass.

## 7. Later authorized Level B decision and published review handoff

After the preceding snapshot, the human user explicitly authorized **“Cho phép APPROVED mức B riêng #88”**. TV5 submitted [APPROVED LevelB](https://github.com/duyxl56785/Project_HK5/pull/88#pullrequestreview-5639341936) at exact **199211beb55656f88c7b33850289e887d1390fbe**, and GitHub read-back confirmedAPPROVED bynguyenminhduy090-alt. This supersedes only the earlier `WAITING_HUMAN_LEVEL_B` task; prior snapshot rows remain history, not a current missingTV5gate.

- [x] LevelB88 for actual private ProductV2/JPA/Repository/adapter implementation is satisfied following437unit+61PGindependentPASS. No newbusiness/MD04–MD10/Finance/COD/HTTP/provider/E2E/deploy/merge/flagpermission; authorstillcontrolsDraft/normalmerge.
- [x] Scoped COMMENTQA published on83/85/87/89/90/91 with exact-headexecution/evidence.83alsohas inlinecombined-sourceconstructorfinding; no new approval onthesePRs and no duplicateoldreview.
- [x] Own80P2resolved and92docfixwithcorrectlive82MERGEDstatuspublished. TV2requestedoncefornewbootstrapdelta, existing92/93TV1/TV2requestsretained.
- [ ] TV2safeintegration:88ownerworkflow first, retarget/sync83/89/90/91andfinalheadCI/review;83explicitcurrencytestfixrequired. TV3fix86SSR;TV2owns86thread.
- [ ] TV1Authruntime/secret/activestatus/PRdescription+92acceptance,TV3consumer90,otherownerDraftdispositions; fullFinance/COD/liveUATgatesremain. Issue5/12/18OPEN/PARTIAL.

[Publishedreview URLs/authorizationreceipt](evidence/tv5-audit-20261025/review-publication.json). Previous `onlyA84authorized` statements apply to the earlier snapshot, not this later explicitLevelBauthorization. No reviews/historicalfindings deleted or other-reviewerthreads resolved.
