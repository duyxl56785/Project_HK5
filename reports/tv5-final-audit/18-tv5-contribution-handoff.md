# TV5 contribution handoff — Nguyễn Minh Duy (BIT240082)

**Role:** TV5 — QA Lead. **Checkpoint:** 18/11/2026. **Overall disposition:** PARTIALLY_COMPLETE; pre-final evidence is delivered, final frozen-SHA acceptance is pending.

## TV5 work delivered

- Prepared and maintained the pre-final evidence receipt at `reports/minh-chung-kiem-thu/tv5-prefinal-552e8eb-20261115/` with raw logs, one real partial Catalog screenshot, database snapshots, run metadata, test summary, UAT matrix and checksum manifest.
- Corrected the receipt's evidence attributes and provenance, removed references to missing version-log files, corrected UAT log paths and distinguished tested `main@552e8eb` (merge commit #202) from PR #194 head `42a09be`.
- Audited repository/worktrees, PRs #204/#207/#209/#210, Issues #18/#158/#160/#162, reviewer state, hosted CI, Docker status, COD/Finance limitations and submission ownership.
- Maintained the QA/UAT, COD/Finance, release-readiness, figure mapping, blocker, freeze and member handoff records under `reports/tv5-final-audit/`.
- Verified the 16-entry pre-final manifest at PR head `cdd84618d83c3d3afdb072ed9b1bf100e4eabc93`: all 16 working files equal their committed Git blob bytes and the SHA-256 manifest values (16/16 MATCH). No raw evidence was rewritten in this continuation.

## Test and CI evidence, with scope kept separate

| Evidence | Source / executor | Recorded result | Acceptance limit |
|---|---|---|---|
| Pre-final backend build | `main@552e8eb`; TV5 Windows receipt | `gradlew.bat clean build --rerun-tasks --no-daemon --max-workers=1 --console=plain`; exit 0; 97 tasks; 1,661 passed / 16 skipped / 0 failed from JUnit XML | XML is not bundled, aggregate cannot be independently recomputed from this receipt; not final UAT |
| Pre-final frontend | `main@552e8eb`; TV5 receipt | `npm ci`, typecheck, build PASS; 178/178 unit tests; one moderate npm advisory recorded | Historical pre-final source only |
| Pre-final isolated smoke | `main@552e8eb`; TV5 receipt | Exit 0; 15 containers at capture; 8 app DBs with Flyway history; health/categories/catalog/frontend HTTP 200 | Cart had 0 rows; no checkout, COD collection or settlement attempted |
| Hosted main CI | `main@879143a`; GitHub Actions #44052552623 / #44054213307 | Order PostgreSQL 140/140; frontend 178/178; typecheck/build PASS; E2E diagnostic 24/24 | Hosted pre-final CI, not frozen-SHA UAT or clean clone |
| TV2 Checkout rehearsal | App `240d028...`; TV2 Windows owner receipt on Issue #160 | One root Order, two SellerOrders; replay 200; shipping mismatch 409; RESERVE once; anonymous 401; cross-Store Seller 404 | Rehearsal only; not final frozen-SHA UAT or submission screenshots |
| TV4 scoped evidence | PR #207 head `b4a33be...` | Inventory unit 27; Order unit 178; Notification 7; Inventory PG 38; Fulfillment 24; journal 39 PASS | Combined PostgreSQL attempt exit 124 BLOCKED; does not prove live two-Store E2E or RET-05 |

No runtime test was rerun for PR #204 because its changes are documentation/evidence only. PR HEAD `cdd84618` has no PR-triggered workflow run or status context returned.

## Remaining acceptance work and owners

| Work | Owner | Required handoff |
|---|---|---|
| Review exact PR #204 head `cdd84618` and decide merge | TV2 | Submitted review/decision before freeze; TV5 must not self-approve or merge |
| Publish frozen main SHA and exact-SHA CI; run clean clone on another machine | TV2 / TV1 | Full SHA, timestamp, CI URLs, clean-clone commands and exit receipts |
| Final Cart two-Store, Checkout COD, Parent/2 SellerOrders, idempotency and authorization | TV2 / TV3 | Final frozen-SHA API/DB/log evidence and approved screenshots |
| Inventory lifecycle, Seller fulfillment/isolation, Return and COD collection | TV4 / TV3 | RESERVE/CONSUME/RELEASE, Return and Admin collection receipts; RET-05/UNKNOWN decisions explicit |
| Finance business approval and settlement/reconciliation | Business owner / TV2 / TV5 | Written MD04/MD12 policy approval, permitted isolated flags, fee snapshot, seller payable, settlement and reconciliation evidence |
| Figures 6.1–6.8 and final Word/deck | TV2; TV5 validates | Eight official captures per Issue #162 and final Word/deck; local TV5 DOCX is not the official submission report |

## COD / Finance boundary

- COD Checkout was exercised only in the 17/11 rehearsal. Admin COD collection has no final receipt.
- Finance policy documentation/static review is not business approval. No final fee snapshot, settlement runtime or reconciliation receipt has been accepted.
- Figure 6.8 wording must describe an amount as “đã ghi nhận đối soát”, not “tiền đã trả”, because settlement does not transfer money.
- Keep Finance and any restricted Inventory/UNKNOWN flags disabled outside a separately approved disposable environment.

## TV2 message draft (not sent)

TV5 đã cập nhật hồ sơ QA/UAT và PR #204 tại head `cdd84618d83c3d3afdb072ed9b1bf100e4eabc93`. Manifest pre-final 16/16 đã đối chiếu với Git blob và working bytes. Ba mục review cũ đã được xử lý trong receipt: checksum/byte attributes, đường dẫn log UAT và provenance SHA; mapping Figures 6.1–6.8 đã đồng bộ theo quyết định nguồn bản nộp ở Issue #162. Nhờ anh review đúng head `cdd84618` và quyết định merge. Phần final UAT, clean clone, Admin COD collection, Finance settlement/reconciliation và ảnh final vẫn chưa có bằng chứng frozen-SHA, nên TV5 giữ NOT_RUN/BLOCKED. TV5 gửi gói đóng góp trước cuối 19/11; lịch freeze 20/11 18:00, final UAT 21/11, nộp chậm nhất 23/11.

**No message, review request, issue update, merge or close action was sent/performed by this handoff.**
