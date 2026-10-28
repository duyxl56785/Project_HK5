# TV5 Finance & QA workspace — read-only frontend

Refs #5/#12. User-authorized frontend task, separate from week1/Finance production acceptance.
React19 + TypeScript + existing Vite. No new application/package, UI library, global CSS, shared router, Auth/API client, owner backend or migration changes.

## Run inside the existing frontend

```sh
npm install --ignore-scripts --package-lock=false
npm run dev -- --host 127.0.0.1
```

Open `/src/features/finance/preview/index.html` on that Vite server. This is the same feature-preview pattern as Catalog/Seller/Checkout, not a replacement application. Five feature-local tabs; production App placeholder/routes remain unchanged. Direct previews support `?view=cod`, `?view=settlement`, `?view=qa`, `?view=prs`, `?state=loading|empty|error|denied|blocked`, and `?scope=store-a|store-b|denied`. Query/scope controls are **preview fixtures, never authentication**.

```sh
npm run typecheck
npm run build
node node_modules/vite/bin/vite.js build --config src/features/finance/vite.finance.config.ts
node --test src/features/finance/workspaceModel.test.mjs src/features/finance/WorkspaceView.test.mjs
```

DOM/browser tests follow the repository Node/Vite convention; there is no configured Vitest/RTL or lint command. Lint is NOT_RUN (no repository linter), not mislabeled TypeScript PASS. Tests can use optional transient `happy-dom`/`playwright` without changing package.json/lockfile; alternatively set `TV5_DOM_MODULE`/`TV5_BROWSER_MODULE` to existing module paths. Browser suite uses installed Chrome, disposable contexts and localhost only.

```sh
node --test src/features/finance/Workspace.dom.mjs
node --test --test-concurrency=1 src/features/finance/Workspace.browser.mjs
```

Set `TV5_BROWSER_EVIDENCE` to a local ignored directory for screenshots. Test tooling is not a runtime dependency. No test runs against a personal browser session or production API.

## Screens and components

| Screen | Scope / behavior | Module |
|---|---|---|
| Finance | observed amounts, COD evidence totals, count, time chart, status distribution; search/Store/status/UTC date/pagination/details | TransactionViews.tsx |
| COD | separate delivery, PAID/UNPAID/unknown payment observation and evidence verification; read-only detail | TransactionViews.tsx |
| Settlement | supplied record ID, amount, nullable commission/net, timestamps, blocker; no calculation or writes | TransactionViews.tsx |
| QA | one selected receipt/head, exact case-level PASS/FAIL/SKIP/NOT_RUN/BLOCKED; module/type/branch/SHA/search filters, detail/evidence | EvidenceViews.tsx |
| PR/reviewers | sanitized CLI snapshot, actual review heads, stale-head warning, CI/unknown mergeability, requests/threads/next-action, filters | EvidenceViews.tsx |

`components.tsx`: DashboardStatCard, StatusBadge, FilterBar, DataTable, Pagination, DetailPanel, DetailFields, StatePanel, EvidenceLink, SnapshotNote. Feature-scoped `.tv5-finance` CSS; mobile375/768 and desktop1440; semantic tables/captions, labelled controls, skip link, dialog focus trap/return, Escape, reduced-motion and text+color badges.

`FinanceWorkspace.tsx` manages loading/retry/abort/session identity. `WorkspaceView.tsx` is presentational and separately testable. `workspaceTypes.ts` defines local read projections and adapter, not a signed shared wire contract. `workspaceModel.ts` handles exact decimals, scope, filtering, pagination and defensive CLI projection.

## Data and boundaries

- Finance/COD/Settlement source is explicitly **DEMO DATA** (`fixtures.ts`), synthetic records only. No invented production transaction, backend request or fake endpoint. RECORDED/BLOCKED are local settlement view labels, VERIFIED/UNVERIFIED/UNKNOWN describe supplied evidence; none introduce persisted backend enum values. Delivery vocabulary mirrors the isolated SellerOrderLifecycle candidate; it does not claim that candidate is integrated.
- Financial totals are observations of displayed snapshots, **not business revenue definitions MD10**. COD evidence totals do not approve COD authority. Commission/net remain null unless supplied; no5%/House/rounding rule is hardcoded. No Create/Approve Settlement, Release Payment, Refund or Manual COD Confirmation.
- Money remains plain decimal **string** with optional up to6fraction digits for lossless display of historical snapshots (max30integer digits, no exponent/whitespace/NaN). Display cap is a defensive UI format limit, not an M01 business bound. BigInt arithmetic aligns scales exactly; no conversion of money to Number. Chart geometry converts only a bounded integer percentage; formatted money is untouched. Currency must explicitly equalVND; missing/other currency displays unavailable rather than default/FX. Approved Productprice MD01/MD02/unitcapMD03 is not changed by formatting legacy observations.
- `recorded/qa-runs.json` derives case projections from published QA103/e7d8948 (749cases) and CLI445deec (32cases), with exact immutable GitHub receipt URLs. These are **two historical recorded runs**, not new frontend test results and never a combined release total. A third clearly non-executed acceptance plan shows BLOCKED/NOT_RUN gates; no fabricated PASS. `recorded/provenance.json` links sources/hashes.
- `recorded/pr-audit.json` is an explicit public-field projection of the existing CLI100/113 output, captured at its embedded UTC timestamp. It does not update live, including when this PR later receives commits/reviews. Import fails closed if audit incomplete, erroneous or a row invalid. No browser GitHub requests/token; extra raw fields not forwarded. Evidence hrefs allow only HTTPS project GitHub paths, no credentials/query or javascript/data/foreign origin.
- Feature `data/` is ignored by root runtime policy. Public sanitized JSON is intentionally versioned under **recorded/**; no root ignore changes. Raw logs/XML, personal records, tokens and credentials are excluded.

## API / authorization handoff

| UI | Current API | Status / next gate |
|---|---|---|
| Finance, COD, Settlement | no approved read endpoints on main91e1a6c | BLOCKED_API_INTEGRATION; TV5+TV3/TV4 read schema/source and TV1 current authority |
| QA | immutable published receipts, imported view projection | RECORDED source; future safe server report endpoint separate |
| PR | sanitized read-only CLI export | RECORDED source; no GitHub credential in browser |

`blockedWorkspaceAdapter` is the safe default, with default scope denied. Preview fixtures must be explicitly supplied. A future production adapter must authenticate/current-authorize on the server, validate its payload, enforce Store scope in query/response and consume owner-approved API through TV1 shared client. Never use the preview scope or client-side filter as authority. Store-scoped UI hides other Store rows/options/sums/details and QA/PR tabs as defense in depth. Abort plus source-object/scope identity prevents stale results appearing when Store or adapter changes, including a replacement adapter with the same ID. Technical errors are sanitized; synchronous throws and rejected promises both enter error state.

TV1 must review before linking into shared navigation/Auth/router/API client. No cross-owner integration is implemented here. FullM01/Finance/COD/settlement policy remains PENDING/BLOCKED independently of Productprice approval. No flags changed, no deployment, no production/E2E acceptance inferred.

## Acceptance checklist

- [x] Five read-only views, typed adapter, source labels and explicit financial fixtures.
- [x] Model26RED→26GREEN; component10FAIL/1existingPASS→11GREEN; browser13PASS/5FAIL→18GREEN after UI label fixes; adapter5PASS/1FAIL→6GREEN after sync-error fix.
- [x] Search/filter/pagination/details, exact money, missing snapshots and stale-result/Store isolation tested at UI boundary.
- [x] Typecheck/appbuild/featurepreviewbuild actually executed; exact source/commands/log hashes in linked PR evidence.
- [ ] Final owner review and integration into TV1 shared navigation/current authentication.
- [ ] Approved Finance/COD read APIs, production policy/source/Store server authorization.
- [ ] Protected Finance HTTP/live integration/E2E (NOT_RUN/BLOCKED).

See PR115 and reports/TV5_FRONTEND_ACCEPTANCE_2026-10-28.md for final command-level receipts and remaining gates. Passing historical749/32 in the QA screen is never counted as new UI verification.
