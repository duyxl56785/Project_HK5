# Ownership và điều phối thay đổi

| Module/Path | Người phụ trách chính | Reviewer khi ảnh hưởng |
|---|---|---|
| backend/auth-service, user-service, api-gateway, discovery-server, config-server | TV2 Nguyễn Văn Duy | TV2 |
| docker/, frontend/src/app/, frontend/src/shared/ | TV2 | TV2 |
| backend/seller-service | TV2 Nguyễn Văn Duy | TV2 |
| backend/product-service/** (catalog, category, images, moderation) | TV2 | TV2 và TV5 nếu chung model/review |
| backend/product-service/** (reviews) | TV5 Nguyễn Minh Duy | TV5 và TV2 nếu đổi Product |
| backend/cart-service | TV3 Trần Văn Chính | TV3 |
| backend/order-service/** (checkout/order creation) | TV3 | TV3, TV4 nếu status/inventory |
| backend/order-service/** (fulfillment, COD) | TV4 Phạm Thế Cường | TV4, TV3 nếu đổi trạng thái chung |
| backend/order-service/** (finance, settlement) | TV5 | TV5, TV4 nếu đổi COD |
| backend/inventory-service, notification-service | TV4 | TV4 và TV3 nếu contract inventory |
| frontend/src/features/seller, catalog, product | TV2 | TV2 |
| frontend/src/features/cart, checkout, orders | TV3 | TV3, TV4 nếu fulfillment |
| frontend/src/features/fulfillment, inventory, notifications | TV4 | TV4 |
| frontend/src/features/reviews, finance | TV5 | TV5 |
| docs/QA.md, reports/ | TV5 | TV5 và TV2 khi nghiệm thu |

**Từ 01/11/2026:** TV1 Lê Xuân Bảo bàn giao hẳn cho TV2 toàn bộ phần việc và quyền quyết định của TV1: Auth, User, Gateway, Docker, cấu hình, CI và frontend dùng chung. Review, quyết định và bằng chứng đã ghi tên TV1 giữ nguyên, tài liệu lịch sử không đổi nghĩa. Issue #1, #8, #14 giữ nguyên người được gán để lưu lịch sử. PR Auth hoặc Gateway do TV2 viết cần một thành viên khác review.

**Từ tuần 4 (03/11/2026):** TV1 nhận một phần việc giới hạn, chỉ các mục ghi trong #153 và #158. Đây là việc được giao, không trả lại ownership: TV2 vẫn là owner và quyết định contract, CI, bảo mật của các vùng trên. PR của TV1 cho #153, #158 do TV2 review; nếu chạm miền của TV3, TV4 hoặc TV5 thì owner miền đó review thêm.

**Quy tắc:** Đường dẫn trên chỉ để tổ chức công việc. Mọi chỉnh sửa API, entity dùng chung, migration, trạng thái enum, router và API client phải báo trước qua Issue/PR. Không tự động coi mọi thư mục là độc quyền: business ownership quan trọng hơn đường dẫn.
