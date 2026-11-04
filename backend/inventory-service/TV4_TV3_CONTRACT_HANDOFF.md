# TV4 → TV3 Inventory handoff

Phạm vi này mô tả đúng provider-candidate hiện có; không mở HTTP route, không đổi
schema production mặc định và không bật `INVENTORY_ORDER_PAIR_ENABLED`.

## Đã hỗ trợ và đã kiểm chứng

| Nội dung | Provider behavior | Evidence |
|---|---|---|
| RESERVE | Chuyển `available` sang `reserved`, khóa SKU theo thứ tự và rollback toàn bộ multi-SKU khi một line lỗi | `InventoryReservationLedgerCandidateService.apply`; PostgreSQL candidate suite |
| CONSUME | Trừ từ held reservation; không cho vượt `held_quantity` | `applyHeldOperation`; `INSUFFICIENT_RESERVATION` regression |
| RELEASE | Trả held quantity về available; không cho release quá phần còn giữ | `applyHeldOperation`; partial/concurrent release regressions |
| Ownership | Reservation được bind bất biến với `reservationId`, `storeId`, `sellerId`, `sellerOrderId` và initial line fingerprint | `verifyOwner`, `RESERVATION_OWNER_MISMATCH`, `RESERVATION_FINGERPRINT_MISMATCH` tests |
| Idempotency | Cùng `operationId` + owner/type/line payload replay an toàn; payload/owner/type khác bị `IDEMPOTENCY_KEY_REUSED` | Concurrent same-key and changed-payload PostgreSQL tests |
| UNKNOWN | Lookup thiếu row trả `UNKNOWN`; không suy ra “chưa commit” và không tự RELEASE | `lookup`; two-Store/lost-response regression |
| Input safety | Null line, SKU padding, duplicate SKU, quantity không hợp lệ bị từ chối trước ledger/stock mutation | Commit `f99f395`; candidate PostgreSQL suite **15 PASS** |

## TV3 cần nối

1. Lưu durable một operation ID riêng cho từng bước RESERVE/CONSUME/RELEASE và
   exact immutable line payload trước HTTP; retry dùng lại đúng tuple đó.
2. Ghi `UNKNOWN` sau timeout/mất response; không tạo operation ID mới và không
   blind RELEASE/CONSUME. Reconciliation/status lookup qua HTTP chưa được mở.
3. Gắn reservation với SellerOrder trong Order transaction/outbox của TV3; provider
   hiện chỉ kiểm tra owner tuple được truyền vào, không tự xác thực JWT/Store hiện tại.
4. Chỉ phát CONSUME sau transition dispatch/handoff đã commit; RELEASE chỉ cho
   cancellation/rejection trước dispatch khi reservation còn held. Đây là C06 còn
   cần joint sign-off, không phải state machine mới của TV4.

## Phê duyệt còn thiếu

| Quyết định | Owner cần xác nhận | Hiện trạng |
|---|---|---|
| Order→Inventory pair credential, caller/system provenance và route scope | TV1 + TV3 + TV4 | PENDING; flag OFF |
| Product→SKU và current Store/Seller authority | TV2 | PENDING |
| SellerOrder status/outbox và C06 dispatch/release timing | TV3 + TV4 | REVISE/PENDING |
| COD/DELIVERED/refund/settlement separation | TV5 + TV3 + TV4 | PENDING; Finance flag OFF |

Không có phần nào trong handoff này là approval cho production integration, HTTP
exposure, COD paid hoặc settlement.
