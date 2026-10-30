# Public Category read integration candidate

Refs #2, #8, #9. Stacked on PR #105 `09c26c784d82dfc275e4ae8f0ce0b2a458ec4490`.

`SharedRouter` accepts optional `publicCategoriesEnabled` (default `false`). When explicitly enabled by a reviewed host, `/catalog/categories` calls the existing shared `ApiClient.request` with GET `/api/categories`, validates the complete tree and canonical UUIDs, projects only `id/name/parentId`, and renders the existing `CategoryBrowser`. TanStack Query owns loading/error/cancellation; retry is manual. No credentials, refresh logic, authority, public Product DTO or pagination contract is invented here. No production App/main entry or runtime flag is changed.

The test-only host opts in with `?categories=on`. Browser responses are intercepted fixtures, not production or live Gateway evidence. A failed request never becomes a fabricated empty collection, and upstream details are not rendered.

## Verification on 30 October 2026

Node 24.21.0, Windows, lockfile-installed dependencies:

- `npm run typecheck`, `npm run build`: PASS, exit 0.
- `node --experimental-strip-types --test src/features/catalog/publicCategories.test.mjs src/features/catalog/categoryTree.test.mjs src/shared/apiClient.test.mjs`: 66 PASS / 0 FAIL / 0 SKIP, exit 0 (8 new adapter, 21 existing tree, 37 shared client).
- Chrome via `TV1_BROWSER_CHANNEL=chrome npm run test:browser`: 17 PASS / 0 FAIL / 0 SKIP, exit 0 (4 Category + 13 existing shared browser cases). Category coverage: default OFF/no request, successful read/selection, sanitized dependency failure/manual retry, malformed response.
- Initial adapter test RED was missing module (exit 1); initial browser run 3 PASS / 1 FAIL exposed a fixture assuming only one StrictMode request. Fixture now remains unavailable until manual recovery; production retry logic was not weakened.

## Pending integration

Gateway Category forwarding is BLOCKED pending a common reviewed base: #105 is independent of Auth/Gateway source in the #134 authority stack. No Gateway route has been added. Existing Product Category controller/security contract already permits public Category reads, while Category writes stay blocked by provider security. This branch does not attest deployment availability of that provider.

Public Product search remains BLOCKED: #91 supplies private Product candidates and candidate totals, not an eligible public page; #126 still reports House authority UNKNOWN. No ACTIVE-only listing, guessed total or fake authority is exposed. Live Category Gateway/Product/PostgreSQL/browser E2E, Product Catalog API connection and marketplace demo acceptance are NOT_RUN. Keep WAITING_REVIEW / INTEGRATION_GATED; no merge, deployment or flag activation.
