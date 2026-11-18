# TV5 final audit — 18/11/2026

**Overall: PARTIALLY_COMPLETE.** PR #204 evidence corrections are pushed and awaiting TV2 re-review. Final frozen-SHA UAT, clean-clone on another machine, Finance settlement acceptance and final report media remain outstanding.

| Hạng mục | Trạng thái | Bằng chứng | Việc còn thiếu |
|---|---|---|---|
| Workspace/Git | PASS — read-only audit | Target worktree branch `docs/tv5-prefinal-evidence-20261115`, HEAD `c7613645b312ff5dd9024a8336b24dd94576d2b2`, matches remote; root worktree remains on `codex/tv5-w1-acceptance-evidence` with unrelated untracked WIP | Preserve root WIP and one anomalously named untracked file in target worktree |
| PR #204 | OPEN; current-head review pending | Remote feature head `c761364`; live PR issue remains OPEN. Only fetched submitted review is CHANGES_REQUESTED by `nguyenminhduy090-alt` on older head `52c4036`; no TV2 approval/review has been recorded in fetched review list. Draft/mergeability/requested-reviewer metadata not exposed by this readback | TV2 must review exact current head and decide merge |
| PR #207/#209/#210 | MERGED | #207 TV2 APPROVED; #209 TV3 APPROVED + CI; #210 TV2 APPROVED | Keep scoped limitations |
| Issues #18/#158/#160/#162 | OPEN | Live GitHub readback | Frozen-SHA UAT, clean clone, report/deck and owner sign-off |
| `.gitattributes` / SHA256SUMS | PASS | 16/16 committed blobs and working bytes match at manifest commit `6ae6284` | None for this receipt |
| Encoding | PASS — scanned docs | 31 Markdown files decoded as UTF-8; no mojibake markers found | Word/PPTX render review remains separate |
| CI/CD | PASS — scoped exact-main pre-final | `main@879143a`: Order PG16 140/140, frontend 178/178, typecheck/build PASS; E2E diagnostic 24/24 (run #44054213307). First E2E run #44052552623 was 23/24 due harness project-name allowlist | Not final frozen-SHA UAT; PR #204 has no PR-triggered checks because it is docs-only |
| QA/UAT | PARTIAL | TV2 Windows rehearsal 17/11 at app `240d028`: one root Order/two SellerOrders, replay and authorization checks | Final frozen-SHA UAT |
| COD | PARTIAL | Checkout COD exercised in rehearsal | Admin COD collection and final UAT receipt |
| Finance | BLOCKED — final acceptance | MD04/MD12 and source rules inspected | Approved final scope and exact-SHA fee/settlement evidence |
| Figures 6.1–6.8 | PARTIAL — official mapping confirmed | Issue #162 TV2 source-of-submission comment defines: 6.1 desktop homepage; 6.2 mobile homepage (390 px); 6.3 Seller product management; 6.4 Admin Seller approval; 6.5 two-Store Cart; 6.6 one root Order/two SellerOrders; 6.7 Seller isolation; 6.8 platform finance/COD | TV2 captures all eight on final frozen SHA; TV5 checks captions, masking and UI/API/DB evidence. Figure 6.8 must say “đã ghi nhận đối soát”, not “tiền đã trả” |
| Handoff | PARTIAL | Audit documents updated | Owner decisions, frozen SHA, approved Word/deck |

**Controlling schedule confirmed by TV2 in Issue #160 on 17/11:** P0 status/reporting checkpoint 20/11 12:00; PR merge cutoff and `main` freeze 20/11 18:00; final UAT 21/11; report/deck 21–22/11; submission no later than 23/11. After freeze, only P0 fixes may be merged, with a final cutoff at 21/11 14:00; no further merges after that. This supersedes earlier 19/11 freeze / 20/11 UAT / 21/11 submission dates.

**Docker update 18/11:** daemon is now available (`desktop-linux`, Engine 29.8.1), but the retained `hk5-uat-tv5-20261115230000` stack is incomplete: 6 containers running, 9 exited; 8 app containers fail to mount missing temporary secret files and PostgreSQL exited 255. Existing volumes/network were left untouched. This is not a usable final UAT stack.

**TV5 delivery:** latest verified feature-branch head is `c7613645b312ff5dd9024a8336b24dd94576d2b2`, and `git ls-remote` confirms the remote feature branch matches it. GitHub issue readback confirms PR #204 remains OPEN. The only fetched submitted review is the historical CHANGES_REQUESTED review by the PR author on `52c4036`; no TV2 review submission was returned. Current draft/mergeability/reviewer-request metadata was not available from the latest connector readback. No merge performed. Current `main` remains `879143a1f081da4644ebc29c0950069429731f0f`.
