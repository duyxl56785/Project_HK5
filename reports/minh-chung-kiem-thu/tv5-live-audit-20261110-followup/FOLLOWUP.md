# TV5 LIVE follow-up 10/11/2026

Source kiểm thử: `78de5c08205b41986a5204a8150a9ef810507847`.

## A Pull Request

[Audit đầy đủ](github/report.md), snapshot complete 6/6, phân trang tất cả 163 PR: 6 OPEN (3 DRAFT), 121 MERGED, 36 CLOSED chưa merge. Không PR mở nào request TV5, không thread mở. #185/#182/#186/#179/#183/#184/#175 đã MERGED; không formal-review lặp. #187 đã có TV5 APPROVED tại đúng bc82486315c2135ae48112887da7a4b48538ed43, CI SUCCESS, chờ TV2/TV4. #170 approval cũ còn effective nhưng khác HEAD, NO_CHECKS; không coi đó là quyền thực thi UNKNOWN. #169/#188/#189 DRAFT; #173 conflict cần TV4 xử lý. #189 reviewer duyxl56785 đã request, không request trùng.

## B Issue

19 Issue OPEN. #5 141/219; #12 43/75; #157 5/6; #18 1/4; #162 3/9; #158 2/6. Tick mới: 0, không tick mục ghép bằng kết quả tiền kiểm. #157 chưa DONE vì còn acceptance #5/#12. Không sửa checklist của owner khác.

## C Công việc / D kiểm thử đã chạy

| Test tại main SHA trên | Kết quả | Evidence |
|---|---|---|
| Công cụ QA Python | 56 PASS, exit 0 | qa-tools-tests.log |
| QA Tester regression | 15 PASS, exit 0 | qa-tester-tests-installed.log |
| QA Tester build | PASS, exit 0 | qa-tester-build.log |
| QA Tester Playwright UI | 3 PASS, exit 0, 3 ảnh browser thật | qa-tester-ui-configured.log, screenshots/ |
| GitHub audit | 6/6 complete, exit 0 | github/snapshot.json |

Lần đầu QA Tester thiếu archiver: 8 PASS/1 file FAIL. Đã npm ci --ignore-scripts từ lockfile trong worktree riêng, không sửa source. UI lần đầu thiếu SOOPI_QA_SCREENSHOT_DIR: 3 FAIL; sau khai báo thư mục ảnh riêng 3 PASS. Giữ cả raw log lỗi lẫn kết quả sau sửa môi trường. Đây không phải F15/F16 production hoặc final UAT.

## E Git / F blocker và owner tiếp theo

- Evidence bổ sung vào nhánh PR #189 đang có; không sửa code thành viên, DOCX/PPTX DRAFT, .deck-build hoặc WIP gốc. Không merge hoặc push main.
- #18: BLOCKED_APPROVAL/NOT_RUN. Cần TV2/business và TV3/TV4 xác nhận runner/stack/gates/cleanup đúng SHA. Docker hoạt động không thay approval.
- #162: DRAFT; chưa rà/render toàn bộ 57 trang, speaker notes qua PowerPoint và ảnh final B.1–B.5. Không công bố sẵn sàng nộp.
- #158: NOT_RUN trên máy khác; owner TV1/người có máy khác cung cấp receipt.
- #161 UNKNOWN claim-conflict: BLOCKED_DESIGN; lost-response vẫn cần receipt/runner/approval đúng scope. Không mở cờ Finance/Inventory.
- #157 và #5/#12: PARTIAL, còn tiêu chí nghiệm thu thật; không đóng.
- #187: WAITING_REVIEW TV2/TV4; #169/#173: TV4 xử lý. Không còn yêu cầu review mở đang giao TV5 theo snapshot; không suy mọi PR merge-ready.
- Branch protection admin REST: chưa xác minh đầy đủ nếu cần quyền admin; vẫn giữ các REVIEW_REQUIRED GitHub quan sát được.

Đây là đợt follow-up có bằng chứng, không phải hoàn thành toàn bộ yêu cầu. Những việc chưa thực hiện không được chuyển thành PASS. SHA256SUMS bảo toàn raw log; không có secret/token hoặc dữ liệu khách hàng trong snapshot xuất bởi công cụ read-only.
