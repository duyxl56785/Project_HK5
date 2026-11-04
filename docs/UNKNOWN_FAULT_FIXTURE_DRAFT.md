# Dự thảo fixture hai ca UNKNOWN

**Trạng thái:** DỰ THẢO của TV2, chưa chạy. Cần TV3, TV4, TV5 duyệt từng ca trước khi làm. Dựa trên [đề xuất của TV4](https://github.com/duyxl56785/Project_HK5/pull/147#issuecomment-6580170816) và [handoff của TV3](https://github.com/duyxl56785/Project_HK5/issues/18#issuecomment-6581596647). Refs #18.

**Cập nhật 05/11/2026:** các đề xuất stack/receipt bên dưới là lịch sử, không phải hướng dẫn chạy. Packet cuối tài liệu thay thế chúng cho quyết định thực thi; hiện **EXECUTION_AUTHORIZATION=NOT_GRANTED**. Không dùng tên project/cổng cũ hoặc `down -v` từ bảng dự thảo.

Mục tiêu là chứng minh đúng hành vi đã có: khi không biết Inventory đã xử lý hay chưa, Order giữ UNKNOWN, không tạo operation ID mới, không tự RELEASE, Cart vẫn bị khóa. Không dùng phản hồi giả, 409 giả, hay sửa DB để ép PASS. Nếu không tạo được lỗi thật thì ghi `BLOCKED_DESIGN`.

## Đề xuất stack

| Mục | Đề xuất |
|---|---|
| Compose project | `tv3-live-unknown-<ngày>`, volume riêng, `down -v` sau khi giữ evidence |
| Cổng | `127.0.0.1:5173`, `127.0.0.1:18080`, chỉ chạy khi không có stack nào khác |
| Source | SHA `main` tại lúc chạy, ghi vào evidence |
| Dữ liệu | Tài khoản `@hk5.local` riêng, không có dữ liệu thật |
| Finance | Không ghi COD, không settle |

## Điểm chèn lỗi

`order-service` gọi Inventory qua `INVENTORY_INTERNAL_URL`. Có thể đặt một proxy TCP giữa hai service bằng file compose override chỉ dùng cho fixture, trỏ biến này sang proxy. Không đổi code production, không thêm API lỗi. Proxy là phụ thuộc mới của fixture, TV4 cần duyệt.

## Ca 1: timeout, mất phản hồi

1. Bật proxy chế độ chuyển request tới Inventory, nhưng bỏ phản hồi về.
2. Customer checkout. Inventory nhận và commit RESERVE, Order timeout.
3. Kiểm tra: journal `UNKNOWN`, attempt chờ đối soát, tồn đã giữ đúng một lần, Cart còn khóa, không có RELEASE.
4. Giữ lỗi đang bật, replay cùng key và body. Kiểm tra ID, tồn, Cart không đổi.

Receipt chấp nhận của provider là dòng `operation_id` trong `inventory_db`, đọc bằng giao dịch chỉ đọc.

## Ca 2: claim-conflict

`InventoryOperationTransport` xếp 409 claim-conflict vào UNKNOWN. Phía Inventory, `InventoryOperationService` chỉ trả `IDEMPOTENCY_CLAIM_CONFLICT` khi `claimOperationId` trả 0 mà `findById` không thấy dòng nào. Nếu thấy dòng thì đi nhánh replay hoặc từ chối.

**Chưa có cách tạo thật, đề xuất ghi `BLOCKED_DESIGN` cho tới khi TV4 có cơ chế.** TV3 yêu cầu claim cạnh tranh thật, không sửa DB. Khoảng thời gian gây ra nhánh này rất hẹp, và request phải khóa dòng hàng trước khi claim. Proxy không chạm được vào nhánh đó. TV4 cần xác nhận có tạo được không mà không thêm code lỗi vào runtime.

## Evidence

Cho từng ca, theo yêu cầu của `frontend/src/features/checkout/acceptance/README.md`:

- File case riêng cho `TV3_E2E_CLAIM_CASE_FILE`, `TV3_E2E_TIMEOUT_CASE_FILE`, gồm `kind`, `attemptId`, `operationId`, `key`, `body`, `lines`, `evidenceFile`, `evidenceSha256`, `faultStillActive`, `settledBeforeReplay`. File này không commit nếu chứa thông tin bí mật.
- Trước và sau: attempt, draft, journal, outbox của Order. Operations, reservation, available và reserved của Inventory. Cart và fingerprint.
- SHA source, SHA fixture, SHA256 đầy đủ. Bỏ PII, key, secret.

## Cần duyệt

| Việc | Người duyệt | Hiện trạng |
|---|---|---|
| Duyệt QA từng fixture | TV5 | Chờ |
| Cơ chế proxy và claim thật | TV4 | Chờ |
| Stack, cổng, runner, source đúng SHA | TV2 | Chờ bản cuối của tài liệu này |
| Giao thức | TV3 | Đã chấp nhận giao thức, chưa duyệt chạy |

Nhóm cũng cần chốt hai ca này là blocker của release hay backlog công khai cho bản demo.

## Packet quyết định thực thi — 05/11/2026

**LOST_RESPONSE_LIVE=NOT_RUN/BLOCKED; CLAIM_CONFLICT=BLOCKED_DESIGN.** Packet này không cấp quyền chạy, không thay contract, không chứa credential hay tuple giả. Chuẩn bị mechanics PASS không đồng nghĩa provider COMMIT hoặc UAT PASS.

Kiểm tra chuẩn bị trong lượt 05/11: local Git merge `9948861f258b2ebb3e4b86a82476b4e4babef8de` (#170 `2ddc0f3` + #175 `4ca0635`), tree `79b36d0acab583b15039731241241018344417ff`; chạy cùng lượt `node --test --test-reporter=tap scripts/qa/tv4-unknown/lost-response.test.mjs scripts/qa/unknown/prepare.test.mjs`: **19 PASS / 0 FAIL / 0 SKIP**, exit 0. Chỉ loopback giả/file tạm, không Docker/provider thật. Cây ghép không được push và không thay HEAD #175; không suy rằng đã đủ parent acceptance.

### Source, stack và runner

| Mục | Giá trị đã đối chiếu / gate còn thiếu |
|---|---|
| Baseline đã đọc | `main@c1ac4d4e5dd450d5d3fc3d6a8267cb5b45f32d48`; chưa chọn SHA nghiệm thu cuối. Trước chạy phải ghi full SHA main thực tế, tree SHA và CI tương ứng; không dùng synthetic merge/branch candidate làm main. |
| Proxy #170 | `2ddc0f3d5283d6a1cc985fa203c8d0d380bc164a`: one-shot per-handler, 12 Node PASS; vẫn Draft/CHANGES_REQUESTED, chưa có TV4 xác nhận và TV5 nghiệm thu revision này. Không restart/recreate handler để mở lại cùng ca. |
| Preparation #175 | `4ca06352368f2cf610bde5fc1dd3cc9886e08c28`: TV3/TV5 APPROVED preparation-only, còn TV4. Chưa chứa parent fix #170 trong HEAD; chỉ sync sau parent acceptance, giữ evidence cũ và chạy lại cả 19 test. |
| Thông số stack đã chọn | Theo [TV2 #152](https://github.com/duyxl56785/Project_HK5/pull/152#issuecomment-6619444913): `hk5-unknown-YYYYMMDDHHmmss`, Gateway `127.0.0.1:18082`, frontend `127.0.0.1:5175`, volume/secret mới riêng, chỉ dữ liệu giả. Không cần quyết định lại các giá trị này; exact run binding vẫn chưa có. |
| Upstream | Current Order wire là `POST /api/inventory/operations`; target loopback trong namespace Inventory, sidecar `network_mode: service:inventory-service`. Path `/internal/inventory/operations` trong unit harness là giả, không phải bằng chứng route production. TV4 phải xác nhận endpoint/pair/scope và provider fingerprint thực tế. |
| Executor/teardown | **MISSING**: #175 chỉ sinh cấu hình default-OFF, không thực thi hay dọn. Start/Stop UAT hiện chỉ nhận `hk5-uat-*` và pair OFF, không tương thích cấu hình UNKNOWN đã chọn. Không nới script chung hoặc sửa plan thành approved để vượt gate. Cần executor/stop riêng được review, nhận diện đúng project bằng labels và giữ log; không xóa volume khi chưa có quyền riêng. |
| Gates | Hiện Inventory/Finance/retention OFF. Chỉ một quyết định thực thi riêng cho đúng ca/source/artifact mới có thể cho pair opt-in trong project cô lập; Finance/retention tiếp tục OFF. Packet này không bật cờ nào. |

### Artifact tuple bất biến — bắt buộc có trước RESERVE đầu tiên

TV3 đã xác định [tuple và oracle](https://github.com/duyxl56785/Project_HK5/pull/170#pullrequestreview-5754561736); không yêu cầu viết lại thiết kế. Các **giá trị của một run cụ thể** dưới đây còn NOT_PROVIDED, không được tự bịa hoặc lấy lại từ request replay:

| Nhóm | Nội dung cần đóng băng, owner xác minh |
|---|---|
| Checkout/Cart | Customer, original Idempotency-Key, body + normalized shipping + expectedItems + revision + RequestFingerprint, CheckoutAttempt/root Order UUID, Cart claim/revision/fingerprint; TV3 đối chiếu bản ghi durable. |
| Store/lines | Frozen SellerOrder UUID, verified Store/Seller/owner mapping, Product UUID → exact SKU và sorted signed-64-bit quantity; không suy Product ID là SKU. TV2/TV3/TV4 xác minh cùng tuple. |
| Inventory T1/T2 | Original operationId, type RESERVE, canonical lines và provider payload fingerprint, journal state/send_attempts (send generation). TV3 chứng minh intent đã commit trước send; timeout/retry không tạo ID hoặc generation khác tùy ý. |
| V3 owner binding | Current main wire chỉ có operationId/type/items, **không có** reservationId/storeId/sellerId/sellerOrderId. Phải có owner-map/receipt production đã được chấp thuận hoặc waiver T1 aggregate riêng do TV3/TV4 duyệt; không nhận Java/SQL candidate là V3 proof. |
| Integrity/privacy | Full source/tree SHA, helper/proxy/executor/overlay/plan SHA256 và private tuple artifact digest. Không publish raw token, key, shipping hay credential; dùng reference/digest đã được owner kiểm tra. Hash chỉ ràng buộc nội dung, không thay chữ ký/phê duyệt. |

### Oracle trước/sau và hai lần replay

| Mốc | Evidence bắt buộc / expected state |
|---|---|
| Trước send | Baseline read-only provider stock + Order attempt/draft/journal/outbox + Cart claim. Ghi original IDs, canonical fingerprint/lines, generations, nguồn DB và nguồn quyền đọc; không ghi token. |
| Provider COMMIT | Quan sát từ connection/transaction đọc riêng sau commit: operationId + type + canonical payload fingerprint + lines + committed status, reservation/owner nếu contract áp dụng, available/reserved trước/sau đúng một RESERVE. Chỉ một operation row, HTTP 200, body SHA proxy hay response bị mất đều **không đủ**. TV4 ký xác nhận provider receipt đã bind đúng tuple. |
| Sau mất response | Journal UNKNOWN, attempt NEEDS_RECONCILIATION, immutable draft được giữ, Cart còn claimed; không có placed Order/outbox mới hoặc RELEASE/CONSUME/operationId khác. Proxy timeout/callback failure/lookup vắng không chứng minh no-commit; dừng và reconcile, không bù trừ mù. |
| Replay 1 và 2 | Cùng customer/key/body/shipping đã đóng băng: API trả 409 `CHECKOUT_REQUIRES_RECONCILIATION` hoặc 503 `CHECKOUT_OUTCOME_UNKNOWN_RECONCILIATION_REQUIRED` theo nhánh hiện có. Đối chiếu lại tất cả IDs/fingerprint/stock/Cart/Order/outbox, không movement thứ hai; late T2 không ghi đè generation đã fenced. |
| Không đủ bằng chứng | Ghi actual/exit/log hash và FAIL hoặc NOT_VERIFIED; giữ quarantine. Không restart proxy, đổi ID/body, thêm retry tự động, fake409/500, xóa row hoặc RELEASE để khiến test PASS. |

### Approval riêng từng ca và kết thúc run

Mỗi decision phải có người có thẩm quyền tự xác nhận, comment URL, timestamp, case ID, exact source/artifact digests và phạm vi. Không điền thay người khác hoặc chuyển preparation APPROVED thành execution approval.

| Gate | Lost-response | Claim-conflict |
|---|---|---|
| TV2 exact source/project/ports/network/secret isolation/executor + stop scope | PENDING run-bound artifact; thông số stack đã chọn giữ nguyên | PENDING |
| TV3 immutable tuple + two-retry oracle + V3 proof hoặc waiver T1 hẹp | PENDING actual artifact/waiver; thiết kế đã có | PENDING contention design |
| TV4 provider/pair + canonical fingerprint/stock/COMMIT receipt + one-shot lifecycle | PENDING current revision/receipt | BLOCKED_DESIGN: chưa có generator tranh chấp thật được chấp thuận |
| TV5 QA/privacy/evidence và permission thực thi đúng ca | NOT_GRANTED | NOT_GRANTED |
| Teardown/handoff | MISSING reviewed executor/stop. Sau evidence, chỉ stop đúng container có project label; giữ private volumes và dữ liệu cho tới khi có quyền xóa riêng. Không prune/reset hoặc dùng project demo chung. | Không chạy |

Khi mọi gate của **một ca** đủ và được duyệt riêng mới lập lệnh thực thi với executor đã review; tài liệu này cố ý không cung cấp lệnh bật runtime. Sau run phải giữ nguyên raw logs/receipts, manifest SHA256, actual source, exit code, expected/actual và cleanup inventory. Final Issue #18/UAT vẫn chưa hoàn thành cho tới khi owner nghiệm thu bằng evidence thật.
