# Category Browser — TV2

Refs #2 #9. Component-only delivery: `UI_READY / API_INTEGRATION_PENDING` after verification below. Router, shared API client/session and Gateway belong to TV1. No direct product-service port, fixture provider, protected write, Product price/status or SellerPrivate data is introduced.

## Scope and implementation plan

User-approved scope: one usable Category Browser before other Catalog/Seller pages. Reuse React 19/TypeScript 5.6/Vite 6 already installed. No new dependencies or shared configuration edits.

- [x] Model the actual `CategoryResponse(id, name, parentId)`, validate complete collections and build a preorder tree without recursion. Reject duplicate IDs, absent parents, cycles and malformed records as a whole. Search compares Vietnamese names without changing source values; keep matching ancestors and backend order. Tests cover a 10,000-level hierarchy.
- [x] Build the controlled Category Browser with loading/error/retry/empty/invalid states, search, expansion and selection. Flat DOM with explicit ARIA tree/group ownership, levels/positions, one tab stop and Arrow/Home/End handlers; native buttons activate with Enter/Space. Local CSS only.
- [x] Verify pure functions, rendered UI states, DOM interactions, typecheck, application build and standalone feature bundle; independent read-only review and focus regression resolved. Real browser interaction and API/E2E remain separate acceptance steps.

## Backend boundary

`backend/product-service/src/main/java/com/projecthk5/productservice/category/CategoryResponse.java` defines exactly `UUID id, String name, UUID parentId`. `GET /api/categories` returns the flat array. Category GET is permitted within product-service; writes remain deny-all. Main `aca5dff` has no Gateway route or frontend API utility for this resource. Do not infer `slug`, `status`, image, price, stock or product count.

The future TV1 loader must supply `CategoryBrowserState`, including a real retry callback, and own cancellation/loading/error transitions. Do not replace unavailable data with an empty success response. `selectedId` is controlled by the caller; a selection is presentation state, not ownership or authorization.

## Tests

No Vitest/DOM renderer is installed. Node's built-in test runner executes the feature's `.test.mjs` files; Node >=22.18 supports the erasable TypeScript used here. This does not replace TypeScript checking. Run from `frontend`:

```powershell
node --test src/features/catalog/categoryTree.test.mjs
npm run typecheck
npm run build
```

Initial tree RED: **2 PASS/19 assertion FAIL** for absent tree/search/visibility behavior; after implementation **21 PASS,0FAIL,0SKIPPED**. Baseline frontend typecheck/build PASS after linking existing dependencies. The earlier sandbox-denied dependency junction caused `tsc` not found (setup failure, not source regression); raw logs retained locally. No project package/lockfile versions were changed.

## Component integration (TV1)

```tsx
import { useState } from 'react';
import { CategoryBrowser } from '../features/catalog';
import type { CategoryBrowserState, CategoryDto } from '../features/catalog';

// In TV1's route/container, use real loader state and a controlled selection:
function CategoryPage({ state }: { readonly state: CategoryBrowserState }) {
  const [selected, setSelected] = useState<CategoryDto | null>(null);
  return <CategoryBrowser state={state} selectedId={selected?.id ?? null} onSelect={setSelected} />;
}
```

The container owns API access and retry; no production fetch implementation is supplied here. Do not register this export in shared App/Router without TV1. `index.ts` exports the component/props/DTO/state, and the component imports its own scoped CSS. The feature needs no Query provider or authentication context to render. It never sends identity/role/version/write requests.

Search is case-insensitive with Vietnamese diacritics retained; matching nodes and their ancestors remain. Search opens matching paths, but a user may collapse them. Clearing the query restores browsing expansion and returns focus to the search input. Clicking a branch both selects and toggles it; Arrow keys only navigate/expand, without selecting. `selectedId` may describe a collapsed descendant; the selection summary still shows the authoritative source name. Unknown IDs are shown as unselected. Data errors expose no usable partial tree.

The flat DOM uses `tree → treeitem → group → child` ARIA ownership and explicit positions; this avoids recursive rendering on deep input. Arrow Up/Down/Left/Right, Home and End follow the [W3C Tree View Pattern](https://www.w3.org/WAI/ARIA/apg/patterns/treeview/). A labelled search field provides name lookup; type-ahead within the tree and optional `*` expansion are not implemented. Indentation is capped visually on small screens while ARIA retains the full depth. This is not a screen-reader certification.

## Reproducible verification

The normal application entry does not import this feature yet. Run a separate bundle build too, from `frontend`:

```powershell
node --test src/features/catalog/categoryTree.test.mjs src/features/catalog/CategoryBrowser.test.mjs
npm run typecheck
npm run build
.\node_modules\.bin\vite.cmd build --config src/features/catalog/vite.category.config.ts
```

The rendered-state suite uses existing Vite SSR and ReactDOM server rendering, not a DOM simulator. It checks loading/error/retry markup, empty/invalid data, ARIA/selection, escaping and unique labels. CSS and its build config stay inside the feature; no shared config, package manifest or lockfile changes.

Optional DOM suite (test tooling only, installed in ignored `build`; Node 24 recommended):

```powershell
npm install --prefix ../backend/product-service/build/category-dom-tools --no-save --package-lock=false --ignore-scripts happy-dom@21.0.4
$env:TV2_DOM_MODULE = (Resolve-Path ../backend/product-service/build/category-dom-tools/node_modules/happy-dom/lib/index.js).Path
node --test src/features/catalog/categoryTree.test.mjs src/features/catalog/CategoryBrowser.test.mjs src/features/catalog/CategoryBrowser.dom.mjs
```

### Actual run — 22/10/2026

- Node **24.21.0**, TypeScript **5.6.3**, React/ReactDOM **19.3.1**, Vite **6.4.4**; installed versions satisfy existing manifest ranges. Reused existing dependencies locally, no installation into frontend or lockfile update. Optional Happy DOM **21.0.4** lives in an ignored test-tool directory.
- UI RED **10 assertion FAIL** before implementation. Focus regression, before the fix: **5 DOM PASS/1 assertion FAIL**, `Expected focus on INPUT; actual BODY`. Same expectations after adding an input ref/clear focus: combined **37 PASS/0 FAIL/0 SKIPPED**, exit0 = **21 pure tree +10 SSR +6 DOM interactions**. Focused runs are not added again. Independent review rechecked the fix and found no remaining blocker; it did not rerun tests.
- DOM tests exercise branch selection/exact DTO callback, expansion/collapse, search/ancestor paths/no-match recovery, Arrow/Home/End focus without selection, direct-child ARIA ownership and retry callback. They do not prove native browser Enter/Space activation or accessibility-tree rendering.
- `npm run typecheck`, `npm run build` and the standalone Vite feature bundle all **PASS/exit0** on final source. Standalone output: **10.89 kB JS +3.97 kB CSS** before gzip. The shared App remains unchanged.
- Initial DOM failure reporting compared complete DOM objects and exhausted the test process while formatting the mismatch. Diagnostics were changed to compare reference identity as a boolean (same focus expectation), then the actual one-failure RED was obtained before the source fix. Both logs remain unchanged; the initial aborted run is not a complete test result.
- Real Chromium/browser/mobile visual/screen-reader/native keyboard tests **NOT_RUN/BLOCKED**: isolated Playwright 1.63.0 setup succeeded after offline-cache metadata was unavailable, but headless Chromium download repeatedly timed out. No browser case was executed, skipped or labelled PASS. Temporary tools/logs are ignored, not committed.
- Backend/PostgreSQL regression **NOT_RUN**, not required for this frontend-only slice. Gateway/API/Auth, authenticated Seller/Store, public Product/Catalog provider, router mount and live E2E **NOT_RUN/PENDING**. No Catalog/Product/Seller forms were added because their runtime/public contracts remain unavailable. No CI workflow currently covers frontend-only changes.

`UI_IMPLEMENTED / UI_TESTED` here means the exported component, logic/render/DOM suites and builds above. `API_CONNECTED` is **false**. Status remains `WAITING_TV1_ROUTER`, `API_INTEGRATION_PENDING`, `WAITING_AUTH_RUNTIME` (future protected flows), `WAITING_CATALOG_PROVIDER` (future Product Catalog) and `NOT_RUN_E2E`. Do not mark all Seller/Catalog/frontend or F08 complete from this delivery.
