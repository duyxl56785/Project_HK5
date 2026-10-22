# Category Browser — TV2

Refs #2 #9. Component-only delivery: `UI_READY / API_INTEGRATION_PENDING` after verification below. Router, shared API client/session and Gateway belong to TV1. No direct product-service port, fixture provider, protected write, Product price/status or SellerPrivate data is introduced.

## Scope and implementation plan

User-approved scope: one usable Category Browser before other Catalog/Seller pages. Reuse React 19/TypeScript 5.6/Vite 6 already installed. No new dependencies or shared configuration edits.

- [x] Model the actual `CategoryResponse(id, name, parentId)`, validate complete collections and build a preorder tree without recursion. Reject duplicate IDs, absent parents, cycles and malformed records as a whole. Search compares Vietnamese names without changing source values; keep matching ancestors and backend order. Tests cover a 10,000-level hierarchy.
- [ ] Build the controlled Category Browser with loading/error/retry/empty/invalid states, search, expansion and selection. Flat ARIA tree with explicit levels/positions, one tab stop and Arrow/Home/End/Enter/Space support; local CSS only.
- [ ] Verify pure functions, rendered UI states, typecheck, application build and a standalone feature bundle; review, commit/push and open one PR. Report browser interaction and API/E2E separately.

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

Initial tree RED: 21 tests, with actual assertion failures for absent tree/search/visibility behavior; after implementation **21 PASS,0FAIL,0SKIPPED**. Baseline frontend typecheck/build PASS after linking existing dependencies. The earlier sandbox-denied dependency junction caused `tsc` not found (setup failure, not source regression); raw logs retained locally. No package/lockfile versions were changed.
