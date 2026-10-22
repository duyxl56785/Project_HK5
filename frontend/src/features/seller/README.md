# Seller/Store Overview — TV2

Refs #2 #9. One private-management presentation feature, independent of Category PR74. No Auth/Gateway/Router/API client or backend edits. No registration/save/delete requests, fake success, authority adapter or live API provider.

## Scope / plan

- [x] Read merged `SellerStoreResponses.java`, `CanonicalVersion`, SellerStatus and StoreDetails. Model only actual display fields and preserve canonical version text; validate consistency and omit private reasons/unknown fields.
- [x] Build controlled overview: four Seller statuses, Store open/closed/profile/version, no-Store and loading/error/retry/empty/unauthorized/unavailable states. Scoped CSS and no write actions.
- [x] Test model/render/real browser/viewport/typecheck/app+feature bundle and independent read-only review. Keep API/Auth/Router/E2E pending; delivery is a component, not an integrated authenticated screen.

## Data and security boundary

Merged backend `SellerPrivate` fields are `id,version,status,reviewReason`; `StoreOwner` fields are `id,sellerId,name,slug,open,version,createdAt,updatedAt`. These are **internal mapped DTOs / HTTP candidate**, not exposed endpoints. This frontend uses display projections: Seller `id,version,status`; Store `id,sellerId,name,slug,open,version`. It introduces no wire contract. No reviewReason, ownerReference, role, token, timestamps or arbitrary object is rendered/exported through the projection.

**Ready props must come from a server-authorized own-management response.** A future TV1 container must verify authenticated identity/current authority/resource scope on the server before constructing ready state. The component/state/parser cannot authorize anyone, and no role/verified boolean from a client is accepted as authority. Do not mount private data on a public Catalog route. Unauthorized, unavailable, loading or error states do not render stale/private payloads even if extra fields are present.

`store:null` means the backend has proved no Store exists; undefined/missing/malformed Store or mismatched Seller ID is a data error. Do not map timeout/403/503 to null or an empty success. `empty` is reserved for proved Seller absence. Store `open` is a stored flag, not current Catalog sellability or a permission grant. A suspended/rejected Seller may still have a stored Store record; UI must not invent a selling permission.

Versions follow the existing canonical `0|[1-9][0-9]*` and Java Long.MAX_VALUE cap. Validation compares string length/lexical order, never JS Number, parseInt or floating point. Display preserves the exact string, including `9007199254740993` and `9223372036854775807`. Names/IDs are not normalized; Store slug/name lengths follow existing DTO/domain sources.

## Foundation checks

From `frontend`: `node --test src/features/seller/sellerStoreModel.test.mjs`, `npm run typecheck`. Reuse the installed toolchain; no project dependencies/config/lockfile changes. Test data remains in test/preview entries only.

## TV1 integration

```tsx
import { SellerStoreOverview } from '../features/seller';

// Initial state until the real server-authorized loader is available:
<SellerStoreOverview state={{ status: 'unavailable' }} />
```

TV1's future private route/container owns authenticated loading, authorized responses, explicit absence, retry and transitions. Once permission has been checked server-side, project the actual DTOs with `readOverview`; only pass a successful result as ready. Invalid data must become an error, not absence. Retry invokes exactly the supplied callback and does not change state or announce success itself. The container also owns focus/announcement policy across asynchronous route/data transitions.

`index.ts` exports the component, props, display types and projection/version helpers. The feature imports its scoped CSS, requires no Query/Auth provider to render an unavailable state, and has no network calls. It does not display reviewReason even for an authorized owner; disclosure and any future private reason UI need separate review. No registration form is included: body `{}` is a candidate, and production authenticated registration is still absent. There are no nonfunctional Save/Delete/Create buttons.

## Verification — 22/10/2026

Node24.21.0, TypeScript5.6.3, React/ReactDOM19.3.1, Vite6.4.4; reused existing installed dependencies. System Chrome155.0.8059.85 via Playwright1.63.0 already installed in ignored tooling, with a fresh temporary profile. No shared file or manifest/lockfile changes; this branch is based on mainaca5dff and imports no Category code from PR74.

Run from `frontend`:

```powershell
node --test src/features/seller/sellerStoreModel.test.mjs src/features/seller/SellerStoreOverview.test.mjs
npm run typecheck
npm run build
.\node_modules\.bin\vite.cmd build --config src/features/seller/vite.overview.config.ts
```

- Foundation RED **5PASS/7assertionFAIL** → GREEN12PASS. UI RED **1PASS/16assertionFAIL** → GREEN17SSR. Three additional line-terminator boundary diagnostics passed without changing the already-correct parser; they are not a bug/RED claim.
- Final model+render invocation: **32PASS/0FAIL/0SKIPPED,exit0 =15model+17SSR**. Covers all statuses, Store flag/absence, malformed/cross-Seller source, all non-ready privacy paths, stripping private/unknown fields, immutable inputs, safe escaping, canonical Long versions including values beyond JS precision, whitespace/overflow and unique headings. No assertion was weakened or test deleted.
- Strict typecheck, application build and **standalone feature build all PASS/exit0**. The latter validates the unmounted export, **6.55kB JS+2.84kB CSS** before gzip; app build alone would not cover it.
- Separate browser invocation: **11PASS/0FAIL/0SKIPPED,exit0** on real Chrome155.0.8059.85. Checks visible exact versions/private-field concealment, closed/no-Store distinction, loading/empty/unauthorized/unavailable with attached stale fixtures, cross-Seller failure and native Tab/Enter retry with **zero fetch/XHR**. Viewports **375×900,768×900,1280×900** retain long Vietnamese names/slugs/versions with no document/card horizontal overflow; mobile+desktop screenshots visually inspected. Separate invocations/branches are not combined into a release total.
- Human screen-reader/physical mobile, real Auth/JWT/ownership/HTTP/Gateway/Router/live-data E2E **NOT_RUN**. UI tests do not establish server authorization or JWT acceptance. Backend/PostgreSQL regression **NOT_RUN** for frontend-only changes. CI **NOT_RUN**: current workflow filters backend/docker; no shared CI change made.

### Optional preview/browser suite

Vite entry `/src/features/seller/preview/index.html` is **test/preview-only**, never mounted/exported by App or the feature. Its banner explicitly states sample data is not the current account and no API is connected. `?state=loading|empty|unauthorized|unavailable|error|no-store|closed|inconsistent`, plus `?long=1`, exercise states; error retry changes only local fixture state. No mock session/token, Auth grants or production success is created.

```powershell
# Point to a separately installed test tool; do not add it to shared package.json:
$env:TV2_BROWSER_MODULE = 'C:/path/to/isolated/node_modules/playwright/index.mjs'
$env:TV2_BROWSER_EXECUTABLE = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
node --test src/features/seller/SellerStoreOverview.browser.mjs
```

The module variable is optional if Playwright is resolvable; executable is optional for installed Chrome. The script binds a temporary Vite server only to127.0.0.1, opens a new profile/context and closes its own resources. It does not touch a personal browser or user data. `TV2_BROWSER_EVIDENCE` optionally overrides the ignored output directory. Logs/screenshots/tools/builds stay unmodified and ignored, not committed.

**UI_IMPLEMENTED / UI_TESTED / BROWSER_VERIFIED** are scoped to this private presentation component and isolated fixtures. **API_CONNECTED=false / WAITING_TV1_ROUTER / WAITING_AUTH_RUNTIME / WAITING_CONTRACT / NOT_RUN_E2E** remain. Do not tick Seller registration/API/moderation/Store ownership/live authorization or all frontend/F08 from this delivery.
