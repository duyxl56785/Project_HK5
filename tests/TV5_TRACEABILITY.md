# TV5 — yêu cầu đến kiểm thử và bằng chứng tuần 1

Nguồn yêu cầu: Issue #5, yêu cầu người dùng và báo cáo V2 đã phân trang 55 trang. Kết quả hiện tại: **63 unit tests local**, xem `reports/evidence/w1-followup/test-cases.csv` để tra từng invocation, class, thời gian và trạng thái. Tên class viết ngắn dưới đây nằm trong package `productservice.review` hoặc `orderservice.finance`.

| ID | Yêu cầu / nguồn V2 | Test hiện có (method) | UAT / phụ thuộc | Bằng chứng và giới hạn |
|---|---|---|---|---|
| F15-01 | Buyer sở hữu sản phẩm DELIVERED — UC06 tr.26 | ReviewRulesTest.acceptsDeliveredPurchaseWithValidStars; rejectsUndeliveredUnpurchasedAndDuplicateReview | R01/R02 | PASS local fact guards; trusted event/DB BLOCKED D03 |
| F15-02 | Stars1–5 — F15 tr.19, A.7 tr.52 | ReviewRulesTest.rejectsInvalidStars; ReviewRulesBoundaryTest.rejectsMalformedRatingCollectionAndDirectInvalidRating | R02 | PASS validation số nguyên ở Java; JSON/HTTP NOT_RUN |
| F15-03 | Unique order/product — §7.2 tr.35 | ReviewRulesTest.rejectsUndeliveredUnpurchasedAndDuplicateReview | R03, D04 | PASS khi precheck true; DB race/retry NOT_RUN, không chứng minh uniqueness |
| F15-04 | Không tin buyer client — §3.3 tr.20 | ReviewRulesBoundaryTest.rejectsMissingBuyerOnBothSidesOfTrustedBoundary; neverNormalizesOpaqueIdentityToGrantOwnership; missingPurchaseIsRejectedInsteadOfAssumingDelivery | R02/A01 | PASS exact opaque refs và fail closed null; JWT provider NOT_RUN |
| F15-05 | Admin hide/unhide — UC17 tr.24 | ReviewRulesTest.adminCanHideUnhideAndRepeatWithoutChangingStars; deniedAdminPolicyCannotModerate | R04 | PASS policy stub/immutable stars; role JWT NOT_RUN |
| F15-06 | Kiểm quyền cả replay/thu hồi — §3.3 tr.20 | ReviewRulesBoundaryTest.eachModerationIncludingReplayRequiresFreshAccessCheck; authorizedModerationStillRejectsMissingStoredReview | R04/R06 | PASS local policy invocation; audit/persistence NOT_RUN |
| F15-07 | Hidden không tính public — UC06 tr.26 | ReviewRulesTest.hiddenReviewDoesNotAffectPublicSumOrCount; ReviewRulesBoundaryTest.visibilityChangesPublicCountAndAverageAndUnhideRestoresThem; hiddenValuesAndInputOrderDoNotBiasPublicAverage | R05 | PASS sum/count/empty; HTTP empty representation D02 |
| F15-08 | Average rounding — draft TV5 | ReviewRulesTest.roundsPublicAverageHalfUp | R05 | PASS helper 2 decimals HALF_UP; chưa phê duyệt format công khai |
| F15-09 | Reason≤500/comment≤1000 — A.7 tr.52 | Chưa có model/contract; không tạo fake DTO test | R06, D05 | BLOCKED validation/audit decision; gap được ghi V2-03 |
| F15-10 | DELIVERED event/reviewable_items — §8.2 tr.38, §9.3 tr.42 | Chưa có producer/consumer trong #20 | R07, D03/D04 | BLOCKED contract; không claim Kafka integration từ rules |
| F16-01 | Phí5%/House0, net — §3.4 tr.21 | FinanceRulesTest.snapshotsFivePercentWithHalfUp; houseStoreHasZeroCommission | F01 | PASS BigDecimal/large/zero; House flag chưa có verified adapter |
| F16-02 | Rounding và bảo toàn tiền — §7.2 tr.35 | FinanceRulesBoundaryTest.roundsCommissionAtHalfCentWithoutRoundingSubtotal; conservesMoneyForDeterministicRangeOfAmountsAndBothStoreTypes | F06, D06 | PASS helper 6006 tổ hợp bên trong 1 invocation; không cộng thành 6006 JUnit tests |
| F16-03 | Không mất precision/không âm — A.6 tr.51–52 | FinanceRulesTest.rejectsNegativeMoneyAndSilentPrecisionLoss; FinanceRulesBoundaryTest.rejectsMissingAmountInvalidScaleAndHousePrecisionLossToo; acceptsExactTrailingZeroRescalingWithoutLosingMoney | F06 | PASS local boundary0..4; schema numeric scale/precision chưa được chứng minh |
| F16-04 | Snapshot khi tạo order — §3.4 tr.21 | FinanceRulesBoundaryTest.computingNewStoreConfigurationDoesNotMutateEarlierSnapshot | F02, D06 | PASS kết quả immutable; settlement đọc snapshot đã lưu NOT_RUN |
| F16-05 | DELIVERED + COD PAID chưa settled — §3.4 tr.21 | FinanceRulesTest.rejectsEveryIneligibleSettlementCombination; permitsOnlyDeliveredPaidUnsettledOrder | F03/F04, D08 | PASS đủ8 tổ hợp; không side effect hoặc cập nhật COD |
| F16-06 | Store isolation — §3.3 tr.20 | FinanceRulesTest.deniesOtherStoreAndUnverifiedStore; FinanceRulesBoundaryTest.missingIdentityOnEitherSideCannotGrantFinanceAccess; ownershipUsesExactServerReferences | F05, D09 | PASS local guard; SQL/API/aggregate NOT_RUN |
| F16-07 | Hai Store tổng300000/phí5000/net295000 — UAT draft | FinanceRulesBoundaryTest.twoStoreArithmeticAndGuardsRemainIndependent | F01/C01 | PASS arithmetic+guard; không tạo Order/DB hoặc gọi Gateway |
| F16-08 | Unique settlement — §7.2 tr.35 | Guard alreadySettled trong F16-05/F16-07 | F04, D08 | Precheck PASS, DB uniqueness/concurrency NOT_RUN |
| F16-09 | Bốn nhóm doanh thu — F16 tr.19/UC12 tr.24 | Không tự đặt công thức để test | F07, D07 | BLOCKED thiếu định nghĩa nhóm/cutoff |
| QA-01 | UAT2 Store/permission/replay/cancel | Kịch bản tests/TV5_UAT.md | C01–C03/A01 | Thiết kế W1 hoàn tất; live NOT_RUN |
| QA-02 | Phân biệt nguồn kết quả — §11.1 tr.44–45 | Evidence ledger trong TV5_W1_FOLLOWUP.md | Không áp dụng | 63 hiện tại khác30/145 lượt trước và440/51/146/16 lịch sử V2 |
| DOC-01 | F02 thiếu UC, ảnh TODO, NFR | TV5_V2_RECONCILIATION.md V2-12..15 | TV1–TV4 | Đã đọc nguồn; sửa báo cáo/thu ảnh/load test chưa thực thi |

Không có consumer-driven contract suite mới: chưa có provider/consumer/payload được duyệt để xác minh. Các test bổ sung chỉ kiểm boundary của rules local hiện hữu, không tự chốt HTTP/Kafka/schema. Xem D01–D10 để biết việc cần chủ sở hữu xác nhận.

## Current-source Finance / diagnostic update — 18/10/2026

Bảng gốc phía trên là snapshot rules của lượt W1 ban đầu, không phải kết quả runtime mới hoặc phủ nhận
các bằng chứng PG bổ sung. [Checkpoint hiện tại](../reports/TV5_MONEY_DECISION_CHECKPOINT_2026-10-18.md)
và raw XML tại sourcec4d517e (parent owneree15851) phân biệt như sau:

| ID | Requirement / scope | Test hoặc quyết định → owner | Evidence và trạng thái |
|---|---|---|---|
| F16-10 | Existing NUMERIC19,2 exact storage bound | OrderAmountStorageBoundsTest,6methods; TV3 utility | [Raw unit XML](../reports/evidence/money-decision-20261018/current-run/test/TEST-com.projecthk5.orderservice.domain.OrderAmountStorageBoundsTest.xml):6PASS. Utility chưa wire, không duyệt VND/scale0/fee. |
| F16-11 | Reject parent overflow before Inventory/write | CheckoutMoneyPreflightRegressionTest.parentOverflowMustBeRejectedBeforeReserveOrOrderWrite; TV3 orchestration/TV5 policy | [Raw diagnostic XML](../reports/evidence/money-decision-20261018/current-run/fin06Regression/TEST-com.projecthk5.orderservice.finance.CheckoutMoneyPreflightRegressionTest.xml):FAIL. Reserve/write occur, DBrollback, NEEDS_RECONCILIATION. Provisional safe-preflight acceptance, fixBLOCKED. |
| F16-12 | No precision loss or response/replay money drift | unsupportedStoragePrecisionMustBeRejectedRatherThanChangingMoneyOnReplay; TV3/TV5 | Same diagnostic XML:FAIL,3.002→3.00. No assertion change; current storage fact, not currency choice. |
| F16-13 | Compatible committed placement/replay control | positiveControlPersistsExactAmountsAndReplayDoesNotReserveOrWriteAgain | Same XML:1PASS, mocked remote ports; no duplicate placement event, not settlement/revenue/liveE2E. |
| F16-14 | Explicit currency/bounds/rate/rounding/snapshots/adjustments/COD/freshness | [MD01–MD10 decision packet](../docs/TV5_M01_HUMAN_DECISION_PACKET.md); human TV5/business +TV1–TV4 own-scope | PENDING signature links; test fixtures, Source enum and module approval do not sign business. |
| F16-15 | Settlement unique/atomic/current Store authorization | Production implementation absent; TV5/TV4/TV3/TV2/TV1 | NOT_RUN/BLOCKED. Existing helper truth-table is not DB uniqueness, concurrency or scoped Finance query. |
| QA-03 | Preserve original failures and publication boundaries | Source/evidence byte comparisons; TV5 | [Derived scope metadata](../reports/evidence/money-decision-20261018/source-scope.json):original11hashes unchanged; new8artifacts separate, no hidden credentials/combined release total. |
| QA-04 | Correct TV4 IDs and lifecycle semantics | Static21legacy+21supplemental comparison, TV4 docs owner | [Derived static checks](../reports/evidence/money-decision-20261018/static-checks.json):ID/meaning/count checksPASS; all42 business casesNOT_RUN; C06/auth/event/risk unapproved. |

Full ordinary unit/tagged PG, Catalog and Inventory suites were not rerun in this checkpoint when their
source was unchanged; historical results retain original SHA/executor. Normal CI does not run the explicit
diagnostic or automatically run tagged PostgreSQL tasks. Do not mark F15/F16/Issue5/12 DONE from these rows.

## Finance preflight latest checkpoint — PR44,18/10/2026

[Báo cáo nguồn/lượt chạy mới](../reports/TV5_FINANCE_ACCEPTANCE_2026-10-18.md) supersedes current-status wording ở các snapshot cũ, không thay evidence lịch sử.

| ID | Requirement / scope | Test → owner | Evidence và trạng thái |
|---|---|---|---|
| F16-16 | Finance gate đóng trước reserve/write | FinanceCheckoutPreflightTest.closedFinanceGateCannotReserveStockOrWriteOrder →TV5 QA | PASS unit tại1311f8a; claim cleanup known, no external side effects; không deployment approval. |
| F16-17 | Consistent House của một Store | conflictingHouseFactsWithinOneStoreCannotSelectAnExemption →TV5 QA; TV2 source/TV3 consumer | PASS unit/mock; trusted House persistence/assignment vẫn PENDING. |
| F16-18 | Một Store không chứa hai Seller facts | matchingPerItemFactsWithTwoSellersCannotShareOneStoreOrder →TV5 QA | PASS orchestration/mock; không chứng minh Store Finance query/current identity. |
| F16-19 | Commission stage trong Checkout thật | currentServiceRoundsOnceForTheStoreRatherThanEachLine →TV5 QA | PASS unit,synthetic config20gross/1fee/19derivednet; MD04 business ký vẫn PENDING, không immutable net column. |
| F16-11/12 | Pre-reserve overflow và exact replay | Original CheckoutMoneyPreflightRegressionTest →TV3 runtime fix/TV5 acceptance | Chạy mới de1f5c6:2FAIL; positive control1PASS; XML/log gốc trong report. Order source/diagnostic unchanged đến1311f8a, không gọi run mới hoặc fixGREEN. |
| QA-05 | Latest owner delta và source provenance | c97a656/5ce0630 ordinary merges →TV5 QA | Latest1311f8a Cart8unit+Order81unit+Cart1PG PASS; Order16PG PASS ởde1f5c6 riêng. Không cộng các run thành release total. |
| F16-14/15 | Policy và production acceptance | MD01–MD10 +same-version signer/current source/settlement →TV1–TV5 | PENDING/BLOCKED; TV5 chỉ đề xuất, business signer chờ TV1/chủ dự án xác nhận; COD/settlement/Finance Store queries/liveE2E NOT_RUN. |

Raw evidence, timestamps, commands, hashes và publication limits: [manifest](../reports/evidence/finance-acceptance-20261018/manifest.json).

Post-merge checkpoint E: owner đã nhận44 tại ccfb38d; Cart.claim fix củaTV3 ởca639bb được chạy riêng trên0834f23:Cart9unit+1PG PASS/exit0/33s. Order source unchanged, không rerun hoặc cộng81unit/16PG/diagnostic vào lượt E. Evidence/docs còn thiếu đi qua branch TV5 mới sau khi44 đóng.

M01 follow-up sau owner merge45 tại5a3788b: **F16-14** trỏ [canonical packet §§8–11](../docs/TV5_M01_HUMAN_DECISION_PACKET.md),10MD ledger/version/choice/signer/evidence/dependency,
6tradeoff sections và partial Product price slice; tất cả MD businessPENDING, chưa unlock module mới. **F16-11/12** giữ original1PASS/2FAIL ởde1f5c6,
source unchanged tới5a3788b; [derived current audit](../reports/evidence/m01-owner-ledger-20261018.json) kiểm61hashes/byte identity, không là runtime rerun hay contract approval.

### Re-audit r3 — source8cc5ce4 thay đổi request guard

| Requirement | Test / owner / current evidence | Result và giới hạn |
|---|---|---|
| F16-11 precision/replay | Original unsupportedStoragePrecisionMustBeRejectedRatherThanChangingMoneyOnReplay →TV3fix; [currentXML](../reports/evidence/m01-reaudit-20261018/fin06Regression/TEST-com.projecthk5.orderservice.finance.CheckoutMoneyPreflightRegressionTest.xml) | **FAIL mới**:3.002→3.00, mockreserve/write xảyra; không dùng kếtquả cũ thayrun này |
| F16-12 parentoverflow | Original parentOverflowMustBeRejectedBeforeReserveOrOrderWrite →TV3preflight | **FAIL mới**, DBrollbackPASS riêng; chưa no-sideeffects trướcRESERVE |
| F16 replay positivecontrol | Original positiveControlPersistsExactAmountsAndReplayDoesNotReserveOrWriteAgain →TV5QA | **PASS mới**,300.00/fee5.00/oneorder+outbox; mockremoteports, không settlement/E2E |
| QA-05 ownerrequestguard | CheckoutRulesTest/CheckoutServiceTest officialJUnit tại8cc5ce4 →TV3 | Included **83Order unitPASS**,16PGPASS riêng; không đếm17harnessassertions như17JUnitcases |
| F16-14 sign-off | Packet§12, TV3engineering MD05/06/08 reply +[audit](../reports/TV5_M01_REAUDIT_2026-10-18.md) →business/TV1–TV5 | All10MDPENDING, no productionunlock; Productpricepartialgate không bị buộcchờMD10 |
| F16-15 productiongate | CurrenttrustedCOD, atomicuniquesettlement, currentStoreFinance, livetwoStore →owners | NOT_RUN/BLOCKED; pureguards/PGfixtures không đủ |

Same-run taskcounts/rawSHA256/commands và22originalassets: [manifest](../reports/evidence/m01-reaudit-20261018/manifest.json).
83unit/16PG/diagnostic1P2F chỉ thuộc8cc5ce4, không là release total cộng lịch sử. PR42/46 integrated, newesthead chưa sửaFIN01/06.

### Current REVIEW-AUDIT-20261018-r4 — be1d5b6

| Requirement / review | Test / owner / evidence | Current disposition |
|---|---|---|
|F16-11 FIN01 /TV1CR5563289579|Originalprecision/replaycase; TV3fix5f2ada8 →actualTV5be1 PGdiagnostic|**FIXED_VERIFIED**, pre-reserve rejection, no changedassertion |
|F16-12 FIN06 /sameCR|Originalparentoverflowcase +positivecontrol →[XML](../reports/evidence/project-review-20261018/fin06Regression/TEST-com.projecthk5.orderservice.finance.CheckoutMoneyPreflightRegressionTest.xml)|**3PASS/0FAIL**, raw8ccREDretained; noInventory/writer/order/outboxforknowninvalid |
|QA JWT/replay/scanner resolvedthreads28|88unit +16PG actualbe1; [manifest](../reports/evidence/project-review-20261018/manifest.json)|**FIXED_VERIFIED**, no regressionreproduced; mockremoteports notliveintegration |
|QA allreviewstates|33PR/119events/16threads/16Issues; [audit](../reports/TV5_PROJECT_REVIEW_AUDIT_2026-10-18.md)|0unresolved;28TV1CR/39TV5CRneedhumanupdateddecision;19alreadyapprovedmerged;PR49mớirefresh |
|F16-14 partialprice M01|e597dafMD01/02/unitcapMD03 +humanTV5scopeagreement, TV2technicalcompatibility|PENDING BUSINESS APPROVAL; quantity1–1000technicalfact; noMD04–10automaticpricegate |
|QA Security/S01|TV1PR48v1+TV2/TV3ownscope; [SP01–14QAplan](TV5_SERVICE_PAIR_QA_MATRIX.md)|Designconfirmationrecorded; runtimepairs/currentStore/COD/Financequeries NOT_RUN/BLOCKED |

88/16/3belongonlybe1run; no additionofold8cccounts/TV2reported117. Publication22rawhashes/commands/time/exit inmanifest; productionFinance/fullM01notDONE.
Delta**c181675**:16targetedCheckoutServiceunit+3originalPGdiagnosticPASS, khôngfullunit/taggedPG rerun.
PR49**5cf4d75**4PASS/1FAILwrappertest →ownerfix**cf60281**5PASS/0FAILsame2securityclasses; **FIXED_VERIFIED testharness**,
SP01–04chỉpartialunitcoverage, actualHTTP/currentactor/rotation/provenance/PG/E2E NOT_RUN. Manifestgiờ35rawassets, táchperSHAkhôngcộngtotals.
