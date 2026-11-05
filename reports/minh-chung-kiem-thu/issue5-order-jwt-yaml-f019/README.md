# Issue #5 — xác minh finding JWT YAML của Order

Source được chạy: `main@f0190e54a18b263df5b5cc0e9f7987873d30617e`, Windows 03/11/2026. Finding lịch sử tại Issue #5 yêu cầu `application.yml` ánh xạ JWT HS256 từ môi trường thật và test cấu hình, thay vì chỉ inject decoder trực tiếp. Bản sửa nằm trong PR #28/#33 đã merge; YAML hiện có `JWT_ISSUER_URI`, `JWT_AUDIENCE`, current/previous kid và key base64.

Lệnh:

```powershell
.\backend\gradlew.bat -p backend :order-service:test --tests '*JwtEnvironmentConfigurationTest' --rerun-tasks --no-daemon --console=plain
```

Exit 0, **2 unit PASS / 0 FAIL**. Test dùng Spring `ConfigDataApplicationContextInitializer` đọc YAML thật: biến môi trường được bind và token HS256/audience đúng được kiểm; thiếu key phải fail-closed. [Log](gradle.log), [JUnit XML](TEST-JwtEnvironmentConfigurationTest.xml) và [SHA256](SHA256.txt) giữ byte gốc (`.gitattributes -text`). Không có secret thật: key trong test sinh ngẫu nhiên, không in ra log. Đây là xác minh đúng finding JWT YAML; **không** chứng minh toàn bộ PR #28/Finance/F15/F16/UAT PASS.
