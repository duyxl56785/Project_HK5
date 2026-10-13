# QUY CHUẨN LẬP TRÌNH & LÀM VIỆC NHÓM — SMART E-COMMERCE MARKETPLACE V2

**Phiên bản:** 1.0  
**Áp dụng:** Nhóm 5 thành viên, kế hoạch 3 tuần (W1–W3)  
**Repository:** [duyxl56785/Project_HK5](https://github.com/duyxl56785/Project_HK5)  
**Chủ trì quy chuẩn:** TV1 — Lê Xuân Bảo (trưởng nhóm kỹ thuật)

> **Phạm vi xác minh:** Quy chuẩn được xây dựng từ báo cáo phân tích thiết kế V2 và kế hoạch nhóm đã thống nhất. Tại thời điểm soạn thảo, repository GitHub chưa có mã nguồn. Do đó các tên service, công cụ và quy trình dưới đây là **quy ước cần áp dụng/kiểm tra khi đưa source lên**, không phải xác nhận mọi quy ước đã tồn tại trong code.

## 1. Mục tiêu và 7 nguyên tắc bắt buộc

1. **Mỗi đầu việc có một người chịu trách nhiệm chính.** Phải liên kết GitHub Issue với Pull Request (PR).
2. **Không push trực tiếp vào `main`** sau khi khởi tạo repository. Mọi thay đổi mã nguồn đi qua branch riêng và PR được review.
3. **Không sửa file/module người khác đang sở hữu nếu chưa trao đổi.** Với vùng dùng chung, chỉ định một người chốt thay đổi.
4. **Không tự đổi API contract, event schema hoặc database schema** khi chưa thống nhất với service/Frontend liên quan.
5. **Một PR chỉ giải quyết một mục tiêu rõ ràng**; ưu tiên thay đổi nhỏ, dễ kiểm tra và dễ hoàn tác.
6. **Không đánh dấu DONE nếu chưa có bằng chứng chạy/test**; phân biệt PASS, FAIL và NOT_RUN.
7. **Không commit secrets, thông tin cá nhân khách hàng hoặc dữ liệu production** vào Git, Issues, PR và log.

## 2. Phân chia trách nhiệm / code ownership

| Người | Tài khoản GitHub | Khu vực chính | Trách nhiệm review khi liên quan |
|---|---|---|---|
| **TV1 — Lê Xuân Bảo** | `lexuanbao258-collab` | Auth, User, Gateway, Docker, cấu hình, UI/layout/router/API client dùng chung | Mọi thay đổi hạ tầng, auth, CI (nếu bổ sung), frontend shared |
| **TV2 — Nguyễn Văn Duy** | `duyxl56785` | Seller, Store, Category, Product catalog/ảnh/kiểm duyệt | Catalog, trạng thái sản phẩm, seller status, API product |
| **TV3 — Trần Văn Chính** | `tranvanchinhbg2006-alt` | Cart, Checkout, tạo đơn tổng/đơn con, idempotency | API checkout, order creation, cart claim, giá/snapshot đơn |
| **TV4 — Phạm Thế Cường** | `phamthecuong2022006-alt` | Inventory, SellerOrder fulfillment, COD, Notification, outbox/retry liên quan | Tồn kho, chuyển trạng thái đơn, hoàn kho, COD, event status |
| **TV5 — Nguyễn Minh Duy** | `nguyenminhduy090-alt` | Review, Finance, settlement, QA, báo cáo và demo | Review, đối soát, yêu cầu kiểm thử, nhất quán tài liệu |

**Vùng giao nhau phải phối hợp trước khi sửa:**

- **`order-service`:** TV3 sở hữu **checkout/order creation**; TV4 sở hữu **fulfillment/COD/hoàn kho**; TV5 sở hữu **Finance/settlement**. Thay đổi entity, migration, enum, controller hoặc DTO dùng chung cần thông báo cả ba, trong đó ít nhất **một người thuộc khu vực bị ảnh hưởng** review.
- **`product-service`:** TV2 sở hữu catalog, moderation, ảnh; TV5 sở hữu nghiệp vụ reviews và điểm đánh giá. Thay đổi bảng/DTO/product API dùng chung cần TV2 và TV5 cùng thống nhất.
- **Frontend dùng chung:** TV1 sở hữu router, layout, auth context, API client, theme/global CSS; TV2–TV5 sở hữu màn hình theo nghiệp vụ nhưng không sửa các file shared mà chưa thông báo TV1.
- **`inventory-service` + checkout:** TV3 và TV4 thống nhất contract consume/release và khóa thao tác theo orderId trước khi thay đổi hành vi.
- **Auth + seller status:** TV1 và TV2 thống nhất role, seller APPROVED/SUSPENDED và điều kiện kiểm quyền; không chỉ dựa vào trạng thái hiển thị ở UI.

> File ownership chính xác sẽ được điền sau khi mã nguồn V2 được import vào repository. Bảng trên mô tả **quyền quyết định nghiệp vụ**, không tự khẳng định tên đường dẫn trong source.

## 3. Quy trình Git bắt buộc

### 3.1. Branch

- Nhánh ổn định: `main` — chỉ nhận code đã review và đạt test liên quan.
- Mỗi nhiệm vụ dùng một branch ngắn sống: `feat/tv2-product-images`, `fix/tv3-checkout-idempotency`, `test/tv4-inventory-retry`, `docs/tv5-report`.
- Các loại branch được chấp nhận: `feat/`, `fix/`, `refactor/`, `test/`, `docs/`, `chore/`.
- Không dùng một branch chung cho 5 người; không sửa trực tiếp trên branch cá nhân của thành viên khác.

### 3.2. Quy trình mỗi nhiệm vụ

```bash
# Lần đầu: clone repository sau khi đã có source
 git clone https://github.com/duyxl56785/Project_HK5.git
 cd Project_HK5

# Mỗi nhiệm vụ mới: bắt đầu từ main mới nhất
 git switch main
 git pull --ff-only origin main
 git switch -c feat/tv3-checkout-validation

# Code, chạy test liên quan, chỉ stage file thuộc nhiệm vụ
 git status
 git add <duong-dan-file-da-sua>
 git commit -m "fix(checkout): validate expected items (#3)"
 git push -u origin feat/tv3-checkout-validation
```

Sau đó mở **Pull Request vào `main`**, mô tả thay đổi và nhờ review theo Mục 4. `#3` chỉ là ví dụ Issue; dùng số Issue thực tế.

### 3.3. Khi có thay đổi từ main hoặc xung đột

```bash
 git fetch origin
 git switch feat/tv3-checkout-validation
 git merge origin/main
 git status
```

Nếu Git báo conflict: mở file, thảo luận với chủ sở hữu nghiệp vụ, xử lý từng đoạn đánh dấu conflict, kiểm thử lại, rồi:

```bash
 git add <file-da-giai-quyet>
 git commit
 git push
```

**Cấm:** `git push --force` lên `main`, `git reset --hard` để xóa thay đổi chưa kiểm tra, hoặc chọn toàn bộ “ours/theirs” nhằm giải quyết conflict mà không đọc code. Không dùng `git push --force` lên branch người khác.

## 4. Pull Request, review và merge

**Yêu cầu trước khi mở PR**

- [ ] Có Issue/mục tiêu cụ thể; PR nêu `Closes #...` khi thực sự hoàn thành toàn bộ Issue, ngược lại dùng `Refs #...`.
- [ ] Không có thay đổi ngoài phạm vi (format hàng loạt, đổi tên file không liên quan, sửa module khác).
- [ ] Ghi rõ API/schema/event thay đổi hay không; nếu có phải cập nhật contract và trao đổi owner.
- [ ] Chạy test thích hợp; ghi lệnh và kết quả PASS/FAIL/NOT_RUN, không ghi PASS nếu chỉ nhìn code.
- [ ] Không commit `.env`, secret, mật khẩu, token, log nhạy cảm hoặc file build sinh ra.
- [ ] Không để comment debug/`console.log` nhạy cảm, xung đột `<<<<<<<` / `>>>>>>>`.

**Quy tắc review:**

1. PR thông thường: ít nhất **1 review của thành viên khác**, ưu tiên chủ sở hữu module.
2. PR chạm `order-service`, `product-service`, API shared, migration, event, Auth hoặc Gateway: **chủ sở hữu phần chịu ảnh hưởng phải review**; TV1 kiểm tra khi ảnh hưởng nền tảng/tích hợp.
3. Không tự merge PR nếu reviewer yêu cầu thay đổi chưa được xử lý.
4. PR đã sửa theo review phải được test lại. Nếu chưa có CI/CD, đính kèm kết quả test local, không giả định hệ thống tự chạy test.
5. Ưu tiên **Squash and merge** nếu repo cho phép; xóa feature branch sau khi merge.

**Mẫu mô tả PR:**

```md
## Mục tiêu / Issue
Refs #...
## Thay đổi
- ...
## Ảnh hưởng
- Service/Frontend: ...
- API / event / database migration: Có/Không; chi tiết ...
## Kiểm thử thực tế
- Lệnh chạy: ...
- PASS: ...; FAIL: ...; NOT_RUN: ... (lý do)
## Bằng chứng
- Ảnh/log hoặc link kết quả test: ...
## Rủi ro / cách rollback
- ...
## Người cần review
- @username
```

## 5. Quy chuẩn backend — Java 21, Spring Boot

Các phiên bản Java 21, Spring Boot 3.3, Spring Cloud 2023, Spring Security, JPA là **stack dự kiến từ báo cáo V2**; ưu tiên đúng phiên bản đã tồn tại trong mã nguồn khi import.

- **Tên:** class `PascalCase` (`OrderCheckoutService`), method/field `camelCase` (`confirmPayment`), constant `UPPER_SNAKE_CASE`, package lowercase. Tên phản ánh nghiệp vụ, tránh `Util`, `Manager` không rõ trách nhiệm.
- **Phân tầng:** Controller tiếp nhận DTO/validation/HTTP; Service chứa nghiệp vụ và transaction; Repository truy cập DB của **chính service**. Không đưa nghiệp vụ dài hoặc SQL trực tiếp vào Controller.
- **DTO và validation:** không trả JPA Entity trực tiếp; kiểm tra đầu vào bằng DTO và validation phù hợp, kiểm tra ownership/role/trạng thái lại ở backend, không tin `storeId` hay quyền do client tự gửi.
- **Lỗi:** API V2 theo contract có `code`, `message`, `errors` khi thích hợp. **Không tự ý sửa toàn bộ API V1** vốn có chỗ vẫn trả `{message}`; cập nhật contract khi có thay đổi.
- **Tiền tệ:** dùng `BigDecimal`/PostgreSQL `numeric`, không dùng `double/float` tính tiền; tuân theo quyết định làm tròn HALF_UP của dự án. Phí sàn 5%, house store 0% được chốt lúc tạo đơn con.
- **Giao dịch:** transaction DB ngắn, không giữ khóa trong lúc gọi HTTP service khác; checkout qua Saga/bù trừ, không tự bổ sung distributed transaction.
- **Bảo mật:** kiểm tra JWT, quyền sở hữu và seller/store status trên server; thao tác ghi quan trọng fail-closed theo contract. Không log access/refresh token, mật khẩu, địa chỉ/điện thoại đầy đủ.
- **Thay đổi dependency:** cần ghi lý do, tương thích phiên bản và tác động đến các service; không nâng version framework toàn hệ thống trong một PR nghiệp vụ.

## 6. Quy chuẩn frontend — React, TypeScript, Vite

- Dùng TypeScript rõ kiểu; tránh `any` không cần thiết; props, API DTO và trạng thái response có type.
- Tên component/hook: `PascalCase` cho component (`SellerOrdersPage`), `camelCase` và tiền tố `use` cho custom hook (`useSellerOrders`).
- Mỗi trang nghiệp vụ do thành viên tương ứng quản lý; những phần chung (router, Auth, API client, layout/global CSS) phải được TV1 review trước khi sửa.
- Gọi API qua lớp client dùng chung và Gateway; không tạo nhiều hàm refresh token ở từng trang, không hard-code base URL, token hay credential.
- Query dữ liệu server bằng cơ chế hiện có của dự án (báo cáo nêu TanStack Query), form/validation theo cấu hình hiện có (React Hook Form + Zod); tránh tạo state/server fetching trùng lặp.
- Mọi trang có loading/empty/error; kiểm tra quyền hiển thị ở UI **không thay cho kiểm tra quyền ở backend**.
- Không tự đổi cơ chế lưu token hoặc làm mới phiên ở phạm vi riêng; nếu muốn thay vì lý do bảo mật, tạo Issue và thống nhất TV1.
- Với màn hình mới, kiểm tra responsive từ 375 px và khả năng truy cập cơ bản (nhãn form, nút, trạng thái lỗi).

## 7. Database, migration, API và Kafka

**Database / Flyway**

- Mỗi service chỉ truy cập DB do nó sở hữu; liên kết sang miền khác bằng ID logic, API hoặc event, **không tạo FK xuyên database**.
- Migration Flyway **chỉ thêm phiên bản mới**, không sửa migration đã được áp dụng; kiểm tra version không trùng với PR song song.
- Người thay schema phải báo owner liên quan, cập nhật entity/DTO/test và ghi phương án tương thích dữ liệu V1.
- Ràng buộc tồn kho không âm, unique idempotency/settlement/review và seller-order ownership phải được giữ đúng.

**API contract / event**

- Thay endpoint, tên field, kiểu dữ liệu, trạng thái, permission, mã lỗi hay semantics => thông báo consumer trước khi merge, cập nhật tài liệu contract và test cả phía gọi lẫn phía nhận.
- Không tạo API trùng chức năng hoặc endpoint nội bộ được gateway mở công khai; giữ ranh giới `/api/**` và `/internal/**` theo thiết kế.
- Giữ `Idempotency-Key` ở checkout, operation theo `orderId`, transactional outbox, consumer idempotent và dead-letter topic; không xóa cơ chế retry/dedupe để “sửa nhanh”.
- Thay đổi không tương thích payload event => thống nhất migration/version event; không âm thầm đổi topic `.v1` đang có consumer.

## 8. Test, bằng chứng và Definition of Done

**Mỗi thay đổi phải có kiểm thử phù hợp** (không yêu cầu tất cả suite cho mọi PR):

- Java: unit/integration JUnit 5; Testcontainers khi cần PostgreSQL/Kafka/Redis thực.
- Frontend: typecheck, lint, build; Playwright cho UI / user flow bị thay đổi.
- Thay liên service: có test contract/tích hợp phía gọi và phía nhận; với luồng quan trọng có E2E live nếu môi trường cho phép.
- Các tình huống không được bỏ qua: access trái quyền; idempotency; sai trạng thái; checkout thiếu hàng; hoàn kho lặp; COD lặp; Kafka tắt/restart; service timeout.
- **E2E mock không được ghi là E2E live.** Ca chưa chạy phải ghi `NOT_RUN` cùng nguyên nhân; không xóa test để đạt PASS.

**Một Issue chỉ được đóng DONE khi:**

- [ ] Chức năng đúng tiêu chí chấp nhận và API/DB/event nhất quán.
- [ ] PR được review, merge lên `main` và không còn conflict.
- [ ] Có test log/ảnh minh chứng tương ứng với môi trường đã chạy.
- [ ] Cập nhật tài liệu/contract và ghi lỗi còn tồn, nếu có.

Trạng thái bàn giao: `DONE` (có bằng chứng), `PARTIAL` (đã làm nhưng chưa đạt toàn bộ), `NOT_VERIFIED` (chưa đủ chứng cứ).

## 9. Tổ chức làm việc trong 3 tuần

| Thời điểm | Việc bắt buộc |
|---|---|
| **Tuần 1 — Ngày 1–7** | Import code, thống nhất contract, phân ownership chính xác, khởi chạy module, lập test baseline và lỗi |
| **Tuần 2 — Ngày 8–14** | Sửa lỗi, tích hợp FE/BE/8 service, test luồng checkout→giao→COD→review/finance; **đóng băng tính năng lớn ngày 14** |
| **Tuần 3 — Ngày 15–21** | Regression, xử lý lỗi quan trọng, thu ảnh thật, kiểm tra báo cáo, demo, tag bản nghiệm thu |

**Nhịp phối hợp:** cập nhật Issue ít nhất mỗi ngày làm việc (đang làm, bị chặn, có PR); đồng bộ nhanh 10–15 phút nếu có phụ thuộc. Khi cần sửa file chung, bình luận trên Issue/PR và chỉ rõ người chịu trách nhiệm để tránh hai PR cùng thay một contract hoặc migration.

## 10. Khi có xung đột / thay đổi gấp

1. **Báo xung đột** bằng bình luận GitHub Issue/PR, nêu file và nguyên nhân.
2. **Trao đổi với owner** của module; không tự chọn một phía và xóa code phía còn lại.
3. **Chốt contract và cách merge**, ưu tiên PR nhỏ, tách thay đổi độc lập.
4. **Merge `origin/main` vào branch**, giải quyết conflict thủ công, test lại hai luồng chịu ảnh hưởng.
5. **Reviewer xác nhận**, sau đó mới merge PR.

Lỗi nghiêm trọng trên `main`: tạo Issue `bug`, branch `fix/tvX-...`, fix tối thiểu, test và PR nhanh; không bỏ qua review vì gấp.

---

## Checklist bàn giao cuối tuần (sao chép vào Issue)

```md
### Báo cáo tiến độ — TV... — Tuần ...
- Công việc hoàn thành: ...
- PR/commit đã merge: ...
- Test: PASS ... / FAIL ... / NOT_RUN ...
- Bằng chứng (log/ảnh/demo): ...
- Lỗi/Blocker: ...
- Thay đổi API/DB/event ảnh hưởng thành viên khác: ...
- Trạng thái: DONE / PARTIAL / NOT_VERIFIED
- Việc tiếp theo: ...
```

**Nguyên tắc cuối cùng:** ưu tiên tích hợp được và kiểm chứng được hơn việc mỗi người làm xong module riêng nhưng không thể chạy chung.