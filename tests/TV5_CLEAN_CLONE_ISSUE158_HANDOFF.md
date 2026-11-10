# Bàn giao kiểm tra clone sạch trên máy khác cho Issue 158

Trạng thái ngày 08/11/2026: **NOT_RUN**. TV5 chưa truy cập một máy tính thứ hai và chưa có final main SHA được nghiệm thu. Tài liệu này là quy trình cho người thực hiện ở máy khác; chạy trên chính máy hiện tại không đáp ứng tiêu chí Issue 158.

## Trước khi chạy

Người thực hiện ghi tên máy/OS, Java/Node/npm/Docker/Compose phiên bản, thời gian Asia/Bangkok, người thao tác và thư mục clone mới. Kiểm tra URL repository và quyền clone. Chỉ dùng dữ liệu giả, account test và Compose project riêng; không sao chép `.env`, JWT, password hoặc volume từ máy dev. Không dùng `docker down -v` trên project chung, không `prune`.

TV1/CI owner xác nhận **full 40-character main SHA** cần kiểm, các PR bắt buộc đã merge và checks tại SHA đó PASS. Nếu chưa có xác nhận, chỉ được chạy clone/build/smoke tiền kiểm, không gọi final UAT/E2E PASS.

## Lệnh PowerShell trên máy khác

Chạy trong thư mục cha mới, không có `Project_HK5-clean` từ trước:

```powershell
if (Test-Path -LiteralPath .\Project_HK5-clean) { throw 'Đích clone đã tồn tại; chọn thư mục mới' }
git clone https://github.com/duyxl56785/Project_HK5.git Project_HK5-clean
if ($LASTEXITCODE -ne 0) { throw 'Clone FAIL; không dùng thư mục clone dở' }
Set-Location .\Project_HK5-clean
git status --short
$fullSha = git rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $fullSha -notmatch '^[0-9a-f]{40}$') { throw 'Không lấy được full SHA hợp lệ' }
Write-Output $fullSha
java -version
node --version
npm --version
docker version
docker compose version
```

So `$fullSha` với SHA do TV1 chốt. Nếu khác, dừng và xác minh lại main; không tùy tiện dùng branch hoặc receipt cũ. Từng lệnh dưới đây cần chạy riêng, lưu nguyên console output và `$LASTEXITCODE` trước khi chạy lệnh tiếp theo. Dừng ở bước lỗi; không cộng số test của một lượt chạy dở vào lượt PASS.

```powershell
# Từ thư mục gốc của clone sạch; dùng Gradle wrapper và lockfile của đúng SHA.
Set-Location backend
.\gradlew.bat build --console=plain
$backendExit = $LASTEXITCODE
Write-Output "backend build exit=$backendExit"
Set-Location ..
if ($backendExit -ne 0) { throw 'Backend build FAIL; lưu raw log và JUnit XML hiện có' }

Set-Location frontend
npm ci
$installExit = $LASTEXITCODE
Write-Output "frontend npm ci exit=$installExit"
if ($installExit -ne 0) { throw 'Frontend clean install FAIL' }
npm run typecheck
$typecheckExit = $LASTEXITCODE
Write-Output "frontend typecheck exit=$typecheckExit"
if ($typecheckExit -ne 0) { throw 'Frontend typecheck FAIL' }
npm test
$unitExit = $LASTEXITCODE
Write-Output "frontend unit exit=$unitExit"
if ($unitExit -ne 0) { throw 'Frontend unit FAIL' }
npm run build
$buildExit = $LASTEXITCODE
Write-Output "frontend build exit=$buildExit"
if ($buildExit -ne 0) { throw 'Frontend build FAIL' }
Set-Location ..
```

Lưu JUnit XML từ `backend/**/build/test-results/` kèm raw log và mã thoát; ghi rõ test nào cần Docker/Testcontainers nhưng không khởi tạo được (`BLOCKED_ENV`, không ghi PASS). Chọn thêm test scoped theo các module đã đổi từ SHA được duyệt. Frontend E2E (`npm run e2e`) là bước riêng, chỉ chạy khi stack cô lập và các gate của [ma trận UAT](TV5_UAT_ACCEPTANCE_CURRENT.md) đã được owner xác nhận; không coi `npm test` là E2E.

Cho rehearsal môi trường cô lập dùng [hướng dẫn UAT](../scripts/qa/uat/README.md): `.\scripts\qa\uat\Smoke-IsolatedUat.ps1 -Build`. Script chỉ kiểm Docker, migration, seed giả và Catalog, giữ Inventory pair/Finance policy OFF. Ghi project name và volumes trước/sau, lưu console raw log đã kiểm secrets, không xóa volume chung. E2E/final UAT hai Store chỉ chạy sau gate trong [ma trận UAT](TV5_UAT_ACCEPTANCE_CURRENT.md), với runner và phạm vi được owner duyệt riêng; không dùng smoke làm bằng chứng E2E.

Nếu owner đã duyệt stack/runner cô lập cho E2E trên máy khác, kiểm `frontend/playwright.config.ts`: suite dùng `E2E_BASE_URL`, mặc định `http://localhost:5173`, Chromium, một worker, không retry. Chỉ sau khi xác nhận URL trỏ tới **stack test cục bộ của máy đó**, thực hiện `Set-Location frontend; npx playwright install chromium; npm run e2e`, lưu mã thoát, HTML report và trace/screenshot gốc; nếu thiếu stack hoặc approval, ghi `BLOCKED`, không chạy riêng lệnh này để lấy số liệu giả.

## Receipt bàn giao

Lưu bản công bố đã kiểm secrets vào `reports/minh-chung-kiem-thu/issue158-clean-clone-<sha8>-<timestamp>/` trong branch evidence riêng; không ghi đè run cũ. Ghi manifest SHA256 từ byte của từng artifact sau khi chốt bản công bố. Không sửa raw XML/log để đổi kết quả; nếu raw chứa secret/PII, giữ nội bộ và chỉ công bố bản tóm tắt dẫn nguồn theo quy định nhóm.

| Trường receipt | Giá trị người chạy phải điền |
|---|---|
| Actor, máy, OS, thời gian Asia/Bangkok | `NOT_RUN` cho đến khi thực hiện trên máy khác thật |
| Main full SHA, CI URL, PR phụ thuộc | SHA 40 ký tự và các gate đã xác minh |
| Java/Node/npm/Docker/Compose, Compose project/volumes | Phiên bản và tên project cô lập thực tế |
| Backend build, frontend ci/typecheck/unit/build | Từng lệnh, exit code, PASS/FAIL/BLOCKED và JUnit/log |
| E2E hai Store | URL test cục bộ, lệnh, exit code, Playwright report/trace; `BLOCKED` nếu thiếu gate |
| Artifacts | Đường dẫn tương đối, SHA256, nguồn gốc raw hay derived summary |
| Kết luận #158/#18 | Chỉ đề nghị tick khi đúng acceptance; smoke không thay final UAT |

Không đăng credential/token/PII lên Issue. TV5 đối chiếu receipt với SHA và acceptance của #158/#18 trước khi đề nghị tick checklist. Không có receipt từ máy khác thì #158 giữ NOT_RUN.
