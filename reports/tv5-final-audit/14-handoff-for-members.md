# Handoff for members — 18/11/2026

## TV2 — Nguyễn Văn Duy
**Deadline:** freeze 20/11 18:00; final UAT and screenshots 21/11; report/deck integration 21–22/11; submit by 23/11.

**Actions/evidence:** review exact PR #204 head `cdd84618d83c3d3afdb072ed9b1bf100e4eabc93` and decide merge; publish the full frozen `main` SHA and matching CI URLs; run the approved isolated UAT stack; supply clean-clone-on-another-machine receipt; capture official Figures 6.1–6.8 with timestamp, SHA256 and API/DB oracle; confirm the final Word/deck. TV2 requested PR review by 20/11 12:00 and main freeze is 20/11 18:00.

## TV3 — Trần Văn Chính
**Deadline:** support final UAT on 21/11.

**Actions/evidence:** provide source-bound HTTP/DB receipt for two-Store Cart, COD Checkout, one Parent Order/two SellerOrders, same-key replay, changed-payload conflict and Seller ownership/isolation; include exact app SHA, run URL, redacted logs, exit code and SHA256. TV3 has supplied a 17/11 rehearsal receipt at `240d028...`; keep it distinct from frozen-SHA UAT and supply final evidence on 21/11.

## TV4 — Phạm Thế Cường
**Deadline:** deliver available receipts before final UAT 21/11; confirm unresolved design gates before report freeze 22/11.

**Actions/evidence:** provide Inventory RESERVE/CONSUME/RELEASE, Seller fulfillment, Admin COD collection, Return and Store-isolation evidence with raw logs, JUnit/XML, exit code, SHA256 and exact source SHA. Explicitly classify RET-05 and UNKNOWN as approved, blocked or not run; do not infer a business decision. Admin COD collection has no final receipt yet.

## TV5 — Nguyễn Minh Duy
**Deadline:** PR #204 review gate 20/11 12:00; final evidence/report/deck check 21–22/11; submission by 23/11.

**Actions/evidence:** keep the 16-file pre-final manifest immutable and verified; obtain TV2 review of current PR #204 head `cdd84618`; maintain QA/COD/Finance matrices; reconcile final UAT run IDs, SHA, logs and screenshots; inspect final Word/deck against the actual frozen-SHA evidence. Run or sign UAT only within the owner-approved isolated environment. Send TV2 the contribution summary before end of 19/11; do not submit the local TV5 Word draft as the official report.

## Business / Finance owner
**Deadline:** before any Finance/COD final runtime test on 21/11.

**Actions/evidence:** confirm exact MD04/MD12 demo policy, promotion allocation, fee basis, HALF_UP rounding, Seller payable and permitted flags in writing; provide settlement/reconciliation acceptance criteria. No settlement or production approval is inferred from helper tests or policy documentation.
