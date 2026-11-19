# TV5 — Hướng dẫn bàn giao evidence Final UAT

**Áp dụng:** TV2, TV3, TV4 và TV5
**Ngày dự kiến:** 21/11/2026
**Checklist case:** [TV5_FINAL_UAT_EXECUTION_CHECKLIST_20261121.md](TV5_FINAL_UAT_EXECUTION_CHECKLIST_20261121.md)
**Ma trận gốc:** [TV5_FINAL_UAT_W5_C2_MATRIX_20261119.md](TV5_FINAL_UAT_W5_C2_MATRIX_20261119.md)
**Evidence kit:** [reports/minh-chung-kiem-thu/tv5-final-uat-20261121/](../reports/minh-chung-kiem-thu/tv5-final-uat-20261121/), gồm manifest mẫu, case-evidence map 37 ID, mẫu result/defect/biên bản/P0-P1-P2. Các file mẫu không phải receipt.

## 1. Quy tắc tiếp nhận

- Evidence chỉ hợp lệ cho Final UAT khi chạy trên **đúng full frozen SHA 40 ký tự** do TV2 công bố. Ghi SHA của source thực thi, không ghi SHA PR-head khác, rehearsal hoặc commit local.
- Mỗi evidence item phải truy ngược được `test_id`, `run_id`, operator, actor giả, timestamp có timezone, environment/config, actual result và raw artifact.
- CI PASS là bằng chứng CI. CI không thay thế live HTTP/UI/DB/Inventory/COD/Finance receipts trên frozen SHA.
- TV5 không nhận ảnh/log không có provenance, ảnh mock, screenshot từ CI thay cho live screen, hoặc bản log đã sửa. Raw artifacts giữ nguyên bytes; redacted copy là file riêng.
- Không nhận credential, JWT, access/refresh token, `.env`, secret, PII hoặc dữ liệu thanh toán thật. Nếu raw log có dữ liệu nhạy cảm, không upload vào PR/Issue; operator phải lưu ở kho hạn chế truy cập và gửi bản redacted cùng hash raw.
- Nếu thiếu một trường bắt buộc, TV5 trả receipt về `NEEDS_INFO`; không nâng case sang PASS.
- Finance MD01–MD11 đã được TV2 phê duyệt cho bản nộp và demo local (5%, HALF_UP/scale 0); production Finance vẫn NOT_APPROVED. Hai cờ C2/Finance mặc định OFF; chỉ TV2 xác nhận runtime và opt-in `.env.demo` trong đúng phạm vi. Không thêm Finance business approval thành gate thứ năm của C2; bốn gate C2 theo quyết định TV2 ngày 18/11 được ghi trong matrix/checklist.

## 2. Cấu trúc thư mục bàn giao

```text
reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/
    run-manifest.json
    UAT-01/
      manifest.json
      raw/
        request-response.json
        auth-audit.log
      redacted/
        request-response.redacted.json
      screenshots/
    UAT-02/
      ...
    C2-05/
      ...
    checksums.sha256
```

Tên folder chỉ là gợi ý. Không đổi tên hoặc sửa raw artifact sau khi tạo checksum. Mỗi case có manifest riêng; run-manifest liên kết frozen SHA, operator, environment và toàn bộ test IDs đã chạy.

## 3. Trường bắt buộc trong manifest

| Trường | Nội dung bắt buộc | Ví dụ/ghi chú |
|---|---|---|
| `repository` | Repo đầy đủ | `duyxl56785/Project_HK5` |
| `test_id` | Một ID trong ma trận 37 case | `UAT-01`, `C1-03`, `PROMO-02`, `C2-05` |
| `frozen_sha` | Full SHA 40 ký tự | Không dùng short SHA |
| `run_id` | ID duy nhất của lượt chạy | Cùng run ID giữa API/UI/DB receipts |
| `started_at`, `finished_at` | ISO-8601 có offset | Ví dụ `2026-11-21T09:15:00+07:00` |
| `environment` | OS, tool versions, Compose project, image digests, DB identity, ports, flags | Không ghi secret/credential |
| `actor` | Fake actor ID + role | Không ghi tên/PII khách hàng thật |
| `operator` | GitHub/team identity của người chạy | Ghi người thao tác thực tế |
| `steps` | Command/action đã thực sự thực hiện | Không copy kế hoạch chưa chạy |
| `actual_result` | HTTP status/body, DB state, UI result hoặc oracle thực tế | Phân biệt observed facts và nhận định |
| `status` | `PASS`, `FAIL`, `BLOCKED`, `NOT RUN` | Không dùng PASS cho CI-only |
| `artifacts` | Path, loại artifact, SHA256, redacted path | Hash tính trên raw file |
| `notes_or_blocker` | Blocker hoặc sai lệch | Ghi owner cần hành động |

### Manifest mẫu (khung, không phải receipt)

```json
{
  "repository": "duyxl56785/Project_HK5",
  "test_id": "UAT-01",
  "frozen_sha": "<TV2 cung cấp full 40-character SHA>",
  "run_id": "<operator tạo ID duy nhất>",
  "started_at": "<YYYY-MM-DDThh:mm:ss+07:00>",
  "finished_at": "<YYYY-MM-DDThh:mm:ss+07:00>",
  "environment": {
    "os": "<OS/version>",
    "tool_versions": {"node": "<version>", "java": "<version>", "docker": "<version>"},
    "compose_project": "<isolated project name>",
    "image_digests": ["<image@sha256:...>"],
    "db_identity": "<non-secret database identifier>",
    "ports": ["<loopback-only port mapping>"],
    "flags": {
      "HK5_VOUCHER_C2_ENABLED": false,
      "HK5_FINANCE_POLICY_APPROVED": false,
      "INVENTORY_ORDER_PAIR_ENABLED": false
    }
  },
  "actor": "<fake actor ID and role>",
  "operator": "<GitHub username/team identity>",
  "steps": ["<actual command or UI/API steps performed>"],
  "actual_result": "<actual observed result>",
  "status": "NOT RUN",
  "artifacts": [
    {
      "path": "raw/request-response.json",
      "kind": "HTTP request/response",
      "sha256": "<sha256 of raw file>",
      "redacted_copy": "redacted/request-response.redacted.json"
    }
  ],
  "notes_or_blocker": "<reason or empty string>"
}
```

**Không gửi manifest mẫu còn placeholder.** Giá trị flag trong ví dụ chỉ thể hiện mặc định an toàn; operator phải ghi đúng runtime values đã quan sát. Nếu runtime flags chưa được kiểm tra, ghi `NOT_VERIFIED`, không đoán `false` từ source code.

## 4. Owner và phạm vi evidence phải gửi

| Owner | Case/phạm vi chính | Receipt cần bàn giao |
|---|---|---|
| **TV2** | Freeze gates G-01..G-03, G-07..G-09; UAT-01/02/15; PROMO-01/02; quyết định C2/Finance | Frozen SHA + freeze time; CI URLs/run IDs; clean clone/stack receipts; image digests/DB identity/ports; runtime flags; fake actors; Auth/Store/Catalog/Admin receipts; P0 triage; policy/approval links; ảnh 6.1–6.4 và official DOCX |
| **TV3** | UAT-03..08; C1-01..05; C2-01..07, C2-09, C2-11; PROMO-03/04 phần Checkout | Cart/quote/Checkout HTTP receipts; Idempotency-Key fingerprint; Parent/child Order IDs; voucher definitions/states/ledger; C2 capability/quote results; PostgreSQL snapshot/constraint test receipts trên đúng SHA |
| **TV4** | UAT-09..13; C2-06..08; COD/Inventory/Return; PROMO-03 nếu có stock effect | Inventory operation/tuple; stock before-after; SellerOrder transitions; COD collection receipt/audit; Return state; refund gross receipt; RET-05 restock exactly-once receipt nếu có contract/implementation; ảnh 6.7 và phần 6.8 liên quan COD |
| **TV5** | UAT-14; C2-03..05/C2-08..10 money oracle; kiểm evidence toàn bộ case | Calculation sheet; report exports; gross/discount/fee/net snapshots; policy reference; manifest/checksum validation; screenshot-to-case mapping; QA disposition |

TV2 là operator và người xác nhận frozen SHA/flags. M01 Finance policy MD01–MD11 đã được phê duyệt cho demo local/bản nộp; production approval không có. TV3/TV4 không tự bật C2/Finance. TV5 không tự xác nhận runtime state.

## 5. Loại bằng chứng chấp nhận

- **CI:** GitHub Actions URL + run ID + exact commit SHA + job name + final conclusion + test summary/skip count. Ghi rõ `CI`, không gắn nhãn `Final UAT`.
- **HTTP/API:** method/path, request payload đã redacted, response status/body, timestamp, actor, correlation/request ID và frozen SHA.
- **DB:** read-only query/receipt, DB identity, schema/migration version, transaction/run correlation và result rows đã redacted. Mutation probes chỉ trong disposable integration-test DB được owner duyệt, không trên DB UAT dùng chung.
- **Inventory:** operationId/tuple fingerprint, operation type/state, quantity, stock before-after, provider receipt và timestamp.
- **COD/Finance:** SellerOrder ID, gross, fee, net, collection/settlement state, actor/audit, policy source. Không dùng từ “đã chuyển tiền” nếu chỉ có trạng thái nội bộ.
- **UI:** screenshot nguyên bản có viewport, route/page, fake actor, timestamp, frozen SHA/run ID và test ID; không crop mất ngữ cảnh cần thiết. UI không thay oracle backend.
- **Ảnh báo cáo 6.1–6.8:** chỉ chụp lại trên frozen SHA, theo đúng mapping của TV2; lưu ảnh gốc + SHA256 + caption + actor + viewport.

## 6. Checklist kiểm tra receipt của TV5

- [ ] Test ID tồn tại trong ma trận 37 case.
- [ ] Frozen SHA đúng bằng SHA TV2 đã công bố.
- [ ] Timestamp bắt đầu/kết thúc có timezone +07:00.
- [ ] Run ID, operator và fake actor có mặt.
- [ ] Environment/config gồm Compose project, image digest, DB identity, ports và flags thực tế.
- [ ] Steps phản ánh thao tác đã chạy, không phải kế hoạch.
- [ ] Actual result có dữ liệu quan sát được và status hợp lệ.
- [ ] Raw artifact có SHA256; redacted copy không làm thay đổi raw.
- [ ] Evidence đủ loại oracle cho test (HTTP/DB/Inventory/COD/Finance/UI).
- [ ] Không nhầm CI PASS với Final UAT PASS.
- [ ] Blocker/negative result được giữ nguyên; không xóa log hoặc đổi thành PASS.

## 7. Quy tắc trả receipt và cập nhật ma trận

TV5 phản hồi mỗi submission theo một trong các kết quả:

- `ACCEPTED`: đủ provenance và oracle; chỉ khi actual result đạt expected result mới cập nhật case `PASS`.
- `FAIL`: có thực thi và actual result không đạt expected result; lưu raw evidence và mở/ghi defect theo owner.
- `NEEDS_INFO`: thiếu trường/artefact/hash; giữ case `NOT RUN` cho tới khi đủ dữ liệu.
- `BLOCKED`: owner/contract/approval/runner/frozen SHA còn thiếu; ghi chính xác điều kiện và người có thẩm quyền.
- `NOT RUN`: chưa thực thi; không được tính vào pass rate.

Không tick hoặc đóng Issue #18/#157/#162 chỉ vì đã gửi receipt. Mọi checkbox nhiều mệnh đề chỉ được tick khi toàn bộ mệnh đề có bằng chứng và owner có quyền xác nhận.
