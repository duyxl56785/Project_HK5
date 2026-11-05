# Precheck R06 Admin revoke — chưa phải test thu hồi quyền

Source: `main@f0190e54a18b263df5b5cc0e9f7987873d30617e`, Windows ngày 03/11/2026. Lệnh:

```powershell
.\backend\gradlew.bat -p backend :security-common:test --tests '*CurrentAuthorityFilterTest' --rerun-tasks --no-daemon --console=plain
```

Exit 0: **5 unit PASS / 0 FAIL**, log và JUnit XML nguyên bản trong thư mục này, SHA256 ở [SHA256.txt](SHA256.txt). Một test hiện hành tên `readsAndInternalRoutesSkipTheCheck` xác nhận chính sách **GET không gọi Auth**; đây là PASS của hành vi hiện tại, **không** chứng minh R06 Admin revoke đã PASS.

Đối chiếu source: `CurrentAuthorityFilter` bỏ qua GET/HEAD/OPTIONS; `ProductWebConfig` bảo vệ `/api/admin/**` bằng role trong JWT; `ModerationController` có `GET /api/admin/reviews`. **Suy luận từ source:** JWT ADMIN cũ có thể tiếp tục qua GET trong TTL nếu quyền ở Auth bị thu hồi; điều này chưa được kiểm bằng HTTP live. `AuthService.disable` từ chối disable ADMIN và API hiện không có thao tác revoke ADMIN công khai, nên TV5 không chạy được hai request trước/sau revoke thật qua Gateway. Owner TV1/TV2 phải xác nhận fixture thu hồi quyền trên stack cô lập và mã lỗi kỳ vọng (filter write hiện trả 401 khi reservation bị từ chối, trong khi contract mô tả 403 cho principal còn hợp lệ mất role). Trạng thái R06 Admin revoke: **NOT_RUN/BLOCKED_OWNER**.

Gói này không chứa credential, token hoặc dữ liệu khách hàng. Không sửa Auth/Gateway, không gọi unit PASS là E2E hoặc production acceptance.
