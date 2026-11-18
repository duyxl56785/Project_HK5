# CI/CD audit — exact SHA separation

| Source / run | Verified result | Scope / limit |
|---|---|---|
| `main@552e8eb...` | Backend 1,661 PASS / 16 SKIP; frontend 178 PASS; historical readiness/frontend/E2E reported SUCCESS | 15/11 receipt; JUnit XML not bundled, aggregate not independently recomputable from this folder |
| `main@879143a...`, [#44052552623](https://github.com/duyxl56785/Project_HK5/actions/runs/44052552623) | Backend readiness SUCCESS, Order PG16 140/140; frontend 178/178, typecheck/build PASS | Initial E2E 23/24 due Compose project allowlist mismatch |
| `main@879143a...`, [#44054213307](https://github.com/duyxl56785/Project_HK5/actions/runs/44054213307) | Exact-main E2E diagnostic 24/24, 0 skipped/flaky, exit 0 | Disposable CI stack, not frozen-SHA final UAT |
| PR #209 head `8caf5b1...`, [#43867495388](https://github.com/duyxl56785/Project_HK5/actions/runs/43867495388) | Frontend and E2E jobs SUCCESS | Voucher C1 demo seed PR only |
| PR #204 head `52c4036...` | No PR-triggered workflow runs or status contexts returned | Documentation-only PR; runtime test not applicable |

Current `origin/main=879143a1f081da4644ebc29c0950069429731f0f`. Hosted CI is pre-final evidence, not final UAT or clean-clone acceptance.
