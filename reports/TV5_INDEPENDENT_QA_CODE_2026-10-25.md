# TV5 — code QA độc lập,25/10/2026

Refs #5 #12. Hai nhóm code đã commit/push trên branch TV5 riêng; **không nhét code vào PR93 docs/evidence**. Giữ nguyên workspace/WIP và ownership, không agentApprove/Merge/close/flags. [Publication/source/review receipt](evidence/tv5-independent-code-20261025/publication.json).

## Inventory và quyết định coding

| Module | Coverage hiện có | Gap / trạng thái | TV5 thực hiện |
|---|---|---|---|
|Productprice/persistence|88T01–T15,46priceunit,productionPG; Productprice slice đãsigned|RequestedVND/integer/cap/no-rounding/constraints covered|Không tạo bản sao hoặc sửaTV2production.|
|Productlifecycle/handler|89PG36;97unit48+PG17|MissingrealcurrentAuth/Storepolicy isruntimecontractgate, not an untestedprivatewritefeature|Whole97review +fresh488unit/115PGPASS and COMMENT; noendpointapproved.|
|Catalogbatch|54validatorunit +90unit10/PG46|Absence,SKU,Store,invalid/duplicate/incompletebatch,price/currency/status/conflictingfacts,all-or-nothing alreadycovered|Không clone12requestedscenarios; sourceaudit retained.|
|Catalogsearch|91unit11+PG35|Publiceligibility/authority/count/freshness pending; privatecandidatepagination covered|Không invent publiccontract orchangeTV2source.|
|Financeexistingstorage|49Financeunit withinOrder109;25OrderPG;3originalFINdiagnostics|Lateoutboxserverfault,outerrollbackafterwriterreturn,parallelfinancialreplay,samekeycustomernamespaces missing|**PR99:4newPostgreSQLtests +reusabletwoStorefixture.**|
|COD/settlement|Pureguards alreadycovered; no trustedpayment/uniqueatomicsettlement/query implementation|PENDING/BLOCKED business/source/security; fakeSQLsettlementtablewouldnotproveproduction|No newphantomsettlementfixtureorpolicyimplementation.|
|SharedPRaudit|Reusablecollector existedonlyinignoredprivateverification|No trackedread-onlyCLI,source-race/pagination/credential/output-safetytests|**PR100:QAtool +27newofflinecases**,actualGitHubmetadataexercise.|

## Code và tests mới

| Priority | Công việc / code | Tests mới | Commit / PR |
|---|---|---|---|
|P0|FinanceDurabilityPostgresIT +FinanceTwoStoreFixture +README_DURABILITY|**4actualPG**:FD01lateoutboxrollback/localretry;FD02outerREQUIREDrollback;FD03synchronized4replays/frozen2Storefinancialrows;FD04twoCustomerssamekey/differenttotals|[446ae44](https://github.com/duyxl56785/Project_HK5/commit/446ae44da90832df534b6e37150f26a71f2adf25),[PR99](https://github.com/duyxl56785/Project_HK5/pull/99),branchcodex/tv5-finance-durability-qa|
|P2|scripts/qa/pr_review_audit.py,unit tests,README/cacheignore|**27offlineAPI/filesystemfixtures** for pagination/sourcechanged/oldCR/ownthreads/CI/missingdata/readonlyauth/redaction/redirect/evidencerace|[8bf9f12](https://github.com/duyxl56785/Project_HK5/commit/8bf9f12e1a7df92b77f8a506fba5e1907a4f1c6c),[PR100](https://github.com/duyxl56785/Project_HK5/pull/100),branchcodex/tv5-readonly-pr-audit|

**31 test mới: 4 PostgreSQL + 27 offline.** Đây không phải một lượt release gồm 31 test. Không phát hiện bug production cần sửa trong phần đã kiểm tra; hai nhóm code bổ sung coverage và công cụ QA còn thiếu. FIN-01/FIN-06, production của owner, migration, API và assertion cũ được giữ nguyên. Lỗi PostgreSQL được chủ động tiêm trong test không phải lỗi production.

## Kết quả thực thi và giới hạn bằng chứng

| Suite / invocation | Source | PASS / FAIL / skip | Evidence / limit |
|---|---|---|---|
|Financeinitialfocused|Baseed85f62 +two new test-source fingerprints,beforecommit|4/0/0|[Focused](evidence/tv5-independent-code-20261025/finance-focused.json); not another committed-head execution.|
|CompleteOrderunit +PG +originalFINdiagnostic|**446ae44**,trackedclean|**109unit+29PG+3diagnostics**,0FAIL/error/skip,exit0|[Committed](evidence/tv5-independent-code-20261025/finance-committed.json); new4includedin29, no additionofinitialfocused4.|
|QAauditoffline|**8bf9f12**|**27/0/0**,exit0|[Offline](evidence/tv5-independent-code-20261025/audit-offline.json); Python3.12.14, deterministicfakeAPI/localfiletests.|
|QAauditactualGitHub|**8bf9f12**|18/18OPENPRrecords,complete=true,0recorderrors,exit0|[Live metadata](evidence/tv5-independent-code-20261025/audit-live.json); **not18applicationtests/liveE2E**. PR99/100createdlaterarenotretroactivelycounted.|
|EarlierAuth/Productindependentreview|96/c59f340 and97/aa790f4|58Auth (25unit+28PG+5explicitPG) /603Product (488unit+115PG),0FAIL/skip perrun|[Priorsequentialhandoff](TV5_PRODUCT_SEQUENTIAL_HANDOFF_2026-10-25.md),source-executor-separated; notaddedtotheabove.|

Finance command: `backend/gradlew.bat -p backend --init-script <absolutecheckout>/tests/finance/fin06-regression.init.gradle :order-service:test :order-service:postgresIntegrationTest :order-service:fin06Regression --rerun-tasks --no-daemon --console=plain`,processJAVA_TOOL_OPTIONS UTC/API1.44;Java21.0.7/Gradle8.10/Docker29.8.1/PG16.6. Replay caller vẫn dùng MoneyRules(false), không bật policy ngay cả trong fixture.

Automation command: `python -m unittest discover -s scripts/qa/tests -v`; `python scripts/qa/pr_review_audit.py --repo duyxl56785/Project_HK5 --output-dir <newdirectory>`. Bothrunon8bf9f12. Initialdevelopmenthad22PASS/2TempIOerrors,tooltranscriptonly/nooriginalfileavailable; no fabricatedrawlog. Tempwrites/cleanupnownarrowedtoverifiedworkspace; originalassertionsretained,24thenexpanded27PASS. Finalnewcasecount27,not24+27.

Mọi summary công khai đều là dữ liệu dẫn xuất. Log/XML gốc được giữ nguyên byte, kiểm size/SHA256 và không publish credential sinh trong test. Không xóa volume/dữ liệu Docker hiện có. Kiểm JSON, link, lịch sử, diff và mẫu secret đã PASS; các kiểm tra tĩnh này không thay kết quả runtime.

## Bàn giao và việc còn chờ

- TV3: PR99 exercisesactualOrderwriter/attempt/outbox/PostgreSQL andcustomer-scopedreplay withtwoStorefixtures. OutboxSQLtriggerproves2children/2itemsalreadyflushedbeforeinjecting23514. SQL/localretryisnotremoteUNKNOWNcompensation. Reviewtransaction/concurrencycontrols, reusefixtureforfutureapprovedintegration. No Inventorycall/wire/SKUbridgefabricated.
- TV1: PR100 credential/query/read-only/pagination/outputboundaryreview; frontendCI/Auth/Gateway/servicepairsareseparatetasks. QAtoolneverrequests/reviews/mergesitself.96childGREENdoesnotfix80untilowneracceptance+parentrerun;TV2ownP2threadremainsitsdecision.
- TV2: defaultreviewerrequestedonbothnewPRs;97wholeprivatereview603PASSCOMMENTpublished.89/90/91/94humanformalreviewsstillseparate. Do notretakeunchangedProductpricebusinessvote ormergebatch.
- TV4: CODpayment/delivery/source/refund/settlementremainPENDING; nofakeuniquesettlementorStoreauthorizationfromfixture IDs. PR99requestedreviewers includeTV4atreadback;donotremoveothers'requests.
- **Productintegration** remainshumanTV5decision→TV2actual89merge→freshcheck→90merge→freshcheck→91merge→freshcheck.94separate. Historical640cannotreplacepostmergechecks; no actualpostmergegatepassedbythisQAwork.

- [x]4newFinancePGcases+twoStorefixture,focusedandcommittedregressionPASS,commit/pushPR99verified.
- [x]Read-onlyPRaudittool+27offlinecases+actual18PRmetadatasmoke,commit/pushPR100verified.
- [x]Newcodekeptoutside93;rawhash/evidence/checklist/sourceboundariesrecorded;noownerproductionedit orbusiness/flagchange.
- [ ]Independent99/100review/CI; TV1maywireQAtoolintoCIbyseparatereviewedtask,local27PASSisnotCI.
- [ ]Productactualsequentialintegration,currentAuth/Store/House/pair/C01E01/MD07/COD/refund/atomicsettlement/StoreFinancequery/live2StoreUAT;Issue5/12 OPEN/PARTIAL.

Phần coding độc lập có đủ căn cứ trong đợt này đã hoàn thành. Viết thêm test cho settlement/HTTP/provider chưa tồn tại sẽ áp đặt contract chưa duyệt hoặc lặp coverage. Ưu tiên tiếp theo sau các xác nhận liên quan là adapter current authority/provider và kiểm thử tích hợp thật của TV1–TV4 với QA TV5; chưa triển khai Finance production.
