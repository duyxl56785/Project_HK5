# TV1 — Auth/JWT/Identity/Gateway Contract

**Owner:** TV1 — Lê Xuân Bảo. **Issue:** [#1](https://github.com/duyxl56785/Project_HK5/issues/1). **Phiên bản:** draft v0.1, ngày 2026-10-16.

> **DRAFT — CHƯA PHÊ DUYỆT, CHƯA TRIỂN KHAI.** Đây là đề xuất để owner/consumer review và xây stub có kiểm soát; không thay thế contract V2 gốc hoặc cho phép thay code TV2–TV5. Các quy tắc mới bên dưới chỉ có hiệu lực sau khi được duyệt. `OPEN DECISION` là điểm chặn triển khai phần liên quan, không chặn việc review tài liệu hay test bằng fixture.

## 1. Baseline và bằng chứng đối chiếu

| Nguồn | Kết quả audit / giới hạn |
|---|---|
| [README](../README.md), [CONTRIBUTING](../CONTRIBUTING.md), toàn bộ 5 tài liệu hiện có trong `docs/` | Scaffold; giữ Java 21, Boot 3.3.5, Cloud 2023.0.5; role CUSTOMER/SELLER/ADMIN theo Issue #1; kiểm quyền server-side, database-per-service, không FK xuyên DB. |
| [CONTRACTS](CONTRACTS.md) | Giữ `/api/**` qua Gateway, `/internal/**` không route công khai, internal token theo service pair. Không đổi checkout, event, tiền, ownership. |
| [Auth](../backend/auth-service/src/main/java/com/projecthk5/authservice/AuthServiceApplication.java), [User](../backend/user-service/src/main/java/com/projecthk5/userservice/UserServiceApplication.java), [Gateway](../backend/api-gateway/src/main/java/com/projecthk5/gateway/ApiGatewayApplication.java), build và `application.yml` tương ứng | Tại commit `d6ddbb8261594024837a4b581003849e693a9335`: chỉ entry point/Actuator; Auth/User dùng starter-web; Gateway dùng Spring Cloud Gateway (reactive). Chưa có controller, DTO, JWT decoder, SecurityFilterChain, route nghiệp vụ hay migration. Port dev Auth 8101, User 8102, Gateway 8080. |
| [PR #19](https://github.com/duyxl56785/Project_HK5/pull/19), head `ea754cef9382e0a64facfc62da92d25f41b0b29f` | OPEN, chưa merge. Đã đọc đủ 144 dòng kế hoạch COD-only và review: contract D2; identity/JWT/401/403/CORS; seller APPROVED; UAT 2 Store. Review TV5 đang CHANGES_REQUESTED; TV2 nhắc không tự chuyển HS256 sang JWKS, không bỏ Kafka/outbox. Không coi JWKS hoặc phân loại P0/P1 trong kế hoạch là quyết định chung đã duyệt. |
| [PR #7](https://github.com/duyxl56785/Project_HK5/pull/7), head `30f0b21944892fe7dabc848ce65b5f7ddd2ad02b` | OPEN, chưa merge. Category GET/HEAD public; mọi write bị chặn 403 `CATEGORY_WRITE_AUTH_NOT_CONFIGURED`. Seller có `SellerIdentity(String reference)`, `SellerAccessPolicy`, lifecycle domain; chưa có HTTP/JPA/JWT adapter. Reference là opaque, so sánh chính xác; chặn self-moderation. Không sửa các hành vi này trong PR tài liệu. |
| [PR #22](https://github.com/duyxl56785/Project_HK5/pull/22) | OPEN, chưa merge; không mang Gradle Wrapper hoặc commit của PR này sang branch contract. |
| Contract V2 gốc `docs/agent/CONTRACTS.md` và báo cáo thiết kế đầy đủ | Không có trong tracked files của checkout; chỉ có tài liệu tham chiếu V2 trong repo. Chưa xác minh tính tương thích từng field/ID/thuật toán với V2 gốc. Không tuyên bố đã audit tài liệu gốc. |
| Frontend shared/auth | `domains.ts` và README feature là placeholder; chưa có API client, auth context hoặc storage token thật. |

Không phát hiện OpenAPI/Swagger hay contract Auth được triển khai trong checkout. Tài liệu này chỉ bổ sung đề xuất TV1; không công bố API Seller/Store/Order của owner khác là đã chốt.

## 2. Ranh giới trách nhiệm và identity đề xuất

| Thành phần | Sở hữu / xác minh | Không được làm |
|---|---|---|
| Auth Service | Credentials, password hash, account status, stable account ID, role grants, phát/refresh/revoke token | Trả password hash, nhận role ADMIN/SELLER từ đăng ký công khai, truy cập DB service khác |
| User Service | Profile gắn account ID, địa chỉ và quy tắc địa chỉ mặc định theo Issue #1 | Làm nguồn xác thực/password, tự cấp role, FK tới DB Auth |
| Gateway | Route allowlist, CORS, verify JWT cho API được bảo vệ, giữ Bearer khi forward | Coi header identity do client gửi là đáng tin, làm nơi kiểm ownership duy nhất |
| Service nghiệp vụ | Verify JWT độc lập, role + ownership + trạng thái nghiệp vụ tại thời điểm thao tác | Chỉ tin Gateway/UI hoặc chỉ decode JWT mà không verify |
| Seller Service (TV2) | Quan hệ owner → seller → store, seller/store status | Suy ra quyền quản lý một Store chỉ từ role SELLER |

**Identity candidate (OD-02):** `userId` là stable account ID do Auth cấp, wire type `string`; JWT `sub` bằng chính `userId`. Profile User dùng cùng logical ID. Consumer không ép UUID/numeric, không trim/lowercase ID, không dùng email làm khóa, không tái sử dụng ID của tài khoản đã xóa. Mỗi môi trường chấp nhận đúng một issuer cấu hình; không trộn dữ liệu giữa các issuer. Nếu V2 gốc hoặc đa issuer đòi namespace khác, nhóm chốt `(iss, sub)`/mapping trước khi lưu dữ liệu.

Trong adapter TV2 tương lai: chỉ sau verify JWT mới tạo `SellerIdentity(reference = sub)` từ SecurityContext; actor không lấy từ DTO/header. Đây là mapping đề xuất, không yêu cầu sửa record/domain trong PR #7. `sellerId` và `storeId` là ID miền khác; tuyệt đối không gán bằng `sub` nếu chưa có mapping của TV2. Đề xuất User profile được tạo/upsert idempotent theo `userId`; cơ chế đồng bộ và lỗi tạo profile thuộc OD-08, không transaction xuyên DB.

## 3. JWT access token candidate

### 3.1. Claims và validation

| Claim | Kiểu / bắt buộc | Semantics đề xuất |
|---|---|---|
| `iss` | string, có | So sánh chính xác với issuer đã cấu hình theo môi trường; không lấy cấu hình từ token |
| `sub` | nonblank string, có | Stable `userId` theo mục 2 |
| `aud` | array<string>, có | Candidate `["smart-ecommerce-api"]`; Gateway và business resource servers cùng kiểm tra audience này; internal API không chấp nhận chỉ user JWT |
| `iat` | integer NumericDate, có | Thời điểm phát, UTC seconds; không được ở tương lai quá clock skew |
| `nbf` | integer NumericDate, có | Đề xuất bằng `iat`; chưa tới thời điểm thì từ chối |
| `exp` | integer NumericDate, có | Đề xuất `iat + 900` (15 phút); kiểm `exp > iat`, thời hạn không vượt profile đã duyệt |
| `jti` | nonblank string, có | ID duy nhất của access token; không tự tạo khả năng revoke nếu chưa có cơ chế kiểm tra |
| `roles` | array<string>, có | Không rỗng; chỉ CUSTOMER, SELLER, ADMIN; giá trị phân biệt hoa/thường, không chứa prefix `ROLE_` |

Không đặt password, token khác, email, phone, address, seller/store status hoặc danh sách Store vào JWT. Không thêm claim `permissions`, `userId`, `sellerId`, `storeId`, `tokenVersion` trùng chức năng nếu chưa có use case được duyệt. Refresh token không dùng làm Bearer access token.

Ví dụ payload **giả, không có chữ ký, không phải token sử dụng được**; timestamp chỉ minh họa quan hệ TTL:

```json
{
  "iss": "https://identity.example.invalid",
  "sub": "acct-customer-fixture",
  "aud": ["smart-ecommerce-api"],
  "iat": 1800000000,
  "nbf": 1800000000,
  "exp": 1800000900,
  "jti": "access-fixture-001",
  "roles": ["CUSTOMER"]
}
```

`example.invalid` là placeholder; issuer thật và audience thuộc OD-01/OD-02. Mọi decoder phải verify chữ ký, allowlist đúng một thuật toán đã duyệt, claim type/presence, issuer, audience, thời gian và roles; từ chối `alg=none`, algorithm/key confusion, token sai purpose. Đề xuất clock skew tối đa 60 giây; không tăng skew để chữa lỗi đồng hồ. Header `typ=JWT` được đề xuất cho access token, kiểm tra nhất quán; internal credential dùng cơ chế riêng. Quy tắc verify tham khảo [RFC 8725 §§3.1, 3.8–3.12](https://www.rfc-editor.org/rfc/rfc8725.html#section-3).

### 3.2. Thuật toán và key distribution — OPEN DECISION OD-01

Chưa tìm thấy thuật toán/secret hiện hữu trong source. Review PR #19 nhắc HS256; cần TV1 cùng consumer đối chiếu V2 gốc trước khi quyết định, không tự mặc định đổi sang JWKS.

| Phương án cần duyệt | Yêu cầu / đánh đổi |
|---|---|
| Giữ HS256 nếu baseline được xác nhận | Secret ngẫu nhiên ít nhất 256 bit, cấp qua secret runtime; mọi verifier biết secret có khả năng ký token nên phải giới hạn phạm vi tin cậy. Không đưa secret vào Config repo, fixture hoặc Git. Không có JWKS endpoint cho symmetric secret. |
| RS256 + JWKS nếu nhóm duyệt | Chỉ Auth giữ private key; verifier lấy public key từ URI tin cậy cố định. Header `kid` bắt buộc; JWKS chỉ có public parameters. Không theo `jku`/`x5u` từ token. Cần bàn giao key rotation/cache/outage tests. |

Nếu chọn RS256, candidate URI riêng của Auth là `/.well-known/jwks.json`, **không mở trên Gateway trong MVP**; resource server cấu hình trực tiếp `jwk-set-uri` và validator issuer/audience, không giả định đã có OIDC discovery. Giữ public key cũ ít nhất access TTL + skew sau lần phát cuối; unknown `kid` chỉ refresh giới hạn, vẫn từ chối nếu không có key. Cache key đã tin cậy có thể dùng theo TTL được duyệt; cache miss và không lấy được key phải fail-closed (503), không bypass signature. Token sai chữ ký/key không tồn tại sau lần tải thành công trả 401.

Gateway cần reactive resource-server chain; Auth/User/business servlet service dùng servlet chain. Claim `roles` phải map rõ sang Spring authorities `ROLE_CUSTOMER`, `ROLE_SELLER`, `ROLE_ADMIN`; không dựa vào mapping scope mặc định. Đây là hướng triển khai, chưa phải cấu hình/dependency đã thêm. Tham khảo cơ chế converter/validator trong [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) và [reactive JWT](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html); tài liệu online có thể thuộc bản mới hơn, implementation phải kiểm lại API tương thích Boot 3.3.5, không nâng framework theo trang hiện tại.

## 4. Roles, quyền và trạng thái động

Role là tập giá trị độc lập, không có thứ bậc ngầm. Đề xuất đăng ký công khai chỉ cấp CUSTOMER; Seller/Admin muốn mua hàng cần CUSTOMER trong tập roles. ADMIN không tự có quyền đọc/sửa tài nguyên khách hoặc vận hành Store khác.

| Thao tác / policy logic đề xuất | Điều kiện backend | Owner chốt |
|---|---|---|
| Public catalog/category read | Không cần JWT; service vẫn lọc visibility theo miền | TV2 |
| Auth `/me`, User profile của mình | Access JWT hợp lệ; target `sub` | TV1 |
| Xin đăng ký Seller | Tài khoản xác thực CUSTOMER; owner lấy từ `sub`; không yêu cầu sẵn SELLER để tránh vòng lặp onboarding | TV1 + TV2 |
| Duyệt/reject/suspend/reinstate Seller | ADMIN, cấm self-moderation như domain PR #7 | TV1 + TV2 (OD-03) |
| Ghi Category, kiểm duyệt Product | Đề xuất ADMIN; tích hợp JWT trước khi mở ghi Category | TV1 + TV2 (OD-03) |
| Quản lý Store/Product, SellerOrder | SELLER + owner mapping đúng + seller APPROVED + trạng thái Store hợp lệ hiện tại | TV2 + TV4 |
| Cart, tạo/đọc đơn của Customer | CUSTOMER + cart/order owner bằng `sub`; checkout giữ `Idempotency-Key` hiện hữu | TV3 |
| Inventory mutation nội bộ | Service pair được phép + operation/ownership theo contract TV3/TV4; không mở user CRUD stock chung | TV3 + TV4 |
| Đọc Notification cá nhân | Access JWT + recipient bằng `sub` | TV4 |
| Xác nhận COD paid | Candidate ADMIN + SellerOrder DELIVERED + idempotency theo TV4; SELLER không mặc nhiên được xác nhận | TV3 + TV4 + TV5 (OD-07) |
| Review / Finance | Review: CUSTOMER + chính người mua + điều kiện miền; Finance: ADMIN hoặc SELLER đúng Store theo API được duyệt | TV2–TV5 (OD-07) |

Các tên policy mô tả hành vi, không tạo thêm enum/claim permission công khai. TV2 adapter `requireSellerModerationPermission` ánh xạ ADMIN đã verify theo đề xuất; `requireAuthenticated` phải kiểm actor gắn với principal hiện tại, không chỉ kiểm reference khác null.

**Seller status không phải role.** APPROVED/SUSPENDED thuộc nguồn TV2; không lấy từ request/JWT để quyết định ghi. Role SELLER cũ không vượt qua suspension. Đề xuất Auth cấp SELLER sau approval, vẫn giữ CUSTOMER; contract đồng bộ và race approval/refresh thuộc OD-03. Trước khi đồng bộ xong, không cấp quyền theo mặc định. Với thao tác ghi quan trọng, không xác minh được seller/store status thì từ chối 503 `AUTHORIZATION_DEPENDENCY_UNAVAILABLE`, không giả vờ người dùng sai quyền (403). Cache/quy tắc offline phải được duyệt riêng.

## 5. HTTP API TV1 đề xuất

Giữ endpoint `/api/auth/me` từ Issue #1. Chưa tồn tại endpoint thực thi nào dưới đây tại baseline. JSON UTF-8, field camelCase; response thành công trả DTO trực tiếp, không tự thêm envelope chung cho service khác. Auth response có `Cache-Control: no-store` và `Pragma: no-cache`.

| Method + path | Credential | Request | Thành công / lỗi chính |
|---|---|---|---|
| `POST /api/auth/register` | Public + kiểm Origin/CSRF theo mục 6 | `RegisterRequest` | 201 `AccountResponse`, `Location: /api/auth/me`; 400 validation, 409 duplicate, 429 throttled; không auto-login |
| `POST /api/auth/login` | Public + Origin/CSRF | `LoginRequest` | 200 `TokenResponse` + refresh cookie; 401 `AUTH_INVALID_CREDENTIALS`, 429 |
| `POST /api/auth/refresh` | Refresh cookie + Origin/CSRF; không yêu cầu access JWT | Không body | 200 `TokenResponse` + cookie mới; 401 `AUTH_REFRESH_INVALID`, 403 CSRF, 503 dependency |
| `POST /api/auth/logout` | Refresh cookie + Origin/CSRF; không yêu cầu access JWT | Không body | 204 + clear cookie, idempotent nếu cookie thiếu/đã vô hiệu; 403 CSRF, 503 nếu chưa ghi revoke được |
| `GET /api/auth/me` | Bearer | Không body | 200 `AccountResponse` từ trạng thái Auth hiện tại; 401 token/account không còn hợp lệ |
| `GET /api/users/me` | Bearer | Không body | 200 `UserProfileResponse`; 404 `USER_PROFILE_NOT_FOUND` nếu chưa provision |
| `PATCH /api/users/me` | Bearer | `UpdateProfileRequest` | 200 `UserProfileResponse`; 400 validation, 404 chưa có profile |

Path/DTO/error mới cần duyệt OD-04/OD-08. Không thêm `/api/v1`, `/auth/current-user` hoặc `/api/users/{id}` cạnh tranh endpoint `me`. Địa chỉ F02 vẫn thuộc User Service nhưng DTO/endpoint địa chỉ cần thiết kế ở nhiệm vụ riêng; tài liệu này không chốt thay contract địa chỉ, cart claim hay order snapshot.

### 5.1. DTO và validation candidate

| DTO | Fields / ràng buộc |
|---|---|
| `RegisterRequest` | `email: string` bắt buộc, email hợp lệ, tối đa 254 ký tự; `password: string` bắt buộc, 12–128 ký tự; không có role/userId/status. Chính sách email/password còn OD-04. |
| `LoginRequest` | `email: string`, `password: string`, đều bắt buộc; password kiểm size 1–128 khi login để không vô tình khóa tài khoản V2 có mật khẩu ngắn cũ; tuyệt đối không trim/lowercase password. |
| `AccountResponse` | `userId: string`, `email: string`, `roles: array<string>` như mục 3; không trả credentials/refresh session internals. |
| `TokenResponse` | `accessToken: string`, `tokenType: "Bearer"`, `expiresIn: integer` giây (=900 theo candidate), `account: AccountResponse`; refresh token chỉ trong HttpOnly cookie. |
| `UserProfileResponse` | `userId: string`, `displayName: string|null`, `phone: string|null`; account identity không lấy từ profile input. |
| `UpdateProfileRequest` | Chỉ `displayName` và `phone`; omitted = giữ nguyên, null = xóa; displayName sau trim 1–100 ký tự nếu khác null, phone dạng E.164 (`+` và 2–15 chữ số, chữ số đầu khác 0) nếu khác null; `{}` hoặc field không biết trả 400. Email/password/role không sửa qua User API. |

Đề xuất normalize email bằng strip + lowercase `Locale.ROOT` giống nhau ở đăng ký/đăng nhập/unique lookup; cần kiểm dữ liệu V2 trước vì có thể gộp identity cũ. `register`/`login` từ chối field ngoài DTO thay vì vô tình bind entity. Credential/account/profile DB khác nhau; không trả 201 nếu account chưa commit. Duplicate email trả 409 `AUTH_EMAIL_UNAVAILABLE` là candidate có đánh đổi enumeration (OD-04); login luôn cùng lỗi/message cho sai email, sai password hoặc account bị khóa, không tiết lộ trạng thái tài khoản. Không log request password/token, kể cả lỗi validation; `errors` không chứa rejected value.

Ví dụ `TokenResponse` chỉ chứa placeholder:

```json
{
  "accessToken": "<runtime-issued-access-token>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "account": {
    "userId": "acct-customer-fixture",
    "email": "customer@example.invalid",
    "roles": ["CUSTOMER"]
  }
}
```

## 6. Refresh token và session candidate — OPEN DECISION OD-05/OD-06

Đề xuất refresh token opaque, CSPRNG ít nhất 256 bit, không phải access JWT; server lưu hash, subject, family/session ID, ngày tạo/hết hạn, trạng thái used/revoked và quan hệ rotation; không lưu raw token. Mỗi login tạo family riêng; thời hạn tuyệt đối 7 ngày từ login, refresh không kéo dài vô hạn. Kiểm account status và role hiện tại khi login/refresh.

1. Login trả access token trong JSON và `Set-Cookie: refresh_token=<opaque>; HttpOnly; Secure; SameSite=Lax; Path=/api/auth; Max-Age=604800`, không có Domain. TTL cookie mới giảm theo thời hạn còn lại của family. Không trả refresh token trong body/query/localStorage/log.
2. Refresh atomically tiêu thụ token hiện tại đúng một lần, tạo token con và access mới trong cùng commit phía Auth; chỉ response sau khi commit. Refresh token cũ không dùng lại được. Failure khi persist trả 503, không phát cặp token chưa lưu.
3. Replay một token đã dùng thu hồi toàn bộ family, clear cookie và trả 401 `AUTH_REFRESH_INVALID`; giữ hash token cũ đủ lâu để phát hiện reuse đến hết family TTL. Thiếu/expired/unknown/revoked token trả cùng mã lỗi công khai. Không thu hồi family khác khi chỉ nhận token không biết nguồn.
4. Client single-flight refresh trên tab và điều phối nhiều tab; hai refresh đồng thời chỉ một lần tiêu thụ thành công, request dùng token cũ sau đó kích hoạt reuse policy. Mất response sau commit có thể buộc đăng nhập lại; không retry mù refresh hoặc thêm grace window ngầm. Đây là đánh đổi cần duyệt OD-05.
5. Logout revoke family xác định từ token hiện tại hoặc lịch sử token đã dùng, clear cookie bằng cùng Path/flags và `Max-Age=0`. Không có credential thì 204 chỉ xác nhận không có session được cung cấp; không hứa revoke mọi thiết bị. Chưa có logout-all trong phạm vi.
6. Access JWT đã cấp vẫn có thể dùng tới `exp` + skew sau logout/role removal nếu verifier chỉ kiểm offline. Candidate MVP chấp nhận cửa sổ tối đa 16 phút; seller suspension vẫn kiểm live ở miền TV2. Nếu cần khóa account/ADMIN tức thì, OD-06 phải chọn revocation/version/introspection với cache/outage semantics; không tuyên bố logout thu hồi access tức thì.

Chọn cookie là đề xuất OD-05, không âm thầm thay storage V2. Access token giữ trong memory FE shared; reload dùng refresh một lần. Với trình duyệt, login/register/refresh/logout bắt buộc Origin đúng allowlist và custom header `X-CSRF-Protection: 1`; reject missing/untrusted Origin hoặc thiếu header với 403 `AUTH_CSRF_REJECTED`, kể cả cookie SameSite. Chặn form/simple request; CORS chỉ cho origin tin cậy đặt header này. Công cụ test cũng phải gửi Origin/header phù hợp. Endpoint access API khác chỉ nhận Bearer, không dùng refresh cookie để xác thực.

Auth public endpoints không yêu cầu Bearer và bỏ qua access Bearer cũ ở cả Gateway/Auth theo profile này; refresh/logout vẫn bắt buộc credential cookie/CSRF như bảng. FE shared không gắn access token lên login/register/refresh/logout. Cookie qua môi trường cross-site cần thiết kế lại SameSite/CSRF, không tự đổi sang `SameSite=None`. Local dev HTTP chỉ được giảm Secure bằng profile local đã duyệt, không áp dụng lên deploy.

Rotation/reuse detection tham khảo [RFC 9700 §4.14](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14); TTL 7 ngày và hành vi HTTP ở đây là lựa chọn dự án, không phải yêu cầu mặc định của RFC hay tuyên bố cung cấp đầy đủ OAuth/OIDC.

## 7. HTTP status và error contract candidate

Đề xuất Auth/User/Gateway dùng `{code, message, errors?}` theo CONTRIBUTING; business code stable, message có thể đổi ngôn ngữ. `errors` khi có là array `{field: string, message: string}`, không có secret/PII/rejected value. Các service TV2–TV5 chỉ áp dụng sau owner duyệt, không mass-rewrite error V1/Category.

```json
{
  "code": "AUTH_ACCESS_INVALID",
  "message": "Access token is invalid or expired"
}
```

| Tình huống | HTTP / code đề xuất | Header / hành vi |
|---|---|---|
| Protected API thiếu Bearer | 401 `AUTH_ACCESS_REQUIRED` | `WWW-Authenticate: Bearer realm="smart-ecommerce"` |
| JWT hết hạn, sai signature/issuer/audience/claim, token sai purpose | 401 `AUTH_ACCESS_INVALID` | `WWW-Authenticate: Bearer realm="smart-ecommerce", error="invalid_token"`; không tiết lộ lý do crypto |
| Header auth không đúng cấu trúc / nhiều phương thức truyền token | 400 `AUTH_REQUEST_INVALID` | Bearer challenge với `error="invalid_request"`; không xếp tất cả malformed request thành sai role |
| Login sai credential / tài khoản không được đăng nhập | 401 `AUTH_INVALID_CREDENTIALS` | Không dùng Bearer challenge vì đây không phải Bearer resource request |
| Refresh cookie không hợp lệ | 401 `AUTH_REFRESH_INVALID` | Clear cookie; không Bearer challenge, không refresh đệ quy |
| Principal hợp lệ nhưng thiếu role, sai ownership, seller suspended, self-moderation | 403 `AUTH_FORBIDDEN` | Không refresh để chữa 403; không giả `insufficient_scope` khi không dùng OAuth scope |
| Origin/CSRF không hợp lệ | 403 `AUTH_CSRF_REJECTED` | Không thực hiện mutation, kể cả logout |
| DTO sai / conflict email | 400 `VALIDATION_FAILED` / 409 `AUTH_EMAIL_UNAVAILABLE` | Lỗi miền, không đổi thành 401/403 |
| Không có resource sau khi đã xác thực/kiểm quyền | 404 mã miền | Candidate ownership mặc định 403; nếu owner cần conceal bằng 404 phải thống nhất riêng |
| Không thể tải key tin cậy hoặc không kiểm tra được authorization dependency | 503 `AUTH_VERIFICATION_UNAVAILABLE` hoặc `AUTHORIZATION_DEPENDENCY_UNAVAILABLE` | Fail-closed; không refresh tự động, không trả allow |
| Auth bị throttle | 429 `AUTH_RATE_LIMITED` | `Retry-After` giây; limiter thực thi và ngưỡng thuộc implementation TV1 |
| Upstream route đã biết không kết nối/timeout | 503 `GATEWAY_UPSTREAM_UNAVAILABLE` / 504 `GATEWAY_UPSTREAM_TIMEOUT` | Không đổi thành 401 hoặc tự retry POST |

Với protected route, xác thực trước, sau đó kiểm role/ownership/validation theo miền. Public GET không có token vẫn hoạt động; nếu có Bearer sai thì trả 401, không âm thầm hạ xuống anonymous. Access token chỉ được gửi ở `Authorization: Bearer ...`, không query/body. Bearer challenge/status dựa trên [RFC 6750 §§2–3](https://www.rfc-editor.org/rfc/rfc6750.html#section-3); schema lỗi và role policy là đề xuất riêng.

FE shared chỉ thử refresh một lần cho protected request 401 `AUTH_ACCESS_REQUIRED`/`AUTH_ACCESS_INVALID`; không cho login/refresh/logout, 400, 403, 429 hoặc 5xx. Refresh thành công mới xét replay request theo khả năng an toàn: write nhạy cảm không retry mù; checkout giữ nguyên `Idempotency-Key` + body, theo contract TV3. Refresh thất bại 401 thì xóa session memory và yêu cầu đăng nhập lại; 503 giữ trạng thái lỗi tạm thời, không tự suy ra sai mật khẩu.

**Tương thích PR #7:** hiện Category write trả 403 `CATEGORY_WRITE_AUTH_NOT_CONFIGURED` kể cả anonymous do chủ ý deny-all; đây chưa phải JWT contract 401/403 mới. Giữ nguyên code/test TV2; sau khi contract duyệt, TV2 triển khai verifier rồi đổi negative tests/error theo PR tích hợp riêng. Không mở write bằng `permitAll` để cho FE chạy.

## 8. Gateway routing, CORS và internal boundary

Route đề xuất dùng allowlist method/path cụ thể; không bật discovery locator tự động hoặc wildcard route toàn service. Không strip `/api` khi downstream controller nhận nguyên path. Không rewrite lỗi miền hoặc tự retry mutation. Unknown path/method không forward; trả 404/405 phù hợp. Chặn `/internal` và `/internal/**` từ public Gateway bằng 404 trước routing, kể cả ADMIN.

| Route public dự kiến | Upstream | Chính sách |
|---|---|---|
| Chính xác các endpoint `/api/auth/*` ở mục 5 | auth-service | register/login/refresh/logout theo cookie/CSRF; chỉ `/me` yêu cầu Bearer |
| `GET/PATCH /api/users/me` | user-service | Bearer; identity từ sub; không tự mở cả `/api/users/**` |
| `GET/HEAD /api/categories`, `GET/HEAD /api/categories/{id}` | product-service | Theo read contract PR #7 sau khi PR được merge |
| `POST /api/categories`, `PUT/DELETE /api/categories/{id}` | product-service | Chưa mở ghi cho tới khi TV1/TV2 duyệt ADMIN + JWT integration |
| Seller/Store/Product/Cart/Order/Inventory/Notification/Review/Finance | Owner nghiệp vụ tương ứng | Chưa chốt path/method/DTO trong tài liệu TV1; deny-default cho tới khi đăng ký route + security review, không đoán prefix/route trùng |

`/actuator/**`, Eureka, Config Server, JWKS (nếu chọn) và admin infrastructure không public-route theo wildcard. Health cho vận hành qua mạng/cổng nội bộ; không coi Actuator exposure sẵn có là API client được phép truy cập. Downstream API không được publish port ra mạng public để bypass Gateway; vẫn verify JWT khi gọi trực tiếp.

Gateway xóa header identity/role do client có thể giả, gồm `X-User-Id`, `X-Customer-Id`, `X-Seller-Id`, `X-Store-Id`, `X-Roles`, `X-Authorities`, `X-Authenticated-User`, cùng header internal credential; không biến chúng thành principal. Không thêm trusted identity header thay cho JWT. `Forwarded`/`X-Forwarded-*` chỉ tin từ proxy đã cấu hình; không dùng host do client gửi để suy ra issuer. `Authorization`, `Idempotency-Key`, `Origin`, `Content-Type` được giữ theo route; cookie/Set-Cookie chỉ phục vụ Auth, không forward refresh cookie sang business service; redact credentials trong tracing/log.

**CORS candidate (OD-05):** chỉ Gateway xử lý CORS; allowlist origin chính xác theo env, local candidate `http://localhost:5173`, không `*` khi `allowCredentials=true`. Preflight cho method/header đã đăng ký được xử lý trước JWT, không mở quyền business. Methods GET/HEAD/POST/PUT/PATCH/DELETE/OPTIONS theo route; allowed headers `Authorization`, `Content-Type`, `Idempotency-Key`, `X-CSRF-Protection`; exposed headers `WWW-Authenticate`, `Retry-After`, `Location`. FE Auth request dùng credentials include; upstream lỗi 401/403 vẫn có CORS headers cho origin được phép. Origin/method/header không được phép phải bị từ chối. CORS không thay thế auth; non-browser vẫn phải vượt verifier/ACL.

**Internal baseline giữ nguyên:** `/internal/**` chỉ mạng nội bộ và internal token riêng từng caller→callee; user JWT/ADMIN không thay thế credential này. OD-09 đề xuất header `X-Internal-Token`, định danh caller từ credential đã verify chứ không từ `X-Service-Name`; so sánh an toàn, allowlist method/path theo pair, rotate secret runtime, không dùng chung JWT signing secret. Credential thiếu/sai trả 401 `INTERNAL_AUTH_INVALID`; pair hợp lệ nhưng không có quyền endpoint trả 403 `INTERNAL_FORBIDDEN`.

Nếu internal operation cần end-user actor, caller phải kiểm JWT và callee nhận/verify original Bearer bên cạnh pair credential; không truyền `userId` trần thành authority. Async/system operation không có end-user phải được chốt rõ policy theo nghiệp vụ. Pair cụ thể, delegated-actor requirements và timeout/retry thuộc owner caller/callee, chưa được mở API theo đề xuất này. Không đổi baseline sang OAuth client credentials/mTLS trong PR tài liệu.

## 9. Bàn giao để TV2–TV5 phát triển song song

| Consumer | Contract cần dùng sau khi duyệt / việc có thể chuẩn bị ngay | Phần cần phối hợp |
|---|---|---|
| TV2 | String `sub` → SellerIdentity trong verified adapter; role ADMIN cho moderation/Category candidate; owner→seller→store được resolve server-side | OD-01/02/03/09; Seller role synchronization, live status check, self-moderation, schema logical owner. Không đổi PR #7 bằng tài liệu này. |
| TV3 | Cart/order customer identity từ `sub`; không nhận customerId làm authority; 401/403; Gateway giữ Idempotency-Key | OD-02/04/09; DTO địa chỉ/snapshot riêng; delegated actor tới seller/inventory và retry checkout |
| TV4 | SellerOrder/Store ownership qua TV2, seller live status; internal pair cho stock; notification recipient = `sub` | OD-03/07/09; quyền COD, policy khi SUSPENDED vẫn có đơn chưa hoàn thành, actor system/compensation |
| TV5 | Customer review ownership, Store isolation Finance, negative security matrix và lỗi chuẩn | OD-04/07; review eligibility/settlement snapshot theo owner, không quyết định P0/P1 thay nhóm |
| FE shared (TV1), feature UI TV2–TV5 | Một auth context/API client: Bearer memory, single-flight refresh, phân biệt 401/403/503, không lưu refresh JS | OD-04/05; UI role chỉ hiển thị, backend vẫn kiểm lại |

Fixture identity an toàn đề xuất: `acct-customer-fixture`, `acct-seller-a-fixture`, `acct-seller-b-fixture`, `acct-admin-fixture`; seller fixture có roles CUSTOMER+SELLER và mapping 2 Store do TV2 cấp. Thêm pending/suspended Seller và Customer khác để test âm. Fixture không có private key/secret/password/token ký sẵn trong Git. Token test thật chỉ được sinh cục bộ bằng key test riêng sau khi OD-01/02 được duyệt; ghi rõ issuer/audience/expiry và không đăng token lên Issue/PR. Stub/mock chưa xác minh crypto phải được gắn nhãn test-only, không cắm allow-all policy vào production.

Trước khi freeze contract: TV1 bổ sung ngày/commit duyệt từng OD ở mục 10; consumer xác nhận mapping/DTO/error, rồi mỗi owner triển khai PR riêng với contract tests hai phía. Không ghi tất cả TV2–TV5 đã đồng ý khi chưa có review.

## 10. OPEN DECISION — cần nhóm xác nhận

Tất cả mục dưới đây **đang OPEN, chưa có phê duyệt được ghi nhận trong tài liệu này**. Quyết định phải ghi reviewer, ngày, commit/link bằng chứng; sửa proposal nếu khác V2 gốc trước khi freeze.

| ID | Quyết định / đề xuất cần xác nhận | Người chốt / tác động nếu chưa chốt |
|---|---|---|
| OD-01 | Xác minh thuật toán V2 gốc: giữ HS256 hay duyệt RS256/JWKS; key distribution/rotation/outage; issuer URL | TV1 + TV2–TV5; chặn verifier/token test và key config thực |
| OD-02 | Namespace account; `sub=userId` opaque string; một issuer/env; audience chung; roles array, TTL 15 phút/skew 60s | TV1 + TV2–TV5; chặn persistence owner/schema và JWT decoder chung |
| OD-03 | CUSTOMER onboarding Seller; cấp SELLER sau approval; đồng bộ role/status; ADMIN moderation/Category; cấm self-moderation; suspended Seller xử lý đơn đang có ra sao | TV1 + TV2 + TV4, TV3 khi ảnh hưởng checkout; chặn mở ghi và policy lifecycle |
| OD-04 | Endpoint/DTO/error mới mục 5/7; normalization email, password, duplicate enumeration; 403 hay concealed 404 cho ownership | TV1 + consumer; kiểm contract V2 trước khi freeze, không sửa API hiện hữu ngầm |
| OD-05 | Refresh cookie hay transport V2 cũ; 7 ngày absolute, rotation/reuse family; single-flight/lost-response; CORS/Origin/CSRF, local Secure flag | TV1 + FE consumers; chặn client/session implementation |
| OD-06 | Chấp nhận access token còn sống tối đa 16 phút sau logout/role removal hay cần revoke tức thì; account disable/admin permission semantics | TV1 + nhóm; ảnh hưởng availability/latency/revocation infrastructure, không tự thêm Redis blocklist |
| OD-07 | Actor được ghi COD; seller scope Finance, review authorization; policy suspension với fulfillment/COD | TV3 + TV4 + TV5 + TV2, TV1 kiểm security; không quyết định workflow nghiệp vụ thay owner |
| OD-08 | Account/profile ownership, provisioning idempotent; account thành công nhưng User unavailable xử lý thế nào; profile DTO và schema địa chỉ riêng | TV1 + TV3 consumer địa chỉ; không transaction DB phân tán, không trả profile giả |
| OD-09 | Header internal token, pair allowlist, rotation, delegated actor và lỗi; giữ mô hình service-pair hiện hành | TV1 + owner caller/callee TV2–TV5; không route internal công khai |

Việc chưa có V2 gốc là giới hạn audit, không phải bằng chứng baseline HS256/ID hiện hữu đã sai. Khi có mâu thuẫn: ưu tiên đối chiếu contract đã duyệt, ghi incompatibility và phương án migration trong PR riêng; không âm thầm đổi thuật toán, ID/schema/event hay service boundary. Kế hoạch PR #19 chưa merge không có quyền ghi đè contract chung.

## 11. Xác minh và acceptance checklist cho implementation sau này

**Đã thực hiện trong nhiệm vụ tài liệu:** đọc README/CONTRIBUTING, toàn bộ tài liệu `docs/` hiện hữu; đối chiếu source/build/config TV1 tại baseline, Issue #1, kế hoạch/review #19 và integration boundary #7; kiểm trạng thái #22. Static document checks và diff được báo ở PR; không suy ra runtime PASS từ audit source.

**NOT_RUN — chưa có Auth/User/Gateway implementation trong phạm vi:** unit/integration JWT, DB, HTTP, CORS browser, refresh race, E2E và Gradle build/test. Các ca dưới đây là tiêu chí cho PR triển khai, không phải kết quả test:

| Nhóm | Ca bắt buộc / expected |
|---|---|
| JWT verifier cả Gateway + downstream | Token hợp lệ, expired/nbf/future iat, wrong issuer/audience/type/signature/alg, missing/invalid claims/roles, refresh-as-access; xác thực đúng 401/400; forged identity headers không đổi actor |
| Key profile đã duyệt | HS256 wrong secret/rotation hoặc RS256 known/unknown kid, key rollover, JWKS cache/outage; không allow khi không verify được |
| Roles/ownership | CUSTOMER không ghi Category/moderate; anonymous đúng 401 sau tích hợp; Admin self-moderation bị chặn; Seller A không đọc/sửa Store/SellerOrder B; Customer khác không đọc đơn; ADMIN không bypass scope ngầm |
| Seller dynamics | Pending không có quyền quản lý Store; suspended bị chặn dù JWT SELLER còn hạn; status dependency chết trả 503; kiểm role refresh sau approval/revoke |
| Auth/session | DTO fields lạ/role injection, email normalization/duplicate race, wrong credential không enum; refresh thiếu/expired/revoked, single use/reuse/concurrency, lost response, absolute expiry, logout khi access hết hạn, clear cookie và logout idempotent; access còn sống theo OD-06 |
| Boundary/CORS/CSRF | Allowed/disallowed Origin, preflight, credentials, lỗi 401/403 vẫn đọc được từ FE; forged internal token/header; user JWT gọi internal bị chặn, pair sai scope 403; internal/Actuator/Discovery/Config không lộ qua Gateway |
| Contract/FE | Caller/callee cùng DTO và roles mapping; profile chưa provision trả 404; FE chỉ một refresh và không loop/refresh 403/503; checkout replay giữ nguyên key/body, không retry POST mù |
| Integration/UAT | FE→Gateway→Auth/User thật; hai Seller/Store và Customer thật theo contract đã duyệt; khởi động lại không mất session/account DB; không secret/token trong Git/log |

Chỉ freeze v1 sau khi owner/consumer review các OD liên quan. Dùng `Refs #1`, không `Closes #1`: tài liệu không hoàn thành Docker/Auth/User runtime và toàn bộ checklist W1.
