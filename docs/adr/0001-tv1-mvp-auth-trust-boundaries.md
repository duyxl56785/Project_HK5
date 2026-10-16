# HS256 và ranh giới tin cậy Auth cho MVP

**Status:** accepted by Technical Lead Lê Xuân Bảo, 2026-10-16; chưa triển khai. **Refs:** #1, OD-01/OD-06/OD-08/OD-09 trong [Contract v1.0](../TV1_AUTH_IDENTITY_GATEWAY_CONTRACT.md). Đây là quyết định của Technical Lead, không phải bằng chứng TV2–TV5 đã review.

MVP giữ HS256 với key runtime ngẫu nhiên ít nhất 256 bit, verify tại Gateway/downstream; không triển khai RS256/JWKS để tránh thêm cơ chế key discovery trái hướng đã chốt. Mọi verifier giữ symmetric key đều có thể ký token giả, nên toàn bộ verifier thuộc cùng miền tin cậy; Auth chỉ là signer duy nhất theo quy ước vận hành. Rủi ro service bị xâm nhập có thể mạo danh ADMIN được chấp nhận cho MVP; phải giới hạn secret access, có rotation keyring/kid và emergency retirement. RS256 giảm phạm vi lộ signing capability nhưng là thay đổi kiến trúc cần ADR sau, không đưa ngầm vào PR implementation.

Access JWT offline có TTL 15 phút và skew tối đa 60 giây, không thể coi logout là thu hồi access tức thì. Logout revoke refresh family sau commit; thao tác quản trị/nhạy cảm kiểm quyền/trạng thái hiện tại qua nguồn tin cậy, fail-closed khi không xác minh được. Không thêm Redis blocklist/introspection chung. Internal credential riêng từng caller/callee không thay được delegated user authorization và không được dùng chung JWT key.

Auth/User giữ DB ownership riêng với accountId logical, không cross-DB FK hoặc distributed transaction. Account + durable profile provisioning task commit cục bộ; User insert-if-absent và worker retry để User downtime không làm mất đồng bộ hoặc rollback account đã có. Không chọn HTTP fire-and-forget vì mất request sau commit sẽ để profile thiếu vĩnh viễn; wire command/recovery phải kiểm chứng ở PR User. Không tạo migration ở PR quyết định.

Lịch sử proposal/alternatives: [draft v0.1 trong PR #23](https://github.com/duyxl56785/Project_HK5/blob/226dd2ca9c943df2fb524ddb77201b9b9d500d02/docs/TV1_AUTH_IDENTITY_GATEWAY_CONTRACT.md). Thay trust boundary, revocation guarantees hoặc sync ownership phải cập nhật ADR/contract trước khi đổi code.
