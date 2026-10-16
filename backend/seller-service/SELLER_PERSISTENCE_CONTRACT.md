# Seller Persistence — contract tối thiểu đã được xác nhận

Ngày 16/10/2026, chủ dự án cung cấp xác nhận của TV1 Technical Lead trong phiên làm việc và cho phép TV2 triển khai. TV2 ghi lại tại [Issue #2](https://github.com/duyxl56785/Project_HK5/issues/2#issuecomment-6205790432); đây không phải review GitHub được tạo thay TV1. Contract này chốt Seller persistence, không freeze toàn bộ Auth/Gateway draft.

## Identity và schema

- Auth dùng Account ID UUID nội bộ; JWT sub truyền string. ownerReference chỉ từ verified sub, không body/header tự khai. Consumer giữ opaque string, không parse UUID, trim/lowercase; Seller ID là UUID khác.
- Bảng seller_profiles ở DB riêng seller-service: id UUID, owner_reference VARCHAR(128) NOT NULL UNIQUE, status VARCHAR(16), review_reason VARCHAR(500), version BIGINT.
- Owner dùng collation C cho equality/unique chính xác, không FK sang Auth/User DB. Không thêm profile fields hoặc quyết định Store boundary.
- Một account tối đa một hồ sơ. Tạo PENDING_REVIEW; resubmit REJECTED cập nhật cùng Seller ID. Giữ PENDING_REVIEW/APPROVED/REJECTED/SUSPENDED.
- REJECTED/SUSPENDED bắt buộc reason không blank, tối đa 500 ký tự; trạng thái khác NULL. Reason giữ cách strip của domain. Persistence tính Unicode code points để khớp PostgreSQL varchar; owner không normalize/cắt chuỗi.

## Transaction và concurrency

- SellerPersistenceService dùng REQUIRED, cùng transaction với caller. Flush không có nghĩa outer transaction đã commit; outer rollback undo registration/moderation/version.
- UNIQUE owner là guard cuối của concurrent registration, không exists-before-insert. Chỉ constraint owner tương ứng map thành SELLER_ALREADY_EXISTS.
- JPA @Version bảo vệ cập nhật cạnh tranh. Command nội bộ nhận expectedVersion; version cũ trả SELLER_VERSION_CONFLICT kể cả sau reject/resubmit quay lại PENDING_REVIEW. DB flush race cũng map conflict; không retry moderation mù.
- Không advisory lock hoặc REQUIRES_NEW; Category implementation giữ nguyên.

## Wiring và security

- Giữ domain record/lifecycle/policy; entity riêng. Internal result là ID/version/domain snapshot, không phải HTTP DTO hoặc exposed JPA entity.
- Orchestration cần SellerAccessPolicy và phải được wire thành Spring bean để transaction proxy hoạt động. Chưa auto-register bean production khi thiếu verified policy. Test configuration wire bằng fixture policy; không có default allow-all policy production.
- Actor/permission được kiểm trước lookup khi cập nhật, sau đó domain kiểm owner/state/self-moderation. Future adapter phải ràng buộc actor với principal hiện tại và chỉ verified ADMIN được moderation. SELLER không thay APPROVED/ownership.
- Không thêm Controller/JWT decoder/Gateway route/Store/Product. Không có endpoint ghi mới công khai. Fixture policy không dùng cho production.
- SELLER_DB_URL/USERNAME/PASSWORD riêng service, Flyway enabled, JPA ddl-auto validate. HTTP error contract vẫn thuộc PR tích hợp riêng.

## Acceptance

PostgreSQL tests phải chứng minh durable registration, DB uniqueness/exact owner, concurrent insert thực sự chờ constraint, same-ID resubmit, lifecycle/reasons, REQUIRED rollback, stale expectedVersion và hai transaction cùng loaded version không cùng moderation thành công. Không thay PostgreSQL bằng H2/mock.

JWT/API/Gateway/Store/Product/E2E là follow-up. Mock/fixture policy không chứng minh verified JWT. Issue #2 vẫn PARTIAL; chỉ nghiệm thu persistence sau PostgreSQL test thực tế.
