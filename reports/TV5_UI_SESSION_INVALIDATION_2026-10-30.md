# TV5 UI session invalidation — 30/10/2026

Refs #5/#12/#115. Source commit `6ab0f3f29b5cb7d8cea14cebe60656cd744b2498`, branch `codex/tv5-finance-qa-ui`.

TV1 review at cae695c required session invalidation even if the actor changes but Store ID stays the same. The isolated feature now accepts a host-owned `sessionEpoch` invalidation generation. A changed epoch hides old ready state before effects, aborts the prior read and remounts view-local detail/filter state. Late responses cannot populate a new epoch. Default0 preserves the explicit preview; a future authenticated host must supply/advance the generation for session/actor/effective-authority changes. This number is not a token or authorization grant.

Changed only `frontend/src/features/finance/FinanceWorkspace.tsx`, `Workspace.dom.mjs` and README. No Auth/router/shared client/backend/contract/flag changes. This implements the local invalidation mechanism; real authenticated-session propagation remains a TV1 integration gate. Finance data remains DEMO; QA/PR data remains RECORDED.

| Verification | Result | Source attribution |
|---|---|---|
| Initial DOM harness |6PASS/2FAIL, one selector error and one real late-result failure | Original log retained; not two behavior findings |
| Corrected DOM reproducer |6PASS/2actual behavior FAIL | Existing component ignored the new epoch; old ready/details retained and second read absent |
| Candidate model/render/DOM |48PASS | Per-file source manifest; candidate over cae, not a false committed-head run |
| Exact committed model/render/DOM |48PASS/0FAIL/SKIP |6ab0f3f |
| Exact committed Chrome |18PASS/0FAIL/SKIP |6ab0f3f; separate local fixture browser invocation |
| TypeScript/app build/feature build |PASS/exit0 | Same candidate source before commit; README-only changes afterward |
| Real session/Auth/Finance API/live marketplace E2E |NOT_RUN/BLOCKED | No approved Finance read API, server query/current authority or shared mount |

The two committed invocations cover66 distinct UI cases on the same source. Do not add historical64 or another PR's totals. Raw logs remain unchanged/private, with SHA256 in `reports/evidence/tv5-ui-session-20261030/`. The checked source remains identical after this documentation-only publication.

- [x] Two session regressions reproduced, corrected harness history retained, minimal feature-only fix.
- [x] Fresh model/render/DOM, Chrome, typecheck and both builds executed; raw hashes verified.
- [x] Code committed/pushed to existing PR115; TV2 request retained and TV1 re-requested once for a significant new source delta after checking no explicit removal event.
- [ ] TV1/TV2 owner review and actual authenticated-host epoch propagation.
- [ ] Signed Finance/COD read DTOs, server Store authorization, M01 production policy, real API/E2E and complete Issue5/12 acceptance.

Keep OPEN/PARTIAL; no Approve/Merge/Close, Finance flags or fake payment/settlement endpoints.
