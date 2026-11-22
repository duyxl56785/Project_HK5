# TV1 W6 — Kiểm định Auth/Gateway trên Soopi frozen SHA

**Refs:** [Issue #223](https://github.com/duyxl56785/Project_HK5/issues/223), [audit cũ #153](https://github.com/duyxl56785/Project_HK5/issues/153#issuecomment-6623115300).
**Source được đọc:** `eaf3bf270255b80db4525f836bfae7e495dc8651` (frozen UAT 20/11). Branch PR tạo từ `main@dafb95f`, không sửa code chạy.

**Giới hạn phương pháp:** Rà mã nguồn/tài liệu và bằng chứng các lượt CI/HTTP đã có; **KHÔNG có fresh Windows/Docker/Java run** trong phiên này vì kết nối Chat On Steroids đang không khả dụng. “Đã sửa ở source” không đồng nghĩa fresh security UAT PASS hay production approval.

## 1. Bốn finding lịch sử

| Finding | Đối chiếu tại frozen SHA | Bằng chứng |
| --- | --- | --- |
| **P1 GET/HEAD đọc dữ liệu với JWT cũ sau lock/revoke** | **FIXED_IN_SOURCE**: #176 MERGED; `CurrentAuthorityFilter` kiểm quyền qua Auth cho GET/HEAD, trả 401 nếu revoked và 503 nếu Auth lỗi. TV2 trước đó có test HTTP đọc+ghi 401 ở `main@618f9ea`, không phải fresh frozen run của TV1. | [PR #176](https://github.com/duyxl56785/Project_HK5/pull/176); [source 26–80](https://github.com/duyxl56785/Project_HK5/blob/eaf3bf270255b80db4525f836bfae7e495dc8651/backend/security-common/src/main/java/com/projecthk5/security/CurrentAuthorityFilter.java#L26-L80); [live receipt](https://github.com/duyxl56785/Project_HK5/tree/e0eb9aadf205c24c8a3a4653fea6ae126689e66d/reports/minh-chung-kiem-thu/tv2-main-618f9ea-r06-e2e) |
| **P2 login lockout theo email / brute force nhiều email** | **FIXED_IN_SOURCE**: #177 MERGED. `AuthController` dùng canonical email + real client IP, thêm client bucket 50 failures/5 phút. Unit test và HTTP proxy rehearsal đã tồn tại, nhưng TV1 không chạy lại. | [PR #177](https://github.com/duyxl56785/Project_HK5/pull/177); [AuthController L69–85](https://github.com/duyxl56785/Project_HK5/blob/eaf3bf270255b80db4525f836bfae7e495dc8651/backend/auth-service/src/main/java/com/projecthk5/authservice/api/AuthController.java#L69-L85); [7 unit/66 HTTP rehearsal](https://github.com/duyxl56785/Project_HK5/blob/main/reports/minh-chung-kiem-thu/pr177-proxy-http/README.md) |
| **P2 register IP Gateway thay IP client** | **FIXED_IN_SOURCE**: `register:<clientAddress>`, trusted proxy allowlist; Nginx overwrite `X-Forwarded-For`; 20 registrations/client/5 phút trong source mới. | [AuthController L58–63,L131–161](https://github.com/duyxl56785/Project_HK5/blob/eaf3bf270255b80db4525f836bfae7e495dc8651/backend/auth-service/src/main/java/com/projecthk5/authservice/api/AuthController.java#L131-L161); [LoginThrottleTest L47–84](https://github.com/duyxl56785/Project_HK5/blob/eaf3bf270255b80db4525f836bfae7e495dc8651/backend/auth-service/src/test/java/com/projecthk5/authservice/api/LoginThrottleTest.java#L47-L84) |
| **P3 map throttle phình bộ nhớ / nhiều Auth replica** | **PARTIAL / còn giới hạn**: #177 thêm dọn window hết hạn khi >=10.000 keys (đã có unit regression); map vẫn **per-instance** và không có hard cap mọi key còn hiệu lực trong một cửa sổ. Multi-replica chưa được chứng minh. TV2 cần quyết định chấp nhận hạn chế single-replica local hay Issue cải thiện Redis/limit. | [LoginThrottle L10–49](https://github.com/duyxl56785/Project_HK5/blob/eaf3bf270255b80db4525f836bfae7e495dc8651/backend/auth-service/src/main/java/com/projecthk5/authservice/api/LoginThrottle.java#L10-L49); [unit L102–109](https://github.com/duyxl56785/Project_HK5/blob/eaf3bf270255b80db4525f836bfae7e495dc8651/backend/auth-service/src/test/java/com/projecthk5/authservice/api/LoginThrottleTest.java#L102-L109) |

## 2. Các ranh giới bảo mật rà tĩnh

- **JWT:** [`ContractJwtDecoder`](https://github.com/duyxl56785/Project_HK5/blob/eaf3bf270255b80db4525f836bfae7e495dc8651/backend/security-common/src/main/java/com/projecthk5/security/ContractJwtDecoder.java) pin HS256/`kid`, cấu hình issuer/audience, yêu cầu key >=256 bit; kiểm `sub,jti,roles,iat,nbf,exp`, TTL <=900s, skew <=60s. Chưa thử brute force/key rotation live.
- **Refresh/logout:** [`AuthService.java` L91–118](https://github.com/duyxl56785/Project_HK5/blob/eaf3bf270255b80db4525f836bfae7e495dc8651/backend/auth-service/src/main/java/com/projecthk5/authservice/service/AuthService.java#L91-L118) dùng refresh family lock, rotate token, replay revoke family, logout revoke family.
- **Cookie và CSRF/Origin:** [`AuthController.java` L87–125,L163–168](https://github.com/duyxl56785/Project_HK5/blob/eaf3bf270255b80db4525f836bfae7e495dc8651/backend/auth-service/src/main/java/com/projecthk5/authservice/api/AuthController.java#L87-L125) tạo refresh cookie `HttpOnly; Secure; SameSite=Lax; Path=/api/auth`; auth POST đòi allowlisted Origin và `X-CSRF-Protection: 1`.
- **Gateway:** [`application.yml` L10–54](https://github.com/duyxl56785/Project_HK5/blob/eaf3bf270255b80db4525f836bfae7e495dc8651/backend/api-gateway/src/main/resources/application.yml#L10-L54) strip các header identity/internal được liệt kê, chỉ route allowlist `/api/**`, không route `/internal/**` và `/api/inventory/**` ra ngoài. Không suy rằng mọi service-pair credential đều scoped đúng từ cấu hình Gateway.
- **Role/current authority:** [`JwtResourceServer`](https://github.com/duyxl56785/Project_HK5/blob/eaf3bf270255b80db4525f836bfae7e495dc8651/backend/security-common/src/main/java/com/projecthk5/security/JwtResourceServer.java) áp JWT stateless, 401/403; [`CurrentAuthorityFilter`](https://github.com/duyxl56785/Project_HK5/blob/eaf3bf270255b80db4525f836bfae7e495dc8651/backend/security-common/src/main/java/com/projecthk5/security/CurrentAuthorityFilter.java) check quyền read/write và giữ write reservation. Không coi thư viện dùng chung là bằng chứng mọi route nghiệp vụ được bảo vệ.
- **Giới hạn triển khai:** [`RUN_LOCAL.md`](https://github.com/duyxl56785/Project_HK5/blob/main/docs/RUN_LOCAL.md) nêu pair/COD flags default OFF và policy chỉ dùng demo cô lập, không duyệt production.

## 3. Regression bổ sung đúng phạm vi #223

Trong `backend/security-common/src/test/java/com/projecthk5/security/CurrentAuthorityFilterTest.java`, bổ sung 2 test không đổi production behavior:

1. `revokedAuthorityAlsoRejectsHeadWithoutTakingAWriteReservation`: HEAD khi revoked phải trả 401, gọi authority-checks và không có write reservation.
2. `headFailsClosedWhenAuthReturnsAnError`: HEAD khi Auth HTTP 500 phải trả 503, không mở đường đọc.

Test dùng mock HTTP server loopback hiện có, **không phải live Auth→Gateway→PostgreSQL UAT**.

**Lệnh kiểm chứng cần chạy:** `cd backend && ./gradlew :security-common:test --tests '*CurrentAuthorityFilterTest' --rerun-tasks --no-daemon --console=plain`.

**Hiện trạng:** `NOT_RUN_LOCAL`; chờ GitHub CI trên exact HEAD mới có thể xác nhận PASS/FAIL. Nếu CI fail, giữ PR Draft và sửa riêng test. Không sửa chính sách P3, production code, runtime flags hoặc secrets trong Issue này.

**Handoff:** TV2 cần rà P3 và giới hạn quyền; TV5 kiểm chứng scope/QA nếu cần. Chưa có review approval hoặc sign-off production.
