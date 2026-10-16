# TV1 — Auth/JWT/Identity/Gateway Contract

**Owner / người quyết định:** Technical Lead / TV1 — Lê Xuân Bảo (`lexuanbao258-collab`). **Issue:** [#1](https://github.com/duyxl56785/Project_HK5/issues/1). **Phiên bản:** Contract v1.0, quyết định ngày 2026-10-16.

> **DECIDED BY TECHNICAL LEAD — CHƯA TRIỂN KHAI.** Technical Lead đã chốt OD-01–OD-09 trong chỉ thị nhiệm vụ ngày 2026-10-16; đây là contract security/identity chính thức cho MVP, chờ merge PR tài liệu để trở thành baseline trên `main`. Không khẳng định TV2–TV5 đã review/xác nhận từng chi tiết. Thông số môi trường, wire contract nội bộ và bằng chứng runtime còn phải xác minh trước khi mở endpoint tương ứng; không tự sửa code hoặc contract nghiệp vụ của owner khác.

**Lịch sử:** [Draft v0.1 nguyên bản tại merge commit PR #23](https://github.com/duyxl56785/Project_HK5/blob/226dd2ca9c943df2fb524ddb77201b9b9d500d02/docs/TV1_AUTH_IDENTITY_GATEWAY_CONTRACT.md) giữ đầy đủ proposal, OPEN DECISION và bằng chứng audit/review trước đây. v1.0 chốt HS256, UUID nội bộ/opaque wire ID, security policies, cookie rotation và database boundaries; không migrate dữ liệu. [ADR-0001](adr/0001-tv1-mvp-auth-trust-boundaries.md) lưu đánh đổi HS256. [Roadmap và audit nền tảng](TV1_AUTH_IMPLEMENTATION_ROADMAP.md) phân biệt preflight của PHASE A với PHASE B chưa bắt đầu.

## 1. Baseline và bằng chứng đối chiếu

| Nguồn | Kết quả audit / giới hạn |
|---|---|
| [README](../README.md), [CONTRIBUTING](../CONTRIBUTING.md), toàn bộ 5 tài liệu hiện có trong `docs/` | Scaffold; giữ Java 21, Boot 3.3.5, Cloud 2023.0.5; role CUSTOMER/SELLER/ADMIN theo Issue #1; kiểm quyền server-side, database-per-service, không FK xuyên DB. |
| [CONTRACTS](CONTRACTS.md) | Giữ `/api/**` qua Gateway, `/internal/**` không route công khai, internal token theo service pair. Không đổi checkout, event, tiền, ownership. |
| [Auth](../backend/auth-service/src/main/java/com/projecthk5/authservice/AuthServiceApplication.java), [User](../backend/user-service/src/main/java/com/projecthk5/userservice/UserServiceApplication.java), [Gateway](../backend/api-gateway/src/main/java/com/projecthk5/gateway/ApiGatewayApplication.java), build và `application.yml` tương ứng | Tại commit `d6ddbb8261594024837a4b581003849e693a9335`: chỉ entry point/Actuator; Auth/User dùng starter-web; Gateway dùng Spring Cloud Gateway (reactive). Chưa có controller, DTO, JWT decoder, SecurityFilterChain, route nghiệp vụ hay migration. Port dev Auth 8101, User 8102, Gateway 8080. |
| [PR #19](https://github.com/duyxl56785/Project_HK5/pull/19), head `ea754cef9382e0a64facfc62da92d25f41b0b29f` | OPEN, chưa merge. Đã đọc đủ 144 dòng kế hoạch COD-only và review: contract D2; identity/JWT/401/403/CORS; seller APPROVED; UAT 2 Store. Review TV5 đang CHANGES_REQUESTED; TV2 nhắc không tự chuyển HS256 sang JWKS, không bỏ Kafka/outbox. Không coi JWKS hoặc phân loại P0/P1 trong kế hoạch là quyết định chung đã duyệt. |
| [PR #7](https://github.com/duyxl56785/Project_HK5/pull/7), head `30f0b21944892fe7dabc848ce65b5f7ddd2ad02b` | OPEN, chưa merge. Category GET/HEAD public; mọi write bị chặn 403 `CATEGORY_WRITE_AUTH_NOT_CONFIGURED`. Seller có `SellerIdentity(String reference)`, `SellerAccessPolicy`, lifecycle domain; chưa có HTTP/JPA/JWT adapter. Reference là opaque, so sánh chính xác; chặn self-moderation. Không sửa các hành vi này trong PR tài liệu. |
| [PR #22](https://github.com/duyxl56785/Project_HK5/pull/22) | OPEN khi bắt đầu audit, sau đó được người khác merge lúc `2026-10-16T00:49:56Z`, merge commit `230d760fd71b8359bb00d4100c2eebb4829d1ffe`. Nhánh contract fast-forward theo main mới, không tự merge PR/cherry-pick wrapper. Gradle 8.10 `--version` với JDK21 exit0; chưa build/test Auth. |
| Contract V2 gốc `docs/agent/CONTRACTS.md` và báo cáo thiết kế đầy đủ | Không có trong tracked files của checkout; chỉ có tài liệu tham chiếu V2 trong repo. Chưa xác minh tính tương thích từng field/ID/thuật toán với V2 gốc. Không tuyên bố đã audit tài liệu gốc. |
| Frontend shared/auth | `domains.ts` và README feature là placeholder; chưa có API client, auth context hoặc storage token thật. |

Audit v1.0 bắt đầu trên merge #23 `226dd2ca9c943df2fb524ddb77201b9b9d500d02`, cập nhật lên `main` `230d760fd71b8359bb00d4100c2eebb4829d1ffe` sau khi #22 merge trong phiên làm việc. Source nghiệp vụ TV1 vẫn scaffold như d6ddbb8. PR #7 head mới `82dea095354c6d70b89379f0f33e49660b80e7a6` thêm Product fallback deny-all 403, không đổi SellerIdentity/Category chain; PR #19 head `1407e9d9f4f5b13286474728f1beefebcb163abc` làm rõ Review/Finance, vẫn chưa merge. PR #20 head `02223789e997774887c293f709cbe607b4e11626` thêm checklist/hướng dẫn, identity String và domain rules chưa thành JWT/HTTP adapter. Giữ bảng audit v0.1 ở trên để không xóa lịch sử. Chi tiết compatibility ở mục 10 và roadmap; không công bố API nghiệp vụ owner khác là đã được TV1 chốt.

## 2. Ranh giới trách nhiệm và identity — OD-02/OD-08 DECIDED

| Thành phần | Sở hữu / xác minh | Không được làm |
|---|---|---|
| Auth Service | Credentials, password hash, account status, stable account ID, role grants, phát/refresh/revoke token | Trả password hash, nhận role ADMIN/SELLER từ đăng ký công khai, truy cập DB service khác |
| User Service | Profile gắn account ID, địa chỉ và quy tắc địa chỉ mặc định theo Issue #1 | Làm nguồn xác thực/password, tự cấp role, FK tới DB Auth |
| Gateway | Route allowlist, CORS, verify JWT cho API được bảo vệ, giữ Bearer khi forward | Coi header identity do client gửi là đáng tin, làm nơi kiểm ownership duy nhất |
| Service nghiệp vụ | Verify JWT độc lập, role + ownership + trạng thái nghiệp vụ tại thời điểm thao tác | Chỉ tin Gateway/UI hoặc chỉ decode JWT mà không verify |
| Seller Service (TV2) | Quan hệ owner → seller → store, seller/store status | Suy ra quyền quản lý một Store chỉ từ role SELLER |

**Identity chính thức:** Auth tạo `accountId` kiểu UUID nội bộ. JWT `sub` là biểu diễn string của accountId; field `userId` trong DTO v0.1 được giữ nguyên tên để tương thích và mang cùng giá trị string, không phát thêm field `accountId` cạnh tranh trong JSON. Profile User liên kết bằng accountId logic. Consumer coi ID wire là opaque string, không ép UUID/numeric, không trim/lowercase, không dùng email làm khóa, không tái sử dụng ID đã xóa. Mỗi môi trường chấp nhận đúng một issuer cấu hình; không trộn namespace giữa issuer. Auth/User có thể dùng UUID khi persist dữ liệu do mình sở hữu; yêu cầu opaque áp dụng ở consumer boundary. Nếu phát hiện dữ liệu V2 cũ có ID khác, dừng migration phần liên quan và lập mapping/compatibility plan, không đổi ID âm thầm.

Trong adapter TV2 tương lai: chỉ sau verify JWT mới tạo `SellerIdentity(reference = sub)` từ SecurityContext; actor không lấy từ DTO/header. Không cần sửa record/domain trong PR #7 để parse UUID. `sellerId` và `storeId` là ID miền khác; không gán bằng `sub` khi chưa có mapping của TV2.

**Database ownership:** `auth_db` sở hữu accounts, role grants, refresh families/tokens và task đồng bộ profile; `user_db` sở hữu profiles/shipping addresses. Mỗi service chỉ migrate DB của mình; chỉ FK nội bộ DB, không FK xuyên service. Schema/entity/migration chưa được tạo trong PR này.

**Hướng đồng bộ profile tối thiểu an toàn:** Auth ghi account + durable provisioning task trong cùng local transaction; worker gửi lệnh tạo profile rỗng idempotent theo accountId qua internal service-pair credential. User insert-if-absent với unique accountId; replay không ghi đè displayName/phone/address đã chỉnh. Worker retry có backoff, task lỗi được giữ để vận hành retry; không rollback account đã commit chỉ vì User unavailable. Endpoint nội bộ/timeout/giới hạn retry phải đặc tả ở PR User, chưa tự mở route. Đây là tác vụ system được allowlist chỉ để provision từ account đã commit, không đại diện quyền ghi profile tùy ý của user. Không cần thêm topic Kafka mới hay transaction xuyên DB. Account register thành công vẫn 201; profile chưa provision trả 404 `USER_PROFILE_NOT_FOUND`, FE có thể retry có giới hạn. Có thể recovery task bằng reconciliation account→profile; phải test crash trước/sau HTTP và duplicate delivery. Phương án chỉ gọi HTTP sau commit mà không có durable task bị loại vì có thể mất đồng bộ; chưa thay đổi event/outbox nghiệp vụ đã có.

## 3. JWT access token — OD-01/OD-02 DECIDED

### 3.1. Claims và validation

| Claim | Kiểu / bắt buộc | Semantics đề xuất |
|---|---|---|
| `iss` | string, có | So sánh chính xác với issuer đã cấu hình theo môi trường; không lấy cấu hình từ token |
| `sub` | nonblank string, có | Stable `userId` theo mục 2 |
| `aud` | array<string>, có | Phải chứa audience cấu hình tin cậy; `["smart-ecommerce-api"]` là ví dụ môi trường, không hardcode consumer; internal API không chấp nhận chỉ user JWT |
| `iat` | integer NumericDate, có | Thời điểm phát, UTC seconds; không được ở tương lai quá clock skew |
| `nbf` | integer NumericDate, có | Bằng `iat`; chưa tới thời điểm thì từ chối |
| `exp` | integer NumericDate, có | `iat + 900` (15 phút); kiểm `exp > iat`, không chấp nhận token có thời hạn dài hơn contract |
| `jti` | nonblank string, có | ID duy nhất của access token; không tự tạo khả năng revoke nếu chưa có cơ chế kiểm tra |
| `roles` | array<string>, có | Không rỗng; chỉ CUSTOMER, SELLER, ADMIN; giá trị phân biệt hoa/thường, không chứa prefix `ROLE_` |

Không đặt password, token khác, email, phone, address, seller/store status hoặc danh sách Store vào JWT. Không thêm claim `permissions`, `userId`, `sellerId`, `storeId`, `tokenVersion` trùng chức năng nếu chưa có use case được duyệt. Refresh token không dùng làm Bearer access token.

Ví dụ payload **giả, không có chữ ký, không phải token sử dụng được**; timestamp chỉ minh họa quan hệ TTL:

```json
{
  "iss": "https://identity.example.invalid",
  "sub": "00000000-0000-4000-8000-000000000001",
  "aud": ["smart-ecommerce-api"],
  "iat": 1800000000,
  "nbf": 1800000000,
  "exp": 1800000900,
  "jti": "access-fixture-001",
  "roles": ["CUSTOMER"]
}
```

`example.invalid` là placeholder; issuer/audience thật là cấu hình runtime tin cậy và phải giống nhau giữa issuer/verifier. Mọi decoder verify chữ ký HS256, claim type/presence, issuer, audience, thời gian và roles; từ chối `alg=none`, RS256/thuật toán khác, algorithm/key confusion, token sai purpose. Clock skew tối đa 60 giây; không tăng skew để chữa lỗi đồng hồ. Header `typ=JWT`, kiểm tra nhất quán; `nbf=iat`. JWT không nhận URL/key do token tự khai. Quy tắc verify tham khảo [RFC 8725 §§3.1, 3.8–3.12](https://www.rfc-editor.org/rfc/rfc8725.html#section-3).

### 3.2. HS256, trust boundary và rotation

Signing key là CSPRNG ít nhất 256 bit **sau giải mã**, cung cấp qua secret runtime/env an toàn; Base64 chỉ là encoding, không tạo entropy. Auth chỉ phát token khi key/issuer/audience hợp lệ; verifier thiếu key không được start ready, request khi không verify được trả 503. Không hardcode, không default secret hoặc nhận key từ request. Không triển khai RS256, JWKS hay OIDC discovery trong MVP. HS256 dùng ít nhất 256-bit key theo [RFC 7518 §3.2](https://www.rfc-editor.org/rfc/rfc7518.html#section-3.2).

Auth là **signer theo quy ước vận hành**, không phải signer duy nhất về mặt mật mã: bất kỳ verifier nào giữ cùng secret đều có thể giả mạo cả ADMIN. Trusted verifier boundary gồm Auth, Gateway và những instance User/Seller/Product/Cart/Order/Inventory/Notification cần kiểm user JWT; không cấp key cho FE, Eureka/Config repository, Redis, Kafka hoặc công cụ người dùng. Chỉ deploy service được kiểm soát cùng miền tin cậy, least-privilege secret access, không public debug/env/heap dump; service bị xâm nhập là sự cố toàn bộ signing domain. Live role check không loại bỏ hoàn toàn khả năng mạo danh một ADMIN đang hợp lệ. Đây là đánh đổi MVP được Technical Lead chấp nhận; thay boundary cần ADR/contract mới, không tự đổi thuật toán trong implementation.

Rotation dùng header `kid` bắt buộc, ID cấu hình không chứa secret, map vào keyring local allowlist tối đa current + previous HS256 key. Token thiếu/unknown kid trả 401, không tìm key qua mạng hay thử tùy ý các key. Token runtime/V2 chưa inventory; nếu có token cũ thiếu kid, phải lập migration/reauth plan trước rollout, không giả định tương thích. Quy trình planned rotation: (1) cấp key mới cho tất cả verifier nhưng chưa phát; (2) xác nhận mọi verifier nhận đúng version mà không log key; (3) Auth phát chỉ bằng kid mới, ghi mốc lần phát key cũ cuối cùng; (4) sau ít nhất 900+60 giây từ mốc đó mới bỏ key cũ. Instance chưa cập nhật không được nhận traffic. Test đồng thời old/new và retire key.

Header minh họa không chứa key: `{"alg":"HS256","typ":"JWT","kid":"fixture-key-v1"}`. `kid` chỉ chọn key đã cấu hình, không quyết định thuật toán.

Nếu nghi key lộ: dừng/thu hẹp traffic nhạy cảm, thay key và retire key lộ ngay, không giữ grace 16 phút; token dùng key đó thất bại 401, user phải xác thực lại. Revoke refresh families bị ảnh hưởng bằng transaction theo incident plan, không nhầm rotation access key tự revoke refresh. Không đưa secret, fingerprint suy đoán hoặc token test thật vào Git/log.

Gateway cần reactive resource-server chain; Auth/User/business servlet service dùng servlet chain. Claim `roles` phải map rõ sang Spring authorities `ROLE_CUSTOMER`, `ROLE_SELLER`, `ROLE_ADMIN`; không dựa vào mapping scope mặc định. Đây là hướng triển khai, chưa phải cấu hình/dependency đã thêm. Tham khảo cơ chế converter/validator trong [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) và [reactive JWT](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html); tài liệu online có thể thuộc bản mới hơn, implementation phải kiểm lại API tương thích Boot 3.3.5, không nâng framework theo trang hiện tại.

## 4. Roles, quyền và trạng thái động

Role là tập giá trị độc lập, không có thứ bậc ngầm. Đăng ký công khai chỉ cấp CUSTOMER; Seller/Admin muốn mua hàng cần CUSTOMER trong tập roles. ADMIN không tự có quyền đọc/sửa tài nguyên khách hoặc vận hành Store khác. ADMIN được provision qua quy trình vận hành được kiểm soát, không qua public register hoặc client role input.

| Thao tác / security policy | Điều kiện backend | Owner triển khai / phối hợp |
|---|---|---|
| Public catalog/category read | Không cần JWT; service vẫn lọc visibility theo miền | TV2 |
| Auth `/me`, User profile của mình | Access JWT hợp lệ; target `sub` | TV1 |
| Xin đăng ký Seller | Tài khoản xác thực CUSTOMER; owner lấy từ `sub`; không yêu cầu sẵn SELLER để tránh vòng lặp onboarding | TV1 + TV2 |
| Duyệt/reject/suspend/reinstate Seller | ADMIN hiện tại, cấm self-moderation như domain PR #7 | TV1 + TV2 (OD-03) |
| Ghi Category, kiểm duyệt Product | ADMIN hiện tại; tích hợp JWT trước khi mở ghi Category | TV1 + TV2 (OD-03) |
| Quản lý Store/Product, SellerOrder | SELLER + owner mapping đúng + seller APPROVED + trạng thái Store hợp lệ hiện tại | TV2 + TV4 |
| Cart, tạo/đọc đơn của Customer | CUSTOMER + cart/order owner bằng `sub`; checkout giữ `Idempotency-Key` hiện hữu | TV3 |
| Inventory mutation nội bộ | Service pair được phép + operation/ownership theo contract TV3/TV4; không mở user CRUD stock chung | TV3 + TV4 |
| Đọc Notification cá nhân | Access JWT + recipient bằng `sub` | TV4 |
| Xác nhận COD paid | Chỉ ADMIN hiện tại + SellerOrder DELIVERED + idempotency theo TV4; SELLER không được xác nhận | TV3 + TV4 + TV5 (OD-07) |
| Review / Finance | Review: CUSTOMER + chính người mua + điều kiện miền; Finance: ADMIN hoặc SELLER đúng Store theo API được duyệt | TV2–TV5 (OD-07) |

Các tên policy mô tả hành vi, không tạo thêm enum/claim permission công khai. TV2 adapter `requireSellerModerationPermission` ánh xạ ADMIN đã verify và kiểm quyền hiện tại; `requireAuthenticated` phải kiểm actor gắn với principal hiện tại, không chỉ kiểm reference khác null.

**Seller status không phải role.** APPROVED/SUSPENDED và owner→Store thuộc nguồn TV2; không lấy từ request/JWT để quyết định ghi. Auth sở hữu durable role grants: TV2 chỉ gửi grant/revoke sau state transition đã commit; integration command phải idempotent, chống stale/out-of-order bằng version của state và retry durable. Không nhận role grant từ FE hoặc caller không được allowlist. TV1 sở hữu validation/persist grant và phát roles hiện tại ở login/refresh; TV2 sở hữu phát lại trạng thái và reconciliation. Wire command/endpoint, version ordering và retry contract phải được TV1/TV2 review ở PR tích hợp; khi chưa có thì không mở flow grant giả.

Sau approval Auth cấp SELLER nhưng giữ CUSTOMER; token cũ thiếu SELLER cần refresh. Khi SUSPENDED phải revoke role grant qua cùng flow; mọi thao tác quản lý Store vẫn kiểm live APPROVED/ownership nên JWT SELLER cũ không vượt suspension dù đồng bộ chậm. ADMIN moderation/COD và thao tác nhạy cảm phải kiểm account/role hiện tại ở Auth cùng dữ kiện miền tại nguồn tin cậy. Dependency unavailable trả 503 `AUTHORIZATION_DEPENDENCY_UNAVAILABLE`, không trả allow hoặc giả 403. Inactive/deleted account đã xác minh trả 401 `AUTH_ACCESS_INVALID`; principal còn hợp lệ nhưng mất role/scope trả 403 `AUTH_FORBIDDEN`. Chưa có freshness guarantee cho cache trạng thái thì không dùng cache để authorize thao tác nhạy cảm. Không tự thêm introspection chung/Redis blocklist; adapter kiểm trạng thái miền qua internal pair/read model có bằng chứng freshness phải được đặc tả và test trước khi mở quyền.

Inventory tối thiểu của thao tác nhạy cảm: mọi ADMIN mutation/moderation, role grant/revoke, ghi COD/settlement và đọc dữ liệu Finance có bảo vệ, Seller Store/Product/fulfillment writes. Owner bổ sung operation-specific checks khi triển khai; không coi verify chữ ký là đã hoàn tất các kiểm tra này. JWT role chỉ có thể thu hẹp quyền: thiếu required role vẫn 403 dù nguồn live đã grant, cần refresh để lấy token mới.

Suspended Seller mặc định không có thao tác ghi quản lý Store/fulfillment; policy xử lý đơn đang giao, thông tin chỉ đọc hoặc system compensation thuộc TV2/TV3/TV4, chưa được cấp ngoại lệ ngầm. Đây là chi tiết nghiệp vụ cần bàn giao, không phải mở lại quyết định cấm vượt suspension.

## 5. HTTP API TV1 — OD-04 DECIDED

Giữ endpoint `/api/auth/me` từ Issue #1. Chưa tồn tại endpoint thực thi nào dưới đây tại baseline. JSON UTF-8, field camelCase; response thành công trả DTO trực tiếp, không thêm envelope chung cho service khác. Auth response có `Cache-Control: no-store` và `Pragma: no-cache`.

| Method + path | Credential | Request | Thành công / lỗi chính |
|---|---|---|---|
| `POST /api/auth/register` | Public + kiểm Origin/CSRF theo mục 6 | `RegisterRequest` | 201 `AccountResponse`, `Location: /api/auth/me`; 400 validation, 409 duplicate, 429 throttled; không auto-login |
| `POST /api/auth/login` | Public + Origin/CSRF | `LoginRequest` | 200 `TokenResponse` + refresh cookie; 401 `AUTH_INVALID_CREDENTIALS`, 429 |
| `POST /api/auth/refresh` | Refresh cookie + Origin/CSRF; không yêu cầu access JWT | Không body | 200 `TokenResponse` + cookie mới; 401 `AUTH_REFRESH_INVALID`, 403 CSRF, 503 dependency |
| `POST /api/auth/logout` | Refresh cookie + Origin/CSRF; không yêu cầu access JWT | Không body | 204 + clear cookie, idempotent nếu cookie thiếu/đã vô hiệu; 403 CSRF, 503 nếu chưa ghi revoke được |
| `GET /api/auth/me` | Bearer | Không body | 200 `AccountResponse` từ trạng thái Auth hiện tại; 401 token/account không còn hợp lệ |
| `GET /api/users/me` | Bearer | Không body | 200 `UserProfileResponse`; 404 `USER_PROFILE_NOT_FOUND` nếu chưa provision |
| `PATCH /api/users/me` | Bearer | `UpdateProfileRequest` | 200 `UserProfileResponse`; 400 validation, 404 chưa có profile |

Paths/DTO/errors trong mục này đã chốt. Không thêm `/api/v1`, `/auth/current-user` hoặc `/api/users/{id}` cạnh tranh endpoint `me`. Địa chỉ F02 thuộc User Service; DTO/endpoint địa chỉ thiết kế ở PR User riêng, không chốt thay cart claim/order snapshot. Register tạo account nên trả 201; `Location: /api/auth/me` chỉ đọc sau login, không hàm ý register tạo session hoặc tự provision profile thành công. Logout 204 không body; refresh 200 chỉ khi rotation commit. Method chưa hỗ trợ trả 405, Content-Type không hỗ trợ trả 415; hai lỗi protocol này không phải 401/403.

### 5.1. DTO và validation

| DTO | Fields / ràng buộc |
|---|---|
| `RegisterRequest` | `email: string` bắt buộc, email hợp lệ, tối đa 254 ký tự; `password: string` bắt buộc, 12–128 ký tự; không có role/userId/status. |
| `LoginRequest` | `email: string`, `password: string`, đều bắt buộc; password kiểm size 1–128 khi login để không vô tình khóa tài khoản V2 có mật khẩu ngắn cũ; tuyệt đối không trim/lowercase password. |
| `AccountResponse` | `userId: string`, `email: string`, `roles: array<string>` như mục 3; không trả credentials/refresh session internals. |
| `TokenResponse` | `accessToken: string`, `tokenType: "Bearer"`, `expiresIn: integer` giây (=900), `account: AccountResponse`; refresh token chỉ trong HttpOnly cookie. |
| `UserProfileResponse` | `userId: string`; `displayName` và `phone` là string hoặc null; account identity không lấy từ profile input. |
| `UpdateProfileRequest` | Chỉ `displayName` và `phone`; omitted = giữ nguyên, null = xóa; displayName sau trim 1–100 ký tự nếu khác null, phone dạng E.164 (`+` và 2–15 chữ số, chữ số đầu khác 0) nếu khác null; `{}` hoặc field không biết trả 400. Email/password/role không sửa qua User API. |

Normalize email bằng strip + lowercase `Locale.ROOT` giống nhau ở đăng ký/đăng nhập/unique lookup; nếu phát hiện dữ liệu V2 cũ trùng sau normalize phải dừng và lập mapping/giải quyết conflict, không gộp account. `register`/`login` từ chối field ngoài DTO thay vì bind entity. Unique email phải được DB bảo vệ kể cả concurrent register; không trả 201 trước commit. Duplicate email trả 409 `AUTH_EMAIL_UNAVAILABLE` có đánh đổi enumeration được ghi nhận; áp dụng throttling, không tiết lộ ID/profile của account đã có. Login dùng cùng lỗi/message cho sai email, password hoặc account bị khóa; xử lý timing với dummy password-hash verification khi account không có. Không log password/token, kể cả validation; `errors` không chứa rejected value. Chính sách hash cụ thể kiểm ở PR Auth API: phải giữ đủ 128 ký tự và không silently truncate (đặc biệt giới hạn byte của bcrypt); test Unicode/long password, không hạ validation để qua test.

Ví dụ `TokenResponse` chỉ chứa placeholder:

```json
{
  "accessToken": "<runtime-issued-access-token>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "account": {
    "userId": "00000000-0000-4000-8000-000000000001",
    "email": "customer@example.invalid",
    "roles": ["CUSTOMER"]
  }
}
```

## 6. Refresh token và session — OD-05/OD-06 DECIDED

Refresh token opaque, CSPRNG ít nhất 256 bit, không phải access JWT; server lưu SHA-256 hash của token entropy cao, subject, family/session ID, ngày tạo/hết hạn, trạng thái used/revoked và quan hệ rotation; không lưu raw token. Mỗi login tạo family riêng; thời hạn tuyệt đối tối đa 7 ngày từ login, refresh không kéo dài. Kiểm account status và role hiện tại khi login/refresh; token không hợp lệ không phân biệt nguyên nhân ra ngoài.

1. Login trả access token trong JSON và `Set-Cookie: refresh_token=<opaque>; HttpOnly; Secure; SameSite=Lax; Path=/api/auth; Max-Age=604800`, không có Domain. TTL cookie mới giảm theo thời hạn còn lại của family. Không trả refresh token trong body/query/localStorage/log.
2. Refresh atomically tiêu thụ token hiện tại đúng một lần, tạo token con và access mới trong cùng commit phía Auth; chỉ response sau khi commit. Refresh token cũ không dùng lại được. Failure khi persist trả 503, không phát cặp token chưa lưu.
3. Replay một token đã dùng thu hồi toàn bộ family, clear cookie và trả 401 `AUTH_REFRESH_INVALID`; giữ hash token cũ đủ lâu để phát hiện reuse đến hết family TTL. Thiếu/expired/unknown/revoked token trả cùng mã lỗi công khai. Không thu hồi family khác khi chỉ nhận token không biết nguồn.
4. Client single-flight refresh trên tab và điều phối nhiều tab; hai refresh đồng thời chỉ một lần tiêu thụ thành công, request dùng token cũ sau đó kích hoạt reuse policy. Vì vậy token con từ response thành công cũng có thể đã bị revoke bởi request thứ hai; kết quả an toàn là buộc login lại. Mất response sau commit có thể buộc đăng nhập lại; không retry mù refresh hoặc thêm grace window ngầm. Đây là đánh đổi đã chốt của OD-05, không nới policy để qua test.
5. Logout revoke family xác định từ token hiện tại hoặc lịch sử token đã dùng, clear cookie bằng cùng Path/flags và `Max-Age=0`. Không có credential thì 204 chỉ xác nhận không có session được cung cấp; không hứa revoke mọi thiết bị. Chưa có logout-all trong phạm vi.
6. Access JWT đã cấp vẫn có thể dùng tới `exp` + skew sau logout/role removal nếu verifier chỉ kiểm offline: cửa sổ tối đa 16 phút từ lần phát token, không phải 16 phút cộng thêm sau exp. Seller suspension và thao tác quản trị/nhạy cảm phải kiểm trạng thái/quyền hiện tại theo mục 4. Logout không hứa revoke mọi access token hoặc mọi thiết bị. Không thêm Redis blocklist hay introspection chung trong MVP.

Cookie là transport chính thức OD-05; không trả refresh token trong JSON. Access token giữ trong memory FE shared; reload dùng refresh một lần. Với trình duyệt, login/register/refresh/logout bắt buộc Origin đúng allowlist và custom header `X-CSRF-Protection: 1`; reject missing/untrusted Origin hoặc thiếu header với 403 `AUTH_CSRF_REJECTED`, kể cả cookie SameSite. Chặn form/simple request; CORS chỉ cho origin tin cậy đặt header này. Công cụ test cũng phải gửi Origin/header phù hợp. Endpoint access API khác chỉ nhận Bearer, không dùng refresh cookie để xác thực.

Auth public endpoints không yêu cầu Bearer và bỏ qua access Bearer cũ ở cả Gateway/Auth theo profile này; refresh/logout vẫn bắt buộc credential cookie/CSRF như bảng. FE shared không gắn access token lên login/register/refresh/logout. Cookie qua môi trường cross-site cần thiết kế lại SameSite/CSRF, không tự đổi sang `SameSite=None`. Local dev HTTP chỉ được giảm Secure bằng profile local đã duyệt, không áp dụng lên deploy.

Rotation/reuse detection tham khảo [RFC 9700 §4.14](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14); TTL 7 ngày và hành vi HTTP ở đây là lựa chọn dự án, không phải yêu cầu mặc định của RFC hay tuyên bố cung cấp đầy đủ OAuth/OIDC.

**Threat model và test OD-06:** stolen access token có thể replay trong TTL ở API chỉ verify offline; stolen refresh token replay làm revoke family nhưng không chứng minh ai là chủ hợp lệ. Logout chỉ revoke sau durable commit; DB lỗi trả 503 và không hứa revoke, FE không coi clear cookie là bằng chứng server đã thu hồi. XSS có thể hành động trong phiên dù refresh là HttpOnly; CORS/CSRF không chữa XSS hoặc HS256 key compromise. Test bắt buộc access còn hạn sau logout, refresh bị từ chối sau commit, logout DB rollback, role ADMIN bị gỡ khi token còn hạn, SUSPENDED với JWT cũ, dependency timeout fail-closed và multi-device family isolation.

## 7. HTTP status và error contract — OD-04 DECIDED

Auth/User/Gateway dùng `{code, message, errors?}` theo CONTRIBUTING; business code stable, message có thể đổi ngôn ngữ. `errors` khi có là array `{field: string, message: string}`, không có secret/PII/rejected value. Consumer áp dụng security codes khi tích hợp adapter theo contract này, không mass-rewrite error miền V1/Category; compatibility ở mục 10.

```json
{
  "code": "AUTH_ACCESS_INVALID",
  "message": "Access token is invalid or expired"
}
```

| Tình huống | HTTP / code | Header / hành vi |
|---|---|---|
| Protected API thiếu Bearer | 401 `AUTH_ACCESS_REQUIRED` | `WWW-Authenticate: Bearer realm="smart-ecommerce"` |
| JWT hết hạn, sai signature/issuer/audience/claim, token sai purpose | 401 `AUTH_ACCESS_INVALID` | `WWW-Authenticate: Bearer realm="smart-ecommerce", error="invalid_token"`; không tiết lộ lý do crypto |
| Header auth không đúng cấu trúc / nhiều phương thức truyền token | 400 `AUTH_REQUEST_INVALID` | Bearer challenge với `error="invalid_request"`; không xếp tất cả malformed request thành sai role |
| Login sai credential / tài khoản không được đăng nhập | 401 `AUTH_INVALID_CREDENTIALS` | Không dùng Bearer challenge vì đây không phải Bearer resource request |
| Refresh cookie không hợp lệ | 401 `AUTH_REFRESH_INVALID` | Clear cookie; không Bearer challenge, không refresh đệ quy |
| Principal hợp lệ nhưng thiếu role, sai ownership, seller suspended, self-moderation | 403 `AUTH_FORBIDDEN` | Không refresh để chữa 403; không giả `insufficient_scope` khi không dùng OAuth scope |
| Origin/CSRF không hợp lệ | 403 `AUTH_CSRF_REJECTED` | Không thực hiện mutation, kể cả logout |
| DTO sai / conflict email | 400 `VALIDATION_FAILED` / 409 `AUTH_EMAIL_UNAVAILABLE` | Lỗi miền, không đổi thành 401/403 |
| Không có resource sau khi đã xác thực/kiểm quyền | 404 mã miền | Ownership mismatch mặc định 403; conceal bằng 404 là thay contract riêng cần owner review, không tự áp dụng |
| Không thể tải key tin cậy hoặc không kiểm tra được authorization dependency | 503 `AUTH_VERIFICATION_UNAVAILABLE` hoặc `AUTHORIZATION_DEPENDENCY_UNAVAILABLE` | Fail-closed; không refresh tự động, không trả allow |
| Auth bị throttle | 429 `AUTH_RATE_LIMITED` | `Retry-After` giây; limiter thực thi và ngưỡng thuộc implementation TV1 |
| Upstream route đã biết không kết nối/timeout | 503 `GATEWAY_UPSTREAM_UNAVAILABLE` / 504 `GATEWAY_UPSTREAM_TIMEOUT` | Không đổi thành 401 hoặc tự retry POST |

Với protected route, xác thực trước, sau đó kiểm role/ownership/validation theo miền. Public GET không có token vẫn hoạt động; nếu có Bearer sai thì trả 401, không âm thầm hạ xuống anonymous. Access token chỉ được gửi ở `Authorization: Bearer ...`, không query/body. Bearer challenge/status dựa trên [RFC 6750 §§2–3](https://www.rfc-editor.org/rfc/rfc6750.html#section-3); schema lỗi và role policy là đề xuất riêng.

FE shared chỉ thử refresh một lần cho protected request 401 `AUTH_ACCESS_REQUIRED`/`AUTH_ACCESS_INVALID`; không cho login/refresh/logout, 400, 403, 429 hoặc 5xx. Refresh thành công mới xét replay request theo khả năng an toàn: write nhạy cảm không retry mù; checkout giữ nguyên `Idempotency-Key` + body, theo contract TV3. Refresh thất bại 401 thì xóa session memory và yêu cầu đăng nhập lại; 503 giữ trạng thái lỗi tạm thời, không tự suy ra sai mật khẩu.

**Tương thích PR #7:** hiện Category write trả 403 `CATEGORY_WRITE_AUTH_NOT_CONFIGURED` kể cả anonymous do chủ ý deny-all; đây chưa phải JWT contract 401/403 mới. Giữ nguyên code/test TV2 trong nhiệm vụ này; khi tích hợp v1.0, TV2 triển khai verifier rồi đổi negative tests/error theo PR riêng. Không mở write bằng `permitAll` để cho FE chạy.

## 8. Gateway routing, CORS và internal boundary

Route dùng allowlist method/path cụ thể; không bật discovery locator tự động hoặc wildcard route toàn service. Không strip `/api` khi downstream controller nhận nguyên path. Không rewrite lỗi miền hoặc tự retry mutation. Unknown path/method không forward; trả 404/405 phù hợp. Chặn `/internal` và `/internal/**` từ public Gateway bằng 404 trước routing, kể cả ADMIN.

| Route public dự kiến | Upstream | Chính sách |
|---|---|---|
| Chính xác các endpoint `/api/auth/*` ở mục 5 | auth-service | register/login/refresh/logout theo cookie/CSRF; chỉ `/me` yêu cầu Bearer |
| `GET/PATCH /api/users/me` | user-service | Bearer; identity từ sub; không tự mở cả `/api/users/**` |
| `GET/HEAD /api/categories`, `GET/HEAD /api/categories/{id}` | product-service | Theo read contract PR #7 sau khi PR được merge |
| `POST /api/categories`, `PUT/DELETE /api/categories/{id}` | product-service | Chưa mở ghi cho tới khi TV1/TV2 duyệt ADMIN + JWT integration |
| Seller/Store/Product/Cart/Order/Inventory/Notification/Review/Finance | Owner nghiệp vụ tương ứng | Chưa chốt path/method/DTO trong tài liệu TV1; deny-default cho tới khi đăng ký route + security review, không đoán prefix/route trùng |

`/actuator/**`, Eureka, Config Server và admin infrastructure không public-route theo wildcard; không có JWKS trong MVP. Health cho vận hành qua mạng/cổng nội bộ; không coi Actuator exposure sẵn có là API client được phép truy cập. Downstream API không được publish port ra mạng public để bypass Gateway; vẫn verify JWT khi gọi trực tiếp.

Gateway xóa header identity/role do client có thể giả, gồm `X-User-Id`, `X-Customer-Id`, `X-Seller-Id`, `X-Store-Id`, `X-Roles`, `X-Authorities`, `X-Authenticated-User`, cùng header internal credential; không biến chúng thành principal. Không thêm trusted identity header thay cho JWT. `Forwarded`/`X-Forwarded-*` chỉ tin từ proxy đã cấu hình; không dùng host do client gửi để suy ra issuer. `Authorization`, `Idempotency-Key`, `Origin`, `Content-Type` được giữ theo route; cookie/Set-Cookie chỉ phục vụ Auth, không forward refresh cookie sang business service; redact credentials trong tracing/log.

**CORS (OD-05):** chỉ Gateway xử lý CORS; allowlist origin chính xác theo env, local example `http://localhost:5173`, không `*` khi `allowCredentials=true`. Preflight cho method/header đã đăng ký được xử lý trước JWT, không mở quyền business. Methods GET/HEAD/POST/PUT/PATCH/DELETE/OPTIONS theo route; allowed headers `Authorization`, `Content-Type`, `Idempotency-Key`, `X-CSRF-Protection`; exposed headers `WWW-Authenticate`, `Retry-After`, `Location`. FE Auth request dùng credentials include; upstream lỗi 401/403 vẫn có CORS headers cho origin được phép. Origin/method/header không được phép phải bị từ chối. CORS không thay thế auth; non-browser vẫn phải vượt verifier/ACL.

**Internal OD-09 DECIDED:** `/internal/**` chỉ mạng nội bộ, không route qua Gateway; internal credential riêng từng caller→callee, không dùng signing secret JWT. Header `X-Internal-Token` mang opaque credential entropy cao; định danh caller từ credential đã verify chứ không từ `X-Service-Name`. Callee so sánh an toàn và allowlist method + path + operation cho từng pair, deny-default; không dùng shared token cho tất cả caller. Credential thiếu/sai trả 401 `INTERNAL_AUTH_INVALID`; pair hợp lệ sai endpoint/operation trả 403 `INTERNAL_FORBIDDEN`; dependency không kiểm được trả 503, không cho qua. Không public-route auth header này qua CORS/Gateway.

Allowlist phải được owner caller/callee ghi cụ thể ở PR tích hợp: Auth→User chỉ provisioning, Seller→Auth chỉ đồng bộ role, cùng các pair checkout/inventory theo owner; đây là phạm vi chức năng, chưa phải endpoint mới đã triển khai. Pair credential chỉ cho quyền của operation, không mặc nhiên cho read/patch tài khoản tùy ý. Secret cấp runtime riêng, không query/log/Git; callee hỗ trợ current+previous của đúng pair trong cửa sổ có hạn đã cấu hình. Planned rotation: callee nhận credential mới → caller chuyển → xác nhận traffic → retire cũ theo deadline; credential lộ retire ngay. Không đổi key pair khác hoặc JWT key chỉ vì rotation một pair. Test cross-pair token, revoked key, unknown token, wrong method/path và không fallback allow khi config thiếu.

Nếu operation đại diện người dùng, caller và callee đều verify original Bearer bên cạnh pair credential, rồi callee kiểm role/ownership/trạng thái hiện tại theo độ nhạy cảm; subject từ body không tạo authority. User JWT thiếu/sai trả 401 theo mã access, actor không có quyền trả 403. Async/system operation không giả token user: phải có system operation được allowlist riêng, kiểm payload facts từ nguồn tin cậy và provenance của task; ví dụ profile provision ở mục 2 không cấp quyền sửa profile. Pair endpoint/timeout/retry cụ thể thuộc PR caller/callee; không mở API trước khi có test. Không đổi baseline sang OAuth client credentials/mTLS.

## 9. Bàn giao để TV2–TV5 phát triển song song

| Consumer | Contract v1.0 do Technical Lead chốt | Chi tiết implementation cần phối hợp |
|---|---|---|
| TV2 | Opaque String `sub` → SellerIdentity trong verified adapter; ADMIN hiện tại cho moderation/Category; owner→seller→store resolve server-side | OD-01/02/03/09; role synchronization/versioning, live status, self-moderation, schema logical owner. Không đổi source PR #7 trong nhiệm vụ này. |
| TV3 | Cart/order customer identity từ `sub`; không nhận customerId làm authority; 401/403; Gateway giữ Idempotency-Key | OD-02/04/09; DTO địa chỉ/snapshot riêng; delegated actor tới seller/inventory và retry checkout |
| TV4 | SellerOrder/Store ownership qua TV2, seller live status; internal pair cho stock; notification recipient = `sub` | OD-03/07/09; quyền COD, policy khi SUSPENDED vẫn có đơn chưa hoàn thành, actor system/compensation |
| TV5 | Customer review ownership, Store isolation Finance, negative security matrix và lỗi chuẩn | OD-04/07; review eligibility/settlement snapshot theo owner, không quyết định P0/P1 thay nhóm |
| FE shared (TV1), feature UI TV2–TV5 | Một auth context/API client: Bearer memory, single-flight refresh, phân biệt 401/403/503, không lưu refresh JS | OD-04/05; UI role chỉ hiển thị, backend vẫn kiểm lại |

Fixture consumer có thể dùng opaque ID bất kỳ để test không parse UUID; fixture **Auth/DB/JWT tích hợp** phải sinh UUID account thật trong môi trường test. JWT JSON ở mục 3 minh họa wire semantics, không phải bản seed Auth DB. Seller fixture có CUSTOMER+SELLER và mapping 2 Store do TV2 cấp; thêm pending/suspended Seller, Customer khác và ADMIN để test âm. Không có secret/password/token ký sẵn trong Git; key test và token ngắn hạn sinh cục bộ, không tái sử dụng key deploy. Ghi issuer/audience/expiry an toàn mà không đăng token lên Issue/PR. Stub/mock chưa verify crypto gắn nhãn test-only, không cắm allow-all policy vào production.

Contract v1.0 đã chốt bởi Technical Lead theo mục 10; consumer review giúp phát hiện mismatch và hoàn thiện wire integration, không được ghi giả đã review. Mỗi owner triển khai PR riêng với contract tests caller/callee; không gộp vào PR tài liệu.

## 10. Architecture Decisions — DECIDED BY TECHNICAL LEAD

**Người quyết định tất cả 9 OD:** Lê Xuân Bảo, Technical Lead/TV1. **Ngày:** 2026-10-16. **Căn cứ:** chỉ thị nhiệm vụ chốt OD-01–OD-09 của Technical Lead trong phiên làm việc này; PR v1.0 và commit chứa tài liệu lưu quyết định vào lịch sử Git. PR #23 chỉ merge bản draft, không được viện làm bằng chứng consumer approval v1.0.

| ID | Trạng thái / quyết định chính thức | Chi tiết implementation chưa xác minh (không mở lại quyết định) |
|---|---|---|
| OD-01 | DECIDED — HS256, key ngẫu nhiên >=256 bit runtime; không RS256/JWKS | Secret injection, trusted instance inventory, kid/keyring rollout và incident drill |
| OD-02 | DECIDED — UUID account nội bộ, string opaque ở JWT/API; sub=accountId; claims mục 3; 15 phút/60 giây; CUSTOMER/SELLER/ADMIN | Issuer/audience thật theo env; UUID provider/schema và dữ liệu V2 gốc |
| OD-03 | DECIDED — register CUSTOMER, SELLER sau approval, ADMIN moderation; APPROVED+ownership; cấm self-moderation, stale JWT không vượt suspension | TV1/TV2 đồng bộ role idempotent/versioned; current-state adapter; policy đơn đang giao khi suspension |
| OD-04 | DECIDED — 5 Auth + 2 User APIs, giữ DTO/error v0.1 theo mục 5/7 | Validation/REST edge tests, throttling thresholds, password encoder hỗ trợ đầy đủ giới hạn |
| OD-05 | DECIDED — opaque refresh hash, HttpOnly cookie, Secure HTTPS, SameSite=Lax, rotation/reuse, family <=7 ngày; memory access; Origin/CSRF/CORS | DB atomicity, multi-tab coordination, lost response và cookie/browser deployment profile |
| OD-06 | DECIDED — revoke refresh sau commit; access offline tới exp+skew; quyền nhạy cảm kiểm hiện tại; không blocklist/introspection tự phát | Trusted status adapters/freshness, sensitive-operation inventory từng owner và failure tests |
| OD-07 | DECIDED — chỉ ADMIN xác nhận COD sau DELIVERED, idempotent; Store isolation; review của buyer đủ điều kiện; Finance kiểm backend | TV3–TV5 chốt order/review key, lifecycle, snapshot và transaction; TV1 không sửa nghiệp vụ |
| OD-08 | DECIDED — auth_db/accounts/roles/sessions; user_db/profiles/addresses; logical accountId, không cross-DB FK; sync idempotent | Local provisioning task/worker, wire command, recovery và database provisioning không phá dữ liệu |
| OD-09 | DECIDED — internal không public-route; credential riêng mỗi pair, endpoint allowlist, delegated actor; không dùng JWT secret làm internal credential | Pair inventory/path/operation, secret rotation và system-vs-user command tests |

**Compatibility:** PR #7 vẫn dùng `SellerIdentity(String)` chính xác/opaque nên tương thích wire ID, không cần sửa thành UUID. Category write và Product fallback hiện deny-all 403 cần PR TV2 tích hợp để thiếu JWT trả 401 và ADMIN hiện tại mới được ghi theo endpoint policy; giữ deny-default cho route khác. PR #20 dùng String buyer identity và AdminAccess boundary; bổ sung adapter thật trong PR TV5, không đổi review/finance business keys hoặc tính tiền. Draft v0.1 chưa chốt internal account type; UUID nội bộ ở v1.0 không cho phép tự đổi ID đã tồn tại. Không thấy persisted Auth/User schema hoặc token production trong tracked source; dữ liệu DB thực và V2 gốc chưa kiểm được, không kết luận không có dữ liệu.

Nếu source/dữ liệu mới mâu thuẫn: ghi mismatch, owner và migration/compatibility plan; dừng phần phụ thuộc nếu ảnh hưởng correctness/security, không xóa migration hay đổi ID/DTO âm thầm. Kế hoạch PR #19 chưa merge không ghi đè quyết định security TV1 hoặc contract nghiệp vụ của owner khác. Không xóa review lịch sử PR #7/#19/#23.

## 11. Xác minh và acceptance checklist cho implementation sau này

**Đã thực hiện trong nhiệm vụ tài liệu:** đọc README/CONTRIBUTING, toàn bộ tài liệu `docs/` hiện hữu; đối chiếu source/build/config TV1 tại baseline, Issue #1, kế hoạch/review #19 và integration boundary #7; kiểm trạng thái #22. Static document checks và diff được báo ở PR; không suy ra runtime PASS từ audit source.

**NOT_RUN — chưa có Auth/User/Gateway implementation trong phạm vi:** unit/integration JWT, DB, HTTP, CORS browser, refresh race, E2E và Gradle build/test. Các ca dưới đây là tiêu chí cho PR triển khai, không phải kết quả test:

| Nhóm | Ca bắt buộc / expected |
|---|---|
| JWT verifier cả Gateway + downstream | Token hợp lệ, expired/nbf/future iat, wrong issuer/audience/type/signature/alg, missing/invalid claims/roles, refresh-as-access; xác thực đúng 401/400; forged identity headers không đổi actor |
| Key profile đã duyệt | HS256 wrong secret, missing/unknown kid, old/new key rotation và emergency retirement; RS256/none bị từ chối; không allow khi key config thiếu |
| Roles/ownership | CUSTOMER không ghi Category/moderate; anonymous đúng 401 sau tích hợp; Admin self-moderation bị chặn; Seller A không đọc/sửa Store/SellerOrder B; Customer khác không đọc đơn; ADMIN không bypass scope ngầm |
| Seller dynamics | Pending không có quyền quản lý Store; suspended bị chặn dù JWT SELLER còn hạn; status dependency chết trả 503; kiểm role refresh sau approval/revoke |
| Auth/session | DTO fields lạ/role injection, email normalization/duplicate race, wrong credential không enum; refresh thiếu/expired/revoked, single use/reuse/concurrency, lost response, absolute expiry, logout khi access hết hạn, clear cookie và logout idempotent; access còn sống theo OD-06 |
| Boundary/CORS/CSRF | Allowed/disallowed Origin, preflight, credentials, lỗi 401/403 vẫn đọc được từ FE; forged internal token/header; user JWT gọi internal bị chặn, pair sai scope 403; internal/Actuator/Discovery/Config không lộ qua Gateway |
| Contract/FE | Caller/callee cùng DTO và roles mapping; profile chưa provision trả 404; FE chỉ một refresh và không loop/refresh 403/503; checkout replay giữ nguyên key/body, không retry POST mù |
| Integration/UAT | FE→Gateway→Auth/User thật; hai Seller/Store và Customer thật theo contract đã duyệt; khởi động lại không mất session/account DB; không secret/token trong Git/log |

v1.0 được Technical Lead chốt về kiến trúc; còn phải merge PR tài liệu và xác minh implementation gates trong [roadmap](TV1_AUTH_IMPLEMENTATION_ROADMAP.md). Dùng `Refs #1`, không `Closes #1`: tài liệu không hoàn thành Docker/Auth/User runtime và toàn bộ checklist W1. Không chuyển PHASE B hoặc tự merge trong nhiệm vụ này khi PR v1.0 còn chưa merge.
