# Hướng dẫn Codex cho Project_HK5

Đọc `CONTRIBUTING.md`, `docs/OWNERSHIP.md`, Issue và PR liên quan trước khi thay đổi. Giữ nguyên quy định hiện có và công việc của các thành viên. Phần quy trình dưới đây áp dụng cho **TV5 Nguyễn Minh Duy**: Review F15, Finance F16, QA và tài liệu. Không thay thế quyền quyết định của owner miền khác.

## Phạm vi và kiểm tra đầu phiên

- Xác định phạm vi từ yêu cầu mới nhất của người dùng và tiêu chí Issue. Khi đang được giao tuần 1, chỉ làm rules, test nền, contract/schema draft, QA/UAT plan, tài liệu và đối chiếu; không tự triển khai API, persistence, frontend hay E2E của tuần 2–3.
- Đọc `git branch -vv`, `git status`, diff staged/unstaged/untracked, lịch sử và remote. Fetch khi có quyền mạng; xác minh trạng thái và **head/base thực tế** của PR trước push. Bảo toàn thay đổi chưa commit; không switch/reset/stash ghi đè công việc chưa kiểm tra.
- Issue #5 là đầu việc tuần 1. Ưu tiên PR #20 cho Review/Finance/QA; PR #21 cho bản sửa kế hoạch/contract tương ứng. Luôn kiểm tra PR còn mở hay đã merge, không giả định các số PR hoặc branch vẫn nhận thay đổi. Nếu đã có PR phù hợp thì cập nhật, không tạo bản trùng. PR mới phải đúng base, liên kết Issue và có kết quả kiểm thử.

## Quy trình sau mỗi phần việc nhỏ

1. Kiểm tra code/tài liệu và ownership, API/schema/event bị ảnh hưởng. Không tự chốt contract đang tranh luận hoặc sửa ngoài vùng được giao.
2. Chạy test/check liên quan; ghi lệnh, môi trường, exit code và bằng chứng thực tế. Tài liệu thuần có thể kiểm tra Markdown/link/diff; ghi runtime test NOT_RUN/không áp dụng nếu không chạy, không viết test hình thức chỉ để có PASS.
3. Xác định tiêu chí đã hoàn thành. Tick `[x]` trong file quản lý tương ứng khi có evidence; mục ghép nhiều yêu cầu chỉ tick khi đủ tất cả. Có thể thêm checklist nhỏ hơn để thể hiện phần đã đạt mà không tick nhầm chức năng production.
4. Kiểm tra secrets và `git diff --check`. Stage từng file đã kiểm tra, không `git add .` khi còn untracked chưa xác minh. Cache IDE, môi trường, credential và build outputs ngoài bằng chứng được giao không được đưa vào commit.
5. Commit nhỏ, thông điệp rõ mục tiêu và `Refs #...`; push đúng feature branch. Không commit/push trực tiếp main, không force push, không commit rỗng. Nếu đã commit thì chỉ push phần còn thiếu; nếu remote đồng bộ thì xác minh, không tạo thay đổi giả.
6. Sau push, xác nhận SHA remote/PR, reviewer và kết quả test. Cập nhật checklist Issue/PR với link commit/evidence. Không ghi đã push khi lệnh thất bại; không ghi PASS khi test thất bại.
7. Báo cáo ngắn bằng tiếng Việt rồi tiếp tục phần được phép có thể làm độc lập. Khi chỉ còn phụ thuộc owner, ghi rõ BLOCKED hoặc WAITING_REVIEW, không tự mở rộng phạm vi để tránh chờ.

## Reviewer GitHub

- Reviewer mặc định cần kiểm tra sau mỗi push là **`duyxl56785`**. Không tự yêu cầu chính tác giả PR review PR đó.
- Nếu tài khoản này đang ở requested reviewers thì giữ nguyên, không request trùng. Nếu đã review tại head hiện tại và không có thay đổi đáng kể thì giữ review đó, không lặp yêu cầu chỉ vì danh sách requested reviewers đã trống.
- Nếu chưa có review/request phù hợp thì thêm reviewer. Khi có thay đổi đáng kể cần xem lại sau review cũ, re-request một lần; cập nhật mô tả để nêu phần cần xem lại.
- Không request review trên PR đã đóng/merge. Kiểm tra quyền, tài khoản đăng nhập và phản hồi GitHub; nếu GitHub từ chối thì báo nguyên nhân thật, không tự nhận đã gán reviewer.
- Owner bị ảnh hưởng vẫn cần review theo OWNERSHIP/CONTRIBUTING; reviewer mặc định không thay review bắt buộc của TV1–TV4. Không tự approve, merge, close, xóa PR hoặc bỏ qua branch protection.

## Checklist, evidence và trạng thái

- Đọc lại Issue trước khi cập nhật. Bảo toàn nội dung, checklist và evidence lịch sử của người khác. Tick trên GitHub sau khi phần việc đã kiểm tra và commit/push thành công; cập nhật có kiểm tra phiên bản nội dung để tránh ghi đè chỉnh sửa mới.
- PASS/FAIL dành cho kiểm tra đã thực thi; NOT_RUN là chưa chạy và phải có lý do; BLOCKED là chưa thể thực hiện vì phụ thuộc cụ thể; WAITING_REVIEW là đã bàn giao nhưng chờ reviewer/owner.
- Tách unit, integration, mock và live E2E. Tách lượt chạy hiện tại, lượt trước của repo và số liệu lịch sử từ báo cáo V2; không cộng gộp hoặc suy PASS từ tên test/file.
- Rules/precheck trong bộ nhớ không chứng minh DB uniqueness, atomic settlement, JWT production hay Store-scoped query. Không tick toàn bộ F15/F16 hoặc Issue DONE khi các tiêu chí đó vẫn chưa được kiểm chứng.
- Raw log/XML dùng làm evidence phải giữ nguyên byte để SHA256 khớp; ghi rõ CRLF/trailing whitespace của generated evidence nếu diff-check báo, đồng thời kiểm code/tài liệu riêng. Không sửa log để che lỗi. Không commit token/password/secret hoặc dữ liệu khách hàng.
- Giữ Issue PARTIAL khi còn blocker/tiêu chí bắt buộc chưa đạt; không đóng Issue hoặc tự coi review một phần là hoàn thành toàn bộ.

## Bàn giao mỗi đợt

Ghi phần hoàn thành và đã tick, phần chưa hoàn thành, test PASS/FAIL/NOT_RUN/BLOCKED, commit SHA, branch đã push, PR, reviewer đã có/request/re-request và owner cần xác nhận. Chỉ ghi trạng thái cụ thể của phiên trong Issue/PR hoặc báo cáo, **không thêm trạng thái tạm thời vào AGENTS.md**.
