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
