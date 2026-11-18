# Main freeze checklist — planned 20/11/2026 18:00

**Status at 18/11/2026:** NOT_FROZEN. Current observed `main` is `879143a1f081da4644ebc29c0950069429731f0f`; this is not a frozen-SHA declaration. TV2 owns the freeze decision and must publish the full SHA and timestamp after the cutoff.

| Hạng mục | Trạng thái | Bằng chứng | Người phụ trách | Ghi chú |
|---|---|---|---|---|
| PR #204 review and merge decision | WAITING_REVIEW | [PR #204](https://github.com/duyxl56785/Project_HK5/pull/204), head `cdd84618`; old CHANGES_REQUESTED review is on `52c4036` | TV2 / TV5 | TV2 review exact head; requested review/P0 checkpoint by 20/11 12:00; merge cutoff 18:00 |
| Other required PRs #207/#209/#210 | MERGED | GitHub PR metadata: #207→`240d028`; #209→`87e0ec8`; #210→`879143a` | TV2 / TV3 / TV4 | Reconfirm any newly opened PRs before freeze |
| P0 report | NOT_VERIFIED | TV2 decision in Issue #160 requires TV3/TV4 report by 20/11 12:00 | TV3 / TV4 | State exact open P0s and evidence; do not infer zero P0 |
| Frozen main SHA | NOT_RUN | No frozen SHA published yet | TV2 | Record full 40-character SHA and freeze timestamp |
| Hosted CI on frozen SHA | NOT_RUN | Existing pre-final CI is for `main@879143a` | TV2 / CI owners | Link checks for exact frozen SHA only |
| Clean clone on another machine | NOT_RUN | Issue #158 remains OPEN; no qualifying receipt found | TV1 | Exact SHA, install commands, environment, exit code and E2E log |
| Clean isolated UAT stack | BLOCKED_ENV | Retained TV5 stack has 6 running / 9 exited, 8 missing secret mounts, PostgreSQL exit 255 | TV2 / environment owner | Provide a new approved isolated runner; do not repair shared/retained data destructively |
| Cart / Checkout / Parent + SellerOrders | REHEARSAL_PASS; FINAL NOT_RUN | TV2 17/11 rehearsal at app `240d028...` | TV2 / TV3 | Final run on frozen SHA 21/11 |
| Inventory lifecycle / Seller isolation / Return | PARTIAL / BLOCKED | PR #207 scoped receipt; RET-05 and UNKNOWN remain gated | TV3 / TV4 | Final owner-approved scope and source-bound receipts required |
| Admin COD collection | NOT_RUN_FINAL | No Admin collection receipt | TV4 / TV5 | Need DELIVERED prerequisite, Admin actor, one-time receipt and duplicate guard |
| Finance approval / settlement / reconciliation | BLOCKED — BUSINESS APPROVAL / RUNTIME | MD04/MD12 documentation and static review only | Business owner / TV2 / TV5 | Written approval and exact-SHA runtime evidence required |
| Figures 6.1–6.8 | NOT_CAPTURED_FINAL | Official crosswalk in `08-figure-6-1-to-6-8.md` | TV2; TV5 validates | Eight final images, timestamp, SHA256, redaction and matching API/DB oracle |
| Official Word and final deck | NOT_ACCEPTED | Official Word not in this worktree; local Word/PPTX are source drafts | TV2; TV5 validates | TV2 creates final Word/deck after final E2E; TV5 checks consistency |
| Evidence manifest | PRE-FINAL 16/16 MATCH | `07-evidence-manifest.md`; verified against Git blobs at PR head `cdd84618` | TV5 | Final manifest must be generated for final captures and exact frozen-SHA evidence |

## Freeze acceptance rule

Do not mark freeze PASS until TV2 publishes the full SHA, freeze timestamp, exact-SHA CI URLs, PR disposition and remaining blocker list. A green earlier main CI or a rehearsal does not establish frozen-SHA acceptance.

## Controlling schedule from TV2 Issue #160 (17/11)

- 20/11 12:00: P0 report/checkpoint and preferred PR #204 review/push cutoff.
- 20/11 18:00: final merge cutoff and main freeze.
- 21/11: final UAT; only P0 fixes may be considered, with a final merge cutoff at 14:00 and full E2E rerun after any accepted fix.
- 21–22/11: report/deck integration and QA comparison.
- By 23/11: submission.
