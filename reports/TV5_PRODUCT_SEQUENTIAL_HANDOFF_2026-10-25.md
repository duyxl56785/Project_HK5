# TV5 — tuần tự Product/Catalog và bàn giao TV1

Refs #5 #12 #89 #90 #91 #94 #96 #97. Snapshot25/10/2026 UTC+7; [head/base/reviews/CI/sequence evidence](evidence/tv5-integration-sequence-20261025-r3/sequence.json).

## Quyết định review và thứ tự bắt buộc

**Human TV5 review → TV2 tích hợp89 → kiểm tra main →90 → kiểm tra main →91 → kiểm tra main.94 độc lập.** TV5 không Approve/Merge/Ready, không sửa branch owner. Main hiện `ed85f6290de7f611c82e5c0ad973f98180f04df4` chỉ chứa baseline88 trong chuỗi này, **89/90/91 đều chưa merge**. Không gọi rehearsal640 là postmerge verification.

| PR | HEAD / current review | Evidence và kết luận | Bước tiếp |
|---|---|---|---|
|89|17f98f4fc96b19f784fe39eeb5fd0c90e49915f6; TV1APPROVED,TV5COMMENT; Ready|Previous exacthead440unit+98PGPASS, currentCI40182643630SUCCESS; unchanged whole6file diff, no new blocking finding. READY_FOR_APPROVAL human-only.|HumanTV5 formal review; TV2 chỉ bắt đầuA sau các gate áp dụng.|
|90|6a290fffc35e6795f9e25f4a79b2b6fe74aa757f; TV1/TV3APPROVED,TV5COMMENT; Ready|Previous exacthead447unit+107PGPASS, CI40197447319SUCCESS; whole6file source unchanged. Internal source ready for human review, not C01/E01 livewire.|Chưa sync/test trên main sau89; giữBchờA.|
|91|4915c3768a310b519e218674e405b132103cd024; TV1APPROVED,TV5COMMENT; Ready|Previous exacthead448unit+96PGPASS, CI40197452631SUCCESS; whole7file diff unchanged. Private candidate search ready for human review.|Chưa sync/test trên main sau90; giữCchờB.|
|94|96d59ca320845495dd2ce6acf42d0b10ee0286a6; TV1APPROVED,TV5COMMENT; Ready|Previous exacthead246unit+138PGPASS, CI40122424378SUCCESS; whole4file diff unchanged. Local Seller facts ready for human review.|Review/accept riêng; không tự chèn vào89→90→91.|
|97|aa790f422a8b3ee698f53e74c455edae7b77d6fa; Draft,TV1/TV5 requested at snapshot|**New TV5 actual488unit+115PG=603PASS**,0FAIL/skip,exit0; CI40207706263SUCCESS. Read entire9file diff, no actionable introduced blocking finding in private JSON/handler preparation.|COMMENT technical handoff; TV1 authority/error boundary review and humanTV5. Stacked on89, not a new step betweenA/B/C; do not exposeHTTP.|
|96|c59f34024748f290bd304b64140cf6c0848940b5; baseAuth80@dfdc814;TV2requested|**New TV5 actual25securityunit+28AuthPG+5explicitPG=58PASS**,0FAIL/skip,exit0; CI40207083756SUCCESS. Narrow NUL rejection fix/tests verified; PBKDF2format unchanged.|TV2 review child; TV1 accept into80then rerun actualparent. Parent80P2/thread remains distinct, no resolution byTV5.|

Four priority runtime suites this task: **NOT_RUN — unchanged source**, previous exact-head evidence retained in [previous review](TV5_PRODUCT_INTEGRATION_REVIEW_2026-10-25.md). New97/96 runs are separate invocations; no summed release total, no history-as-freshPASS. Raw log/XML private unchanged with hashes, derived [97](evidence/tv5-integration-sequence-20261025-r3/pr97.json) /[96](evidence/tv5-integration-sequence-20261025-r3/pr96.json).

## Gate A → B → C cho TV2

Mỗi bước dùng local worktree/branch owner đã bảo toàn WIP; không directmain/force/bypass. Record pre-sync main SHA, final PR HEAD/base, exact merge receipt, command/time/exit/freshXML/hash. **Nếu FAIL: dừng bước đó, không tiếp tục bước sau.** NormalCI SUCCESS không tự chứng minh explicit FINdiagnostic hoặc từng task PG nếu chưa kiểm tra actualtask/log.

| Bước | Thao tác TV2 | Điều kiện để đi tiếp | Hiện tại |
|---|---|---|---|
|A:89|Sau humanreview, sync với latestmain bằng lịch sử tiến tới, kiểmV1/V2/persistence/@Version/rollback/exactVND. Run full Product build ở finalPRHEAD, xác minhCI/reviews/threads/mergeability; merge bình thường khi được phép; fetch actualmain và chạy lạifullProductbuild.|GitHub89merged=true; actual merge_commit_sha (kể cả squash) là ancestor của actualmain; fresh finalPRCI+postmerge main unit/PG PASS, migration/authoritydefault regressions giữ nguyên; lưu receipt. Chỉ PRHEAD ancestor là không đủ cho squash.|**WAITING_HUMAN_REVIEW/TV2; postmerge NOT_RUN**.|
|B:90|Chỉ sauAgreen: sync90trên main chứa89. Giữ ProductID→exactSKU, successfulABSENT≠UNAVAILABLE, all-or-nothing/price/currency/Store/House negatives; run unit/PG/internalcontract tests trên finalHEAD và actualmain sau merge.|Prove maincontains89+90actualmerge receipts, freshCI/unit/PGPASS; source không làm mất freshread/version/rollback89. C01/E01 HTTP/privateauthority vẫn separate; no productionprovider/House inferred.|**BLOCKED_BY_A; sync-after89/postmerge NOT_RUN**.|
|C:91|Chỉ sauBgreen: sync91trên main chứa89+90. ProductRepository phải giữ cả batchquery/projection90 **và** searchfragment91; không chọn ours/theirs/copyđè để mất một phía. Run completeProductbuild và regressions ở finalHEAD vàactualmain sau merge.|Prove ordered merge receipts89→90→91; fresh fullPG/unit/CI, migration/persistence/lifecycle/batch/search regression allpass. Verify filtering/literalLIKE/ACTIVEcandidates/UUIDtiebreak/localRRcount-row/concurrency; publicStoreeligibility notinvented.|**BLOCKED_BY_B; sync-after90/postmerge NOT_RUN**.|

Actual command from each corresponding exact checkout (Java21 +Docker ready):

```powershell
$env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC -Dapi.version=1.44'
.\backend\gradlew.bat -p backend :product-service:build --rerun-tasks --no-daemon --console=plain
```

Chỉ count fresh XML của invocation; ghi SQL-candidate riêng, không thay productionProductPG. Không yêu cầu đạt một số test cố định nếu actual source thay đổi; phân loại từng suite, không suy GREEN từ tổng640cũ. Source changes sau sync cần re-review delta và exactCI; approvalstale không tựdismiss theo ruleset không có nghĩa đã reviewsource mới.

##94: dependency thật sự

94không importProduct/Checkout hoặc ngược lại; cả4PRs build riêng. Seller DB sở hữu local Store→Seller FK và exact persisted ownerReference; Product chỉ giữ logical StoreUUID.94là **nguồn facts cục bộ** cho adaptertươnglai, không phải authenticated remote API, currentAccount proof, House source hay freshnesslease.90yêu cầu explicit currentStore/Seller/House;94**không có Housefield**, nên không thể wire thẳng hoặc defaultfalse. TV1+TV2 concreteProduct→Seller pair/API/currentactor/privacy và TV2/business authoritativeHouse còn thiếu. Chúng không chặn internal code89/90/91, nhưng chặn positiveHTTP/Checkout.

## Bàn giao TV1 song song — chưa đủ runtime

Contract baseline hiện hành: [Auth/Identity/Gateway v1.0](https://github.com/duyxl56785/Project_HK5/blob/ed85f6290de7f611c82e5c0ad973f98180f04df4/docs/TV1_AUTH_IDENTITY_GATEWAY_CONTRACT.md), [TV1-S01-OD09-20261018-v1](https://github.com/duyxl56785/Project_HK5/blob/ed85f6290de7f611c82e5c0ad973f98180f04df4/docs/TV1_S01_OD09_INTEGRATION_HANDOFF_2026-10-18.md). Không xin lại baseline design đã chốt. Các API/interface dưới đây là hiện trạng, không tự chọn endpoint/claim/error mới.

| Hạng mục / owner | PR/SHA + version/interface hiện có | Test / CI evidence | Blocker / yêu cầu bàn giao tiếp |
|---|---|---|---|
|VerifiedJWT/currentprincipal —TV1|80/dfdc8143c4a86cdfce8eefc9a98e1b07126da41d;96/c59f340child. Authv1.0; Hk5JwtCodec +CurrentAccountAuthority.requireCurrentForRequest; conditionalbootstrap, no HTTPfilter/controller.|New96:4codec+5currentrole+5bootstrap+11hasherunit,28AuthPersistencePG+5explicitcurrentauthorityPGPASS;80CI40120710982,96CI40207083756SUCCESS (separate).|#96notintegratedin80/main; currentactive-status approval, runtime secrets, actual registered request verifier/AuthAPI/principal adapter+negativeHTTP/PG proof absent. Codec/unit success doesnotmeanAuthruntime delivered.|
|Currentroles +SELLERsync —TV1+TV2|80currentDBroles require tokenrole ANDDBrole;S01/OD09v1 requires durable versioned Seller→Auth command afterSellercommit. No merged wire/worker/rolegrant endpoint receipt found.|5currentauthorityunit+5QAactualPG in96 test currentroles/revocation/outage, **not SELLERsync**.|TV1+TV2 exact command/schema/ordering/idempotency/retry/revoke/currentstate/Sellerapproval mapping, durable provider+consumer and concurrency/recoverytests; record newPR/HEAD/version.|
|Policybinding —TV1+TV2|89ProductAccessPolicy uniqueprovider;97ProductManagementAuthority.requireCurrentActor port;94localfacts. Private interfaces, no concrete trusted Product authority bean.|New97realPG17handlercases; authority/Storepositivesfixtures; denydefault route negative, notpositiveHTTP.|Bind current verified account/roles to exact serverowned Store/Seller/currentstatus. Missing/ambiguous/unavailable failclosed; forgeheader/body rejected. Exact HTTP401/403/503 mapping needsowner review, no genericerroradapter treated as Authprotocol.|
|ReactiveGateway —TV1|mained85f6290de7f611c82e5c0ad973f98180f04df4:only ApiGatewayApplication +minimalapplication.yml. Authv1.0/OD09; no runtime route/security/filter source receipt.|No Gateway verifier/route/security suite found; **NOT_RUN**, Backendreadiness status not proof.|NewPR/HEAD for WebFlux security/routes, independentdownstreamverification, no public/internalproxy, spoofheader/CORS/CSRF/negativepair tests; actualrequest interface andCI receipt.|
|Servicepairs —TV1+TV2/TV3/TV4|OD09v1 baseline scopes; concreteOrder→Catalog/Product→Seller anddelegated/systemadapter/version nothandedoff. Private97authority port isnot apairfilter.|No positive livepair tests in97/96; fixtures/deny403 notservicecredential proof.|Exact caller/callee/method/path/operation/credentialrotation/provisioning, originalBearer vs system provenance, currentactor/privacy +401/403/503, crosspair/noncanonicalpath/no wildcard negatives, positive tests atintegrationHEAD.|
|Frontendrouter/APIclient —TV1+consumers|main frontend/src/app/App.tsx andshared/domains.ts scaffold;95ProductUI/86CartCheckout isolatedcomponents.97preparedprice/versionstrings matchinternalprojection, no shared approvedwire.|PriorUI fixtures/HappyDOM/browser separate; current97PG isnot frontendtransport. ProtectedHTTP/hostreload/currentprincipal **NOT_RUN**.|Versioned router/authstate/session lifecycle, APIclient/tokenrefresh/singleflight/error DTO/version mapping. VerifiedStore-scoped reload receipt required by95;86trustedSessionScope notauthentication. Supply realadapterPR/SHA+contracts+tests.|
|FrontendCI —TV1|main has only .github/workflows/backend-readiness.yml, pathsbackend/docker/workflow. No frontendworkflowPR/run handedoff in allopen scan.|95/86noCIchecks; localtypecheck/build/DOMPASS notCI. **FrontendCI NOT_IMPLEMENTED/NOT_VERIFIED** at examinedsources.|OwnerPR for frontend install/runtime/cache/lock strategy, typecheck/build/unit/SSR/DOM andboundedbrowser checks; exacttrigger/head/run/outcome. No neededitbackendworkflowfromTV5.|

TV1must handoff actualPR/commitSHA +contractversion +interface +testcommand/counts/rawhashreference +CIrun/head +owner/runtimeblockers for eachrow. Run positiveHTTP/currentroles/Storeisolation only after actualadapter exists. No Auth/runtime deliverable inferred from NULfix, password primitive or bootstrapunit tests.

## Finance và human review

MD01/MD02/ProductunitMD03 valid businessreceipt84 +LevelA+TV2mappingACK+88LevelB/merge remain effective. No re-sign unchanged Productpriceslice; no inference of quantity/aggregate/MD04–10/House/rounding/COD/refund/settlement/Financeapproval. FullFinanceproduction, CODauthority, atomicsettlement, protectedHTTP/Checkout/live2StoreE2E remain separatePENDING/BLOCKED; flags unchanged/OFF.

HumanTV5 có thể dùng full-PRreview text trong [prior report](TV5_PRODUCT_INTEGRATION_REVIEW_2026-10-25.md): review89→90→91 và94riêng. `Files changed → Review changes → Approve → Submit review` chỉ khi human đồngý toàn bộ scope và currentHEAD không đổi; đây không là permissionmerge/deploy/HTTP/businesspolicy. TV5agent hiệnCOMMENT, không tựApprove.

- [x] Current4priorityhead/diff/CI/scope verified, no new P0/P1/compatibilityblocker in unchanged internaldiffs; existingQAretained withoutrerunclaim.
- [x] Newwhole97requestedreview/source/603actualPASS; independent96narrow58actualPASS withexplicitPGtask; rawbytehashes verified/private.
- [x] GateA/B/C +94realdependency +seven-rowTV1handoffmatrix prepared; no batchmerge/ownerproductionedit/flag.
- [ ] HumanTV5 formaldecisions;TV2Aactualmerge+mainGREEN,thenB,thenC. **No postmergegate ticked from rehearsal.**
- [ ] TV1handoffruntime/frontendCI,TV2consumercontracts/House;fullFinance/COD/atomicsettlement/liveE2E. Issue5/12 OPEN/PARTIAL.
