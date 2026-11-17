# TV3 → TV2 — Chạy rehearsal Checkout trên máy demo đã duyệt

Source cho lượt rehearsal này: `240d0280bdde66f7d9055e24ded75f1e9417edfb` của `duyxl56785/Project_HK5`. Đây là rehearsal trước freeze. TV2 thực hiện ảnh chính thức và UAT khi có frozen SHA.

## Lấy đúng bốn file vào worktree theo source SHA

Từ **Windows PowerShell** ở root clone Project_HK5 của TV2, xác nhận origin là repository nhóm. Tạo worktree mới, detached ở source SHA, rồi chỉ restore các file test từ branch bàn giao. Ghi lại cả SHA ứng dụng và commit của bộ test.

~~~powershell
$sha = '240d0280bdde66f7d9055e24ded75f1e9417edfb'
$handoff = 'handoff/tv3-checkout-rehearsal-20261117'
$remoteRef = "refs/remotes/origin/$handoff"
git remote -v
git fetch origin "refs/heads/$($handoff):$remoteRef"
if ($LASTEXITCODE -ne 0) { throw 'Fetch handoff thất bại' }
git rev-parse $remoteRef
if ($LASTEXITCODE -ne 0) { throw 'Thiếu commit handoff' }
$qa = Read-Host 'Đường dẫn mới cho worktree rehearsal'
if (Test-Path -LiteralPath $qa) { throw 'Đường dẫn đã tồn tại' }
git worktree add --detach $qa $sha
if ($LASTEXITCODE -ne 0) { throw 'Tạo worktree thất bại' }
Set-Location -LiteralPath $qa
git restore --source "origin/$handoff" --worktree -- frontend/e2e/tv3-demo-rehearsal.spec.ts Run-TV3DemoRehearsal.ps1 verify_tv3_rehearsal.py TV3_TV2_LIVE_RUN_HANDOFF.md
if ($LASTEXITCODE -ne 0) { throw 'Restore bộ test thất bại' }
$actual = git rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $actual -cne $sha) { throw 'Sai source SHA' }
git diff --exit-code HEAD --
if ($LASTEXITCODE -ne 0) { throw 'Tracked source bị thay đổi' }
git status --short
~~~

Kỳ vọng bốn file bàn giao xuất hiện với trạng thái **untracked**; app HEAD vẫn là `240d028`. Theo [quyết định của TV2](https://github.com/duyxl56785/Project_HK5/issues/160#issuecomment-6938080387), máy chạy là **Windows PowerShell 5.1**, không phải PowerShell 7. Runner đã sửa phần ghi JSON UTF-8 no BOM và đường dẫn tương đối để tương thích PS 5.1; cần TV2 **thực chạy xác nhận** `run.json`/`SHA256SUMS.txt` trước khi gọi PASS.

### Dựng một stack mới hoàn toàn riêng — thao tác trên máy TV2

**Chỉ sau khi TV2 đọc và chấp thuận commit bộ test mới nhất.** Chạy trong **Windows PowerShell 5.1**, từ root worktree detached source `240d028`. Không chạy trên máy TV3 và không sao chép tài khoản/secret từ máy TV3.

1. File `docker/compose.demo.yml` có `name: hk5-demo`; phải đặt `COMPOSE_PROJECT_NAME=hk5-tv3-rehearsal` để Compose sử dụng **project name khác**. Kiểm tra bằng `docker compose ls`/container label, không chỉ tin vào tên biến.
2. Cổng `5173` và `18080` được bind cố định. **TV2 tự kiểm và dừng stack hk5-demo do TV2 quản lý nếu đang chiếm hai cổng; không dùng lệnh down -v hoặc xóa volume của stack cũ.** Runner không tự dừng hay tạo stack nào.
3. `frontend/e2e/support.ts`, runner và `up.mjs` đều đọc `.env.demo` ở root worktree. TV2 chép **file `.env.demo` của chính máy TV2** vào root worktree mới và giữ private/git-ignored, không commit hay in nội dung. `up.mjs`/gen-env dựng lại `docker/secrets` trong worktree này. Không chép `docker/secrets` hay volume từ checkout khác.
4. Tạo và seed stack trên project/volume riêng sau khi TV2 quyết định cho phép cả hai cờ **chỉ trong demo local**:

~~~powershell
# Windows PowerShell 5.1 — root worktree source 240d028, sau khi TV2 kiểm tra cổng.
if (Test-Path -LiteralPath '.env.demo') { throw 'Worktree đã có .env.demo; xác minh lại trước khi chạy' }
$sourceEnv = Read-Host 'Đường dẫn tuyệt đối tới .env.demo do TV2 quản lý trên chính máy TV2'
if (-not (Test-Path -LiteralPath $sourceEnv -PathType Leaf)) { throw 'Không tìm thấy .env.demo nguồn' }
Copy-Item -LiteralPath $sourceEnv -Destination '.env.demo' -ErrorAction Stop
$env:COMPOSE_PROJECT_NAME = 'hk5-tv3-rehearsal'
$env:E2E_BASE_URL = 'http://localhost:5173'
docker compose ls
# Chỉ khi TV2 đã xác nhận 5173/18080 không thuộc stack cần giữ.
node scripts/demo/up.mjs --approved-inventory-pair --approved-finance-policy
if ($LASTEXITCODE -ne 0) { throw 'Dựng hoặc seed rehearsal thất bại; không chạy Checkout' }
~~~

`up.mjs` tạo các container và seed giả, **thực hiện ghi dữ liệu**; đây là bước TV2 phải chủ động chạy, không phải preflight read-only. Không chạy hai stack song song trên cùng cổng. Lệnh này **không** bật flags cho production hoặc UAT chính thức; các cờ mặc định OFF ở môi trường khác.

5. Sau khi dựng, `E2E_BASE_URL` giữ đúng `http://localhost:5173` (Origin whitelist của stack). Đọc lại `docker compose ls`, nhãn/volumes và HTTP readiness bên dưới trước khi chạy test.

## 1. Kiểm trước khi chạy trên chính máy có stack

Chạy bằng **Windows PowerShell**, tại root checkout QA của TV2. Dùng worktree vừa tạo và đúng tên Compose project TV2 đã duyệt.

```powershell
Set-Location -LiteralPath (Read-Host 'Root checkout được TV2 duyệt')
$sha = '240d0280bdde66f7d9055e24ded75f1e9417edfb'
git rev-parse HEAD
git status --short
docker context show
docker version --format '{{.Server.Version}}'
docker compose ls
$project = 'hk5-tv3-rehearsal'
docker ps --filter "label=com.docker.compose.project=$project" --format '{{.Names}} {{.Status}} {{.Ports}}'
docker port "$project-frontend-1" '80/tcp'
docker port "$project-api-gateway-1" '8080/tcp'
$env:E2E_BASE_URL = Read-Host 'HTTP loopback origin của frontend đã xác minh'
curl.exe --noproxy '*' --max-time 5 -sS -o NUL -w 'Frontend HTTP %{http_code}' "$env:E2E_BASE_URL/"
curl.exe --noproxy '*' --max-time 8 -sS -o NUL -w 'Catalog qua Gateway HTTP %{http_code}' "$env:E2E_BASE_URL/api/catalog/products?size=1"
```

Điều kiện kiểm tra đạt khi SHA đúng, tracked tree sạch, frontend và Catalog qua Gateway đều 200, project chạy trên máy này, label work directory khớp checkout và PostgreSQL dùng volume riêng. Project phải là hk5-tv3-rehearsal và dùng volume mới dành riêng cho rehearsal. TV2 xác nhận provenance build trên SHA này và seed Customer/Seller1/Seller2 giả; runner ghi image ID nhưng không tự chứng thực SHA của image. Hai gate Inventory/Finance phải đã bật sẵn đúng quyết định TV2. Runner kiểm giá trị thực trong container và không thay chúng.

Cho một người chạy. Customer demo phải có Cart trống; test tự dừng trước POST nếu giỏ không trống. Nếu một lần trước có checkout-attempt.json ghi OUTCOME_UNVERIFIED, hoặc đã bấm COD nhưng không rõ kết quả, giữ trạng thái và đối soát trước. Không chạy lại, đổi key, dọn giỏ hoặc xóa dữ liệu để vượt qua.

## 2. Chạy đúng một lượt

Sau khi TV2 đọc bộ test và xác nhận các điều kiện ở trên, chạy trong cùng cửa sổ **Windows PowerShell**, từ root và với biến của bước 1.

```powershell
.\Run-TV3DemoRehearsal.ps1 -ExpectedSha $sha -ApprovedIsolatedStack $project
```

TV2 đã [đồng ý dùng mode PostgreSQL cho stack disposable này](https://github.com/duyxl56785/Project_HK5/issues/160#issuecomment-6938080387) để kiểm `reserved` và đúng một `RESERVE`. Sau khi TV2 xác minh quyền, **thêm `-UsePostgresReadOnly` trong chính lượt đầu tiên**:

~~~powershell
.\Run-TV3DemoRehearsal.ps1 -ExpectedSha $sha -ApprovedIsolatedStack 'hk5-tv3-rehearsal' -UsePostgresReadOnly
~~~

**Lưu ý bảo mật/phạm vi:** cờ này dùng tài khoản PostgreSQL `hk5` qua `docker exec` với mỗi truy vấn bọc trong `BEGIN READ ONLY ... ROLLBACK`; **không** phải tài khoản DB chỉ được cấp quyền SELECT. Chỉ sử dụng vì TV2 đã cho phép trên stack riêng sẽ xóa sau rehearsal, không áp dụng production. Nếu quyền PostgreSQL thực tế chưa được TV2 xác nhận, chạy **API-only** và ghi `PARTIAL` thay vì tự suy ra single reserve.

Nếu thiếu dependency hoặc Chromium, dùng `-InstallDeps` **trong cùng một lượt đầu**, không chạy test ở cả hai mode để gom evidence. Bài này tạo đúng một Order và thay đổi Cart/stock; không chạy lại khi trạng thái Checkout chưa được đối soát.

Runner in đường dẫn riêng trong TEMP. Sau khi chạy, gọi verifier ở root:

```powershell
$folder = Read-Host 'Thư mục evidence do runner in'
py -3 -B .\verify_tv3_rehearsal.py $folder --expected-sha $sha
```

Verifier 0 = cấu trúc và checksum PASS trong phạm vi PostgreSQL được phép; 2 = PARTIAL hoặc lượt chưa hoàn tất; 1 = lỗi integrity/contract receipt. Runner exit 0 với API-only chỉ cho biết các phép kiểm của mode đó chạy thành công.

## 3. TV2 trả lại evidence

- Full source SHA, provenance build nếu có, Compose project, image IDs, thời điểm, mode và runner/Playwright exit code.
- `run.json`, `playwright.log`, `playwright-results.json`, `checkout-attempt.json`, `receipt.json` và `SHA256SUMS.txt`. Xác minh `run.json` và manifest **được tạo trên Windows PowerShell 5.1 thật**, không chỉ parse syntax. Nếu thất bại, gửi những file thực có và trạng thái đã dừng ở đâu.
- Hai PNG REHEARSAL sau khi kiểm pixel và masking. Trace/video bị tắt có chủ đích vì chứa phiên xác thực. Ghi `trace=OFF` trong bàn giao, dùng JSON report và log đã rà secrets để xem lỗi.
- Đối chiếu Hình 6.5 với đúng hai card Cart. Đối chiếu Hình 6.6 với hai card Order Detail và receipt chứng minh hai SellerOrders khác ID/Store cùng root. Receipt để TV5 đối chiếu. Không ghép PNG REHEARSAL vào Word làm ảnh frozen SHA.

CI hai Store và order journey là nền tảng. Lượt này kiểm bổ sung trên **cùng một run**: API ownership hai Seller với chéo 404, anonymous 401, Seller đọc root bị từ chối, replay cùng request giữ Order ID, shipping đổi trả 409, số đơn giữ nguyên và không tăng reserve qua DB. CI có thể kiểm một số hành vi riêng, nhưng không có receipt này gắn với ảnh và exact run, và không có CI tại exact 240d028 trong lần đối chiếu trước. UNKNOWN, RET-05 và voucher tại Checkout nằm ngoài rehearsal.
