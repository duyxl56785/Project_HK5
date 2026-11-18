# TV5 final audit — 18/11/2026

**Overall: PARTIALLY_COMPLETE.** PR #204 evidence corrections are being prepared. Final frozen-SHA UAT, clean-clone on another machine, Finance settlement acceptance and final report media remain outstanding.

| Hạng mục | Trạng thái | Bằng chứng | Việc còn thiếu |
|---|---|---|---|
| Workspace/Git | PASS — audit | Branch `docs/tv5-prefinal-evidence-20261115`, HEAD `52c4036`; origin/main `879143a`; branch 28 commits behind | Commit/push; preserve unrelated untracked files |
| PR #204 | PARTIALLY_COMPLETE | OPEN, Draft, mergeable; TV2 CHANGES_REQUESTED at `52c4036` | Correct manifest, figure mapping and source description; push and re-review |
| PR #207/#209/#210 | MERGED | #207 TV2 APPROVED; #209 TV3 APPROVED + CI; #210 TV2 APPROVED | Keep scoped limitations |
| Issues #18/#158/#160/#162 | OPEN | Live GitHub readback | Frozen-SHA UAT, clean clone, report/deck and owner sign-off |
| `.gitattributes` / SHA256SUMS | LOCAL_CORRECTION_IN_PROGRESS | Raw payloads match index; 15/16 current worktree hashes match after JSON crosswalk correction | Final Git blob verification after payload commit |
| Encoding | NOT_RUN | No bulk conversion performed | UTF-8/mojibake scan before commit |
| CI/CD | PASS — scoped exact-main pre-final | `main@879143a`: Order PG16 140/140; frontend 178/178; E2E 24/24 | Not final frozen-SHA UAT |
| QA/UAT | PARTIAL | TV2 Windows rehearsal 17/11 at app `240d028`: one root Order/two SellerOrders, replay and authorization checks | Final frozen-SHA UAT |
| COD | PARTIAL | Checkout COD exercised in rehearsal | Admin COD collection and final UAT receipt |
| Finance | BLOCKED — final acceptance | MD04/MD12 and source rules inspected | Approved final scope and exact-SHA fee/settlement evidence |
| Figures 6.1–6.8 | PARTIAL / CAPTION REVIEW | 6.1 architecture caption present; 6.4–6.6 owner crosswalk; 6.2/6.3/6.7/6.8 not defined in inspected draft | TV2 confirm required figure list; final 6.4–6.6 captures and separate B.1–B.5 appendix evidence |
| Handoff | PARTIAL | Audit documents updated | Owner decisions, frozen SHA, approved Word/deck |

**Schedule conflict:** this task says freeze 19/11, verify 20/11 and deadline 21/11. TV2's later Issue #160 update says freeze 20/11 18:00, final UAT 21/11 and submission by 23/11. TV2 must confirm the controlling schedule.

**Docker update 18/11:** daemon is now available (`desktop-linux`, Engine 29.8.1), but the retained `hk5-uat-tv5-20261115230000` stack is incomplete: 6 containers running, 9 exited; 8 app containers fail to mount missing temporary secret files and PostgreSQL exited 255. Existing volumes/network were left untouched. This is not a usable final UAT stack.
