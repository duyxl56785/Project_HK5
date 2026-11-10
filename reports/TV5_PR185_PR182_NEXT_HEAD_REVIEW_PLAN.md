# TV5 — kế hoạch kiểm tra HEAD mới của PR #185 và #182

**Kết thúc kế hoạch tại 10/11/2026:** GitHub xác nhận #185 MERGED 07/11/2026, #182 MERGED 08/11/2026; #183/#184 cũng MERGED. Nội dung bên dưới là kế hoạch lịch sử lập trước khi các PR được tích hợp, không còn là danh sách PR đang chờ review. TV5 đã chạy tiền kiểm Return–Settlement trên `main@78de5c08205b41986a5204a8150a9ef810507847`: [24 PostgreSQL/MockMvc PASS](../reports/minh-chung-kiem-thu/tv5-main-78de5c-return-preflight/). Kết quả này không xác nhận riêng browser fixture #185, UNKNOWN hay final UAT #18.

Ngày lập: 07/11/2026. Đây là **kế hoạch**, không phải kết quả chạy test hoặc formal review. Lần đọc GitHub gần nhất ghi `main@868fd8deb7abd789cea93bbd71fc6f8e3d641a71`, #185 `d2929a4dd8b9dea9ca9609e126deda15aa7202c5` và #182 `539cf3bcc9c523ec71bc06bd3810baf74334b942`. Quyền fetch đang bị bộ duyệt tự động từ chối vì hết hạn mức; local chỉ có #185 `e0eb9aadf205c24c8a3a4653fea6ae126689e66d` và #182 `c187e5bddce4aca288a56fe2d7e846d48b6ebab8`. Không suy source mới từ metadata GitHub.

## Tiền kiểm chung khi quyền trở lại

1. Kiểm tra `git status` từng worktree, bảo toàn `.vs/`, `frontend/package-lock.json` và `reports/tv5-m01-local/`. Fetch main và hai PR vào ref riêng; đối chiếu SHA fetch với GitHub API, base, state, files changed, commits, review threads và checks đúng HEAD. Nếu HEAD đổi lần nữa, dùng SHA mới làm mốc.
2. Đọc `AGENTS.md`, `CONTRIBUTING.md`, `docs/OWNERSHIP.md`, `.github/CODEOWNERS` tại base/head có hiệu lực. Chỉ xét diff từ mốc TV5 đã review tới HEAD mới và diff PR với base mới; kiểm phần source giao nhau và migration khi main đổi.
3. Kiểm hosted CI đúng SHA; raw artifact mới là bằng chứng CI, không thay independent test. Chọn project/DB Testcontainers riêng; không dùng lại hoặc xóa shared volume. Không bật `approved-finance-policy`, `approved-inventory-pair` hoặc UNKNOWN fixture.
4. Ghi command, exit code, JUnit/Playwright artifact, timestamp, full source SHA và SHA256. Redact token, địa chỉ, mật khẩu trước khi xuất bản; giữ raw byte của XML/log đã được phép công bố. Không cộng lượt branch với main thành một release total.

## #185 — Customer Return browser E2E của TV2

Finding TV5 ở `e0eb9aa`: `return-request.spec.ts` tự tạo order nhưng sau đó dựa vào trạng thái được chuẩn bị bởi `order-journey.spec.ts`; khi chạy riêng, Seller có thể không thấy order hoặc không hoàn tất transition. HEAD mới phải được đọc thực tế để xác nhận fixture đã độc lập, không chỉ đổi tên case hoặc assertion.

| Ca / lệnh dự kiến sau fetch | Điều phải thấy | Trạng thái hiện tại |
|---|---|---|
| `cd frontend; npx playwright test e2e/return-request.spec.ts --reporter=list` trên stack demo cô lập | Từ seed giả có thể tạo Customer order, Seller chuyển đúng SellerOrder của order đó tới DELIVERED, Customer tạo Return 201, replay cùng key/body 200 cùng `requestId`, đổi body 409, danh sách có đúng một request. Không phụ thuộc spec khác. | NOT_RUN tại HEAD mới |
| `cd frontend; npm run e2e -- --reporter=list` sau ca chạy riêng | Không có thứ tự spec ngầm, duplicate seed hoặc dữ liệu còn từ lần trước; mọi spec chạy độc lập. | NOT_RUN tại HEAD mới |
| Kiểm `return-request.spec.ts`, `order-journey.spec.ts`, `support.ts` và scripts seed | Fixture/cleanup không dùng token thật, không ghi Authorization vào log/artifact; selector buộc đúng order/Store; thất bại trả FAIL thay vì skip hoặc timeout bị coi PASS. | NOT_VERIFIED tại HEAD mới |
| Review HTTP/DB scope | Browser qua Gateway/Order trên demo chỉ chứng minh flow đó; không suy ra restock, refund, UNKNOWN hay final hai Store UAT. | Giới hạn bắt buộc |

Chỉ formal review sau khi diff mới và kết quả scoped thỏa mãn finding. Nếu isolated spec vẫn FAIL, gửi finding tại dòng mới cùng trace và owner TV2; nếu environment không khởi động, ghi BLOCKED_ENV, không quy thành lỗi PR.

## #182 — Seller Return/receipt/settlement của TV4

Finding TV5 ở `c187e5b`: migration V7 `ck_return_receipt_reference` chỉ kiểm `IS NOT NULL`, nên SQL có thể ghi `status='RECEIVED'` cùng `receipt_reference=''` hoặc toàn khoảng trắng. Tại mốc đó branch còn conflict với main và chưa chắc tiếp nhận UI/Gateway #183. GitHub gần nhất báo #182 hết conflict; phải xác minh diff và test mới chứ không dùng check cũ.

| Ca / lệnh dự kiến sau fetch | Điều phải thấy | Trạng thái hiện tại |
|---|---|---|
| Đọc V7, migration kế tiếp, entity/service và review thread | DB chặn cả NULL, empty, whitespace khi RECEIVED; cho phép nonblank dài hợp lệ. Nếu V7 đã phát hành trên main, sửa bằng migration mới, không sửa migration đã áp dụng. | NOT_VERIFIED tại HEAD mới |
| `./backend/gradlew.bat -p backend :order-service:postgresIntegrationTest --tests '*FulfillmentPostgres16IT' --rerun-tasks --no-daemon --console=plain` | PostgreSQL thật kiểm direct SQL invalid receipt bị CHECK từ chối, transaction rollback, valid RECEIVED và replay; số test lấy từ XML của HEAD mới. | NOT_RUN tại HEAD mới |
| `./backend/gradlew.bat -p backend :order-service:test --tests '*ReturnRequestServiceRulesTest' --rerun-tasks --no-daemon --console=plain` | Rule reason/receipt/status boundary và idempotent action đạt, không thay PG constraint. | NOT_RUN tại HEAD mới |
| Đối chiếu #183 và base main | Admin read-only, Seller routes, Gateway authorization và UI consumer có cùng status/action/DTO; xác minh #183 MERGED hay CLOSED qua GitHub, không suy từ danh sách PR mở. | NOT_VERIFIED |
| Store/Finance negative cases | Seller Store A không tác động Return Store B; SUSPENDED chỉ tiếp tục đơn hợp lệ, không xem Finance; REQUESTED/APPROVED/RECEIVED chặn settlement, REJECTED bỏ chặn, COD collected vẫn ghi nhận đúng. | Cần scoped rerun nếu source liên quan đổi |

Sau test, chỉ đề xuất APPROVE trong phạm vi Seller Return/Finance guard có evidence. Restock INCREASE, refund/reversal, live Gateway và final UAT vẫn là gate riêng. Review TV3 ở HEAD #182 không thay TV5/TV2 khi code giao miền còn thay đổi.

## Quyết định và handoff

- #185: TV2 sửa fixture, cung cấp raw Playwright artifact và SHA; TV5 kiểm độc lập ở HEAD mới.
- #182: TV4 chịu migration/receipt/Store workflow; TV2 kiểm Gateway/UI và migration owner; TV5 kiểm Return–Settlement/Finance; TV3 xác nhận contract/consumer nếu status hoặc SellerOrder DTO thay đổi.
- #183/#184: xác minh `merged_at`/`closed_at`, merge commit và main ancestry trước khi ghi MERGED. #179 cần TV2 re-review HEAD `2a2c4e4`; #186 đã có APPROVED của TV2 tại HEAD `2d13167`, không request lại chỉ vì danh sách reviewer trống. Kiểm lại check runs/branch protection khi quyền được phục hồi.
- Không tick #5/#12/#18/#157/#162 từ kế hoạch này. Final UAT #18 và Finance production giữ BLOCKED.
