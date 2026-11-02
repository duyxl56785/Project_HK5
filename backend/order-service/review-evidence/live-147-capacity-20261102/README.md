# TV3 — Live #147 được duyệt, bị chặn bởi dung lượng Docker

**BLOCKED_TEST_ENVIRONMENT — 0 acceptance executed, 0 PASS, 0 FAIL, 0 SKIP, 12 NOT_RUN/BLOCKED.** Không còn blocker “chưa có phê duyệt stack”; TV2 đã cấp quyền. Blocker mới được đo thực tế trước khi startup để bảo vệ stack cũ.

## Source và quyền

- Nơi chạy: CODEX TERMINAL — Linux.
- Exact application HEAD: **`44426859cbcdcd6dd9ba0d5ce6f84f91a317256d`**.
- Git tree: **`20ca9d7212bc6d03e43cc9e14c42f654b7b8c728`**.
- Worktree kiểm thử riêng có sẵn: `/workspace/tv3-owner-147-44426859`, detached, sạch trước/sau; không cập nhật source HEAD.
- [Phê duyệt TV2](https://github.com/duyxl56785/Project_HK5/pull/147#issuecomment-6579014091): project `hk5-tv3-147`, tài nguyên mới, ports5173/18080 loopback; được dùng port override riêng không commit khi xung đột. Chỉ dọn tài nguyên thuộc project mới. Hai UNKNOWN không được phê duyệt fault injection.
- Đã đọc AGENTS/CONTRIBUTING/OWNERSHIP tại exact source và ownership mới trên main (#141, TV2 nhận phần TV1). Không thay nội dung hoặc áp dụng main vào worktree exact source này.

## Preflight đã thực hiện

Docker local socket hoạt động: **28.4.0, storage driver `vfs`, data-root `/var/lib/docker`**. Không kết nối Docker Desktop Windows/SSH/VPN, không expose Docker API. Node24.19.0.

Baseline có **15 container đang chạy, 16 volume**. Hai cổng `127.0.0.1:5173` và `127.0.0.1:18080` thuộc project cũ `tv3-live-145-20261101`. Port conflict được TV2 cho phép giải quyết bằng override, nhưng chưa tạo/khởi động override vì capacity gate đã chặn trước đó. Project mới chưa cấp port nào.

Filesystem Docker còn **2.240.303.104 bytes (~2,24 GB)**. Tạo đúng một container preflight mới, chưa start:

```bash
docker create --name hk5-tv3-147-vfs-preflight \
  --label com.docker.compose.project=hk5-tv3-147 \
  --label tv3.purpose=vfs-capacity-preflight \
  eclipse-temurin:21-jre true
```

Lệnh exit0. Free space giảm còn **1.560.096.768 bytes**: allocation quan sát được **680.206.336 bytes (~680 MB)** cho một container. Stack chính thức cần 11 container Java; ngoại suy khoảng **7,48 GB cho Java**, chưa tính PostgreSQL/Kafka/Redis/frontend/build/DB growth. Đây là ước lượng theo probe, không giả vờ đã đo cả stack. Ngay tổng logical image size của 11 Java containers cũng ~3,52 GB, lớn hơn disk còn trống; không thể coi disk hiện tại đủ an toàn.

Đã xác minh name/project label bằng Docker inspect, rồi `docker rm <exact-created-id>` exit0 cho **chính container preflight chưa chạy**. Không dùng prune, không dọn image/cache/volume/stack cũ. Dung lượng được thu hồi, không để container preflight tồn tại.

Không chạy `up.mjs`, gen-env, seed hoặc Playwright; không tạo DB, token, credentials hay bật demo/production flags. Không cố tạo đủ 15 container để chờ ENOSPC, vì làm đầy filesystem này có thể ảnh hưởng stack cũ. Không khởi động daemon khác hoặc chuyển Docker data-root.

## Acceptance disposition

| Ca | Trạng thái | Lý do |
|---|---|---|
| Two-Store browser Checkout + same-key replay | NOT_RUN/BLOCKED | Docker disk |
| Customer cancel + Seller reject, RELEASE once | NOT_RUN/BLOCKED | Docker disk |
| DISPATCHED, CONSUME once | NOT_RUN/BLOCKED | Docker disk |
| Negative same-key changed body | NOT_RUN/BLOCKED | Docker disk |
| Negative same-key changed shipping | NOT_RUN/BLOCKED | Docker disk |
| Negative expectedItems mismatch | NOT_RUN/BLOCKED | Docker disk |
| Negative Catalog price changed | NOT_RUN/BLOCKED | Docker disk |
| Negative insufficient stock/no partial reserve | NOT_RUN/BLOCKED | Docker disk |
| Negative Product inactive | NOT_RUN/BLOCKED | Docker disk |
| Browser Order History | NOT_RUN/BLOCKED | Docker disk |
| UNKNOWN claim-conflict | NOT_RUN/BLOCKED | Chưa có fault fixture/identity/receipt TV4+TV5 duyệt; thêm gate disk |
| UNKNOWN lost-response/timeout | NOT_RUN/BLOCKED | Chưa có fault fixture/identity/receipt TV4+TV5 duyệt; thêm gate disk |

Mười ca có quyền nhưng chưa chạy vì môi trường; hai ca UNKNOWN vẫn ngoài phạm vi thực thi. Không có assertion ứng dụng nào đã chạy rồi FAIL; không đổi FAIL thành BLOCKED. Không dùng các PASS lịch sử, PostgreSQL fixture/unit hoặc GitHub CI để thay kết quả live mới.

**JUnit/Playwright reports: NOT_GENERATED**, vì runner chưa thực thi. Không tạo fake testcase PASS hoặc `<skipped>` cho ca NOT_RUN. **SQL oracle: NOT_RUN** vì DB mới chưa khởi động; không lấy before/after Order/SellerOrder/journal/stock từ database cũ để giả bằng chứng. `results.json` là manifest trạng thái, không phải output thực thi Playwright.

## Bảo toàn và tiếp tục

`preservation.json` xác nhận 15 container cũ giữ nguyên IDs/StartedAt/restart counts/mount sets và vẫn running; toàn bộ 16 volume giữ nguyên tên. Không ghi shared `.env.demo`, secret, source hoặc dữ liệu DB. Không thay branch của thành viên khác, main, review/approve hay merge PR.

Cần **tăng dung lượng filesystem chứa Docker data-root**, giữ nguyên tài nguyên hiện hữu, trước khi tạo stack mới. Nên dự phòng khoảng12 GB free cho VFS + stack/build/data growth; đây là khuyến nghị capacity, chưa phải phép đo cả stack. Chỉ `/tmp` còn chỗ không giải quyết Docker data-root hiện tại. Sau khi môi trường đủ dung lượng, dùng nguyên HEAD/approval/project trên, kiểm tra port override riêng và chạy mười ca đã được duyệt. Hai UNKNOWN vẫn cần quyết định riêng của TV4/TV5.

Evidence raw local: `/tmp/tv3-live-147-20261102/`. `raw-input-sha256.json` giữ hashes raw; HTML page/raw Docker inspect chỉ giữ local, không publish surrounding page forms hay credential values. Public `approval-readback.txt` chỉ trích comment TV2 đã đọc. Lệnh/exit trong `commands.json`, số đo trong `vfs-capacity-preflight.json`, hash public trong `SHA256SUMS`.

Refs #3 #10 #16 #145 #147. Không tuyên bố Checkout/website/UAT hoàn thành.
