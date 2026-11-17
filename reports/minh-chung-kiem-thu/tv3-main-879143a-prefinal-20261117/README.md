# TV3 — Exact-main pre-final verification and submission handoff (17/11/2026)

**Application source SHA:** `879143a1f081da4644ebc29c0950069429731f0f` (`main` lúc lập bộ kiểm).  
**Chủ thể ghi chú:** TV3 — Cart / Checkout COD / Order / SellerOrder / Inventory Journal / Voucher C1 / Promotion.  
**Cấp bằng chứng:** PRE-FINAL — không tự tuyên bố frozen SHA, UAT final, owner sign-off, clean-clone Windows hay ảnh Word chính thức.

## 1. Những bằng chứng đã có và SHA tương ứng

| Bằng chứng | SHA / URL | Kết luận chính xác |
| --- | --- | --- |
| Owner Windows PS5.1 rehearsal Checkout | [TV2 #160 comment 6940668725](https://github.com/duyxl56785/Project_HK5/issues/160#issuecomment-6940668725), **app 240d0280bdde66f7d9055e24ded75f1e9417edfb**; test handoff a69cbba4 | **PASS** runner, Playwright 1/1, PostgreSQL oracle, validator 10 file; ảnh rehearsal **không** đưa vào Word |
| CI Checkout độc lập trên Ubuntu | [run 44028551726](https://github.com/duyxl56785/Project_HK5/actions/runs/44028551726), **app 240d028…** | PASS, PostgreSQL READ ONLY, validator 8 file, không phải final |
| Full pre-final main CI | [Run 44052552623](https://github.com/duyxl56785/Project_HK5/actions/runs/44052552623), **app 879143a1…** | **Backend readiness PASS** (Gradle BUILD SUCCESSFUL, 6m46s), **frontend 178/178 PASS**, typecheck/build PASS. **E2E 23/24** do custom CI project name bị `promotion-pricing.spec.ts` guard từ chối; **overall workflow FAIL**, không được đánh dấu SUCCESS. |
| Corrected full-browser isolated CI | [Run 44054213307](https://github.com/duyxl56785/Project_HK5/actions/runs/44054213307), **app 879143a1…** | **24/24 Playwright PASS**, exit 0, unexpected=0, skipped=0, flaky=0, clean checkout, disposable stack/seed and cleanup PASS. Compose project `hk5-promo-test-tv3-main-879143a` được allowlist test; không sửa app hoặc nới lỏng guard. |
| Independent diagnostic of prior 23/24 | [Run 44054009377](https://github.com/duyxl56785/Project_HK5/actions/runs/44054009377) | Test lỗi duy nhất `promotion-pricing.spec.ts` (`Seller clearance flows through Catalog and Checkout into Order price and fee`), category ASSERTION_EXPECTED_RECEIVED do project name ngoài allowlist. Diagnostic là **FAIL** và được giữ làm root-cause record; corrected 24/24 là lượt chạy mới trên DB/stack riêng. |
| TV3 Checkout report & figure mapping | [CHECKOUT.md](https://github.com/duyxl56785/Project_HK5/blob/879143a1f081da4644ebc29c0950069429731f0f/docs/project-report/tv3/CHECKOUT.md), #188/#210 merged | Nội dung nguồn đã TV2 review, **chưa phải file Word bản nộp** |
| TV3 report submission handoff | [Issue #162 comment 6940782803](https://github.com/duyxl56785/Project_HK5/issues/162#issuecomment-6940782803) | Chỉ nguồn ghép báo cáo, chưa có các ảnh chính thức |
| TV5 evidence draft | [PR #204](https://github.com/duyxl56785/Project_HK5/pull/204) HEAD 52c4036 | Draft, **manifest SHA256 chưa hợp lệ trên HEAD** và matrix có tên file cũ; không dùng làm final |

## 2. Case → test / oracle → bằng chứng / rủi ro còn lại

| Case | Automated coverage available on application main | What is actually proven | Release/UAT status |
| --- | --- | --- | --- |
| UAT-TV3-01 Cart 2 Store / COD one root + 2 seller orders | `frontend/e2e/two-store-order.spec.ts`; isolated TV3 spec | Owner Windows + CI at app 240d028 confirm one root/two children and Stock/RESERVE via PG read-only | **PASS_PRE_FINAL**; NOT_RUN_FROZEN_SHA |
| UAT-TV3-02 Idempotency replay/mismatch | TV3 rehearsal `tv3-demo-rehearsal.spec.ts`; Order PG regression | Owner at 240d028: same-key replay HTTP200 same root; shipping mismatch HTTP409; stock and reserve unchanged | **PASS_PRE_FINAL**; NOT_RUN_FROZEN_SHA |
| UAT-TV3-03 PRICE_CHANGED / quote mismatch | Cart/Checkout source and backend regression | Code/backend suites available; dedicated frozen browser + SQL oracle **not yet evidenced** | **NOT_RUN_FROZEN_SHA** |
| UAT-TV3-04 Sale snapshot & 5% fee | `frontend/e2e/promotion-pricing.spec.ts` (clearance); M01 demo policy | Existing browser suite tests promotional order price/fee in scope; production Finance not approved | **PASS_EXACT_MAIN_BROWSER_879143A / NOT_RUN_FROZEN_SHA** |
| UAT-TV3-05 Cancel/reject/dispatch RELEASE/CONSUME once | `frontend/e2e/order-journey.spec.ts`; Order/Inventory PG tests | Owner Checkout rehearsal **does not cover** release/consume lifecycle | **PARTIAL / NOT_RUN_FROZEN_SHA** |
| UAT-TV3-06 Voucher Store C1 | `frontend/e2e/voucher-c1.spec.ts`; PR #200 merged | Seller/Admin voucher publish/request/approve, ownership; **not checkout discount redemption C2** | **EXACT_MAIN_BROWSER_PASS_879143A / NOT_RUN_FROZEN_SHA** |
| UAT-TV3-07 Flash/Super/Clearance | PR #202; `frontend/e2e/promotion-pricing.spec.ts` | Sale price and clearance checked in existing suite; specific Super campaign or clock boundary not inferred PASS | **PARTIAL / NOT_RUN_FROZEN_SHA** |
| Auth, Customer/Seller/Admin ownership | `frontend/e2e/access.spec.ts`, seller-onboarding and dedicated owner rehearsal | 24/24 browser suite at exact main + owner cross-store denial at 240d028; limited to seeded actors | **EXACT_MAIN_BROWSER_PASS / FINAL_UAT_PENDING** |
| Return request / seller workflow | `frontend/e2e/return-request.spec.ts`, `seller-return.spec.ts`, `return-access.spec.ts` | Existing browser suite 24/24 PASS at exact main includes Return UI/API/access; **does not prove RET-05 restock** | **BROWSER_PASS_EXACT_MAIN / RESTOCK_BLOCKED** |
| UNKNOWN fault U11/U12 | Contract/owner decisions on #18/#154 | Fixture approval and provider oracle unresolved | **BLOCKED_DESIGN / NOT_RUN** |
| RET-05 restock | TV4/TV5 contract | No authorized real restock exactly-once oracle | **BLOCKED_DESIGN** |
| Production Finance / real bank payout | M01 demo and #162 decision | COD/5% demo calculation is **not actual money transfer** | **NOT_PRODUCTION_APPROVED** |

*Live replay proof from owner cannot be reused as frozen-main evidence merely because main was documentation-only after 240d028. Every claim must retain its original exact application SHA.*

## 3. Bản nộp và hình ảnh do TV2/TV5 nghiệm thu

Theo [quyết định của TV2](https://github.com/duyxl56785/Project_HK5/issues/162#issuecomment-6907759709), bản nộp dùng **`Bao_cao_Smart_ECommerce_ban_nop.docx`** do TV2 ghép, không phải DOCX TV5 draft. Hình 6.1 home, 6.2 mobile 390px, 6.3 Seller product, **6.4 Admin Seller approval**, **6.5 Cart đúng hai Store**, **6.6 one root Order/two SellerOrders**, 6.7 Seller ownership, **6.8 Finance 5% COD / “đã ghi nhận đối soát”** chứ không ghi “tiền đã trả”. Không dùng ảnh REHEARSAL, mock, Cart trống, ảnh ba Store, hoặc ảnh sai SHA làm ảnh nộp.

**Tài liệu Word cuối hiện nằm ngoài GitHub và không có trong phiên làm việc này**; không thể kiểm pixel/đóng gói/nộp thay TV2 nếu chưa có bản source. TV5 cần duyệt masking/PII + receipt SQL, source SHA và manifest. Riêng PR #204 phải giải quyết lỗi checksum và path theo TV2 review trước khi dùng làm evidence.

## 4. Kiểm soát an toàn / trạng thái

- Không tự thêm app feature hoặc force-push; không sửa `main`, branch TV5 hay cờ production.
- Các stack GitHub Actions dùng runner disposable, synthetic accounts, `COMPOSE_PROJECT_NAME` riêng, secrets random, volume dọn sau lượt test; CI không chạm máy Windows của nhóm.
- Log frontend/browser có thể chứa data runtime: chỉ public **stats redacted** và SHA/checks; raw logs/headers/cookie/token/PII **không** upload.
- CI exact-main ở nhánh `ci/tv3-main-879143a-preflight-20261117` kiểm app theo `ref:879143a1…` trên clone sạch, **không sửa application source**. Corrected isolated run **24/24 PASS** với `--workers=1 --retries=0`; raw report chỉ còn trong runner ephemeral, công bố sanitized stats + test title/diagnostic và artifacts redacted; không được coi là owner Windows clone sạch hoặc UAT frozen SHA.
- Mọi yêu cầu đóng #160/#18/#158/#162 phải dựa vào frozen SHA, E2E/clone và owner sign-off thật. Chưa hoàn tất thì giữ OPEN.
