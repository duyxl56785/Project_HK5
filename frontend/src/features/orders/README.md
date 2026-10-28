# Customer Order History client/UI — TV3

Working feature implementation for backend PR107, not a mock preview. Independent
of Cart/COD frontend PR86 (now merged on main) and Catalog mapper PR106. No shared root, router, client,
Auth, Gateway, dependency manifest or runtime flag changes.

## Host boundary for TV1

Import `OrderHistory` from this feature's `index.ts`. Host props:

- `transport(path, RequestInit): Promise<Response>`: TV1's stable authenticated
  same-origin/Gateway fetch wrapper. The feature calls only GET `/api/orders` and
  GET `/api/orders/{validatedUUID}`. It does not store tokens, login, refresh,
  choose an API host, reinterpret an account ID or fabricate authorization.
  Transport must honor `signal`, preserve GET/Accept/cache/redirect constraints,
  and apply the host's actual session/401 policy. Do not put an Inventory or
  Catalog credential here. Never pass an unauthenticated fallback fetch.
- `sessionScope: string | null`: trusted host correlation marker, changed on
  principal/session changes and null when access is unavailable. This is **not
  authorization**; the server verifies JWT and ownership. Keyed unmount + cancellation
  + late-result guards prevent the prior session's Orders rendering in a new one.

TV1 must supply the real Auth session/client and choose the Customer route in
`src/app`. This patch intentionally has no global route or fixture entry page.
Synchronized main `91e1a6c2cc2e2f08dfb905386ba21e4d34668bf6` has no such shared
client/session: App still renders placeholders, shared contains domains.ts only,
Gateway application.yml has no business routes and Auth main has persistence only.
Do not infer that TV1 has written no implementation: its separate candidates below
contain substantial code, but are not merged/mounted or approved for Order routing.
Earlier variable-presence observations were local only, not deployment evidence.

### TV1 candidates inspected on 28/10/2026

| Candidate | Delivered source | Specific remaining Order History boundary |
|---|---|---|
| PR101 `e45cede29435bbce553cb244cb956493cb98c26b` | Password/JWT/bootstrap/current-account configuration, exact ACTIVE-only operational guard. | Not on main; not an Order Gateway/session binding. Do not reopen its already-fixed ACTIVE policy decision. |
| PR102 `36ad517174ef3911bf794ef4ac568647cd9eb4eb` | Gated Auth register/login/refresh/logout/me, real Gateway/Auth/PG acceptance source. | Gateway allowlist/RouteLocator/CORS cover only four POST Auth routes and GET `/api/auth/me`; even the candidate does not route `/api/orders`. Flags stay OFF. TV1 must add/review explicit Order GET forwarding/current-principal and downstream behavior. |
| PR105 `09c26c784d82dfc275e4ae8f0ce0b2a458ec4490` | Prepared createApiClient, session subscription, SharedRouter, role guards; isolated Phase-C source approval exists. | Not on main or mounted in App. SharedRouter Customer route is a Shell; useClient is private. Source approval explicitly excludes mounting/runtime integration. TV1 owns the route/provider seam and session lifecycle. |

There is no approved website mount contract to apply here. Keep this feature
independent. Once TV1 approves the concrete host change, it must:

1. Render this feature under the verified CUSTOMER guard with the actual client
   supplied through an agreed public prop/provider seam, not a private-hook import.
2. Derive `sessionScope` only for authenticated `/me` Customer sessions, for example
   from the validated account userId and sessionKey; all other states pass null.
   Change it on expiry/logout/account switch; never derive it from stored role text.
3. Adapt `client.request` errors: PR105 throws ApiError on non-2xx. The host wrapper
   must map trusted 401/403 to OrderHistoryError('unauthorized'), detail404 to
   'not-found', other HTTP failures to 'unavailable', preserving AbortSignal and
   not forwarding private error messages. Do not simply cast request as transport.
4. Reconcile request cache policy: PR105 RequestOptions currently excludes `cache`
   and its resource send builds a new init without it. TV1 must ensure authenticated
   Order reads are no-store end-to-end, in addition to existing same-origin,
   credentials-omit, header restrictions and redirect-error behavior.
5. Keep refresh/single-flight/replay entirely within the reviewed shared client;
   do not add another refresh implementation or retry Checkout writes here.

GET forwarding must preserve the path/query and original Bearer to Order, with
reviewed origin/CORS/session behavior. Do not expose internal routes or widen
authorization just to render this feature. Backend107 at
`b155f799eaea8e49837d5d2970a78b369d7c0924` stays unchanged; its Backend readiness
run40450777345/job122012719419 succeeded for the PR. TV1's published readback identifies
the runner checkout as synthetic merge `b4e86ab53b46cc1cc3d6e3a72702f48874d1c857`,
not a standalone b155f79 checkout; local acceptance below uses standalone b155f79.

## Behavior

- Real list/detail HTTP calls against107's DTOs, strict ID/page/snapshot validation,
  lossless decimal strings and unknown-field stripping. No currency/fee/status
  inference or current Product lookup. List route404 means unavailable; detail404
  means not found; 401/403 clear private data and show sign-in-required state.
- Customer list, stored status/time/totals, page navigation, two-Store item detail,
  back navigation, loading/empty/error/retry/access states. No auto-retry, create
  order, stock operation or payment action. React escapes source text.
- Exact NUMERIC strings stay strings, including `99999999999999999.99`. This UI
  does not append a currency missing from stored history. Known Order statuses
  PLACED/CANCELLED are displayed without inventing SellerOrder/COD lifecycle.
- Changing selected page/detail unmounts old request state. Session loss clears
  Orders synchronously; old HTTP results cannot replace current session data.

## Reproducible tests

Install the repository's existing frontend dependencies without creating a
lockfile or editing manifests. Protocol regression from `frontend`:

```bash
node --experimental-strip-types --test src/features/orders/orderHistoryClient.test.mjs
npm run build
npm run typecheck
```

Real acceptance uses a checkout of exact backend107 with the actual HTTP API.
Keep that checkout clean; the test-only init script registers a separate source
set in memory. From the backend107 repository root, using existing Java21/Docker:

```bash
bash backend/gradlew -p backend --init-script /absolute/frontend/src/features/orders/acceptance/history-ui.init.gradle -Ptv3UiRoot=/absolute/frontend -Ptv3DomModule=/absolute/test-tools/node_modules/happy-dom/lib/index.js :order-service:historyUiAcceptance --rerun-tasks --no-daemon --max-workers=2 --console=plain
```

This run used Node24.19, React19/Vite6.4.5, HappyDOM21.0.4 installed **outside the
checkout** for DOM tests, Java21.0.8/Gradle8.10, process UTC/API1.44 and actual
PostgreSQL16.6 Testcontainers/Flyway/JPA. No mock backend/security/repository is
substituted. Harness starts the unchanged107 server at a random local port, seeds
22 Orders for A and1 for B, and passes ephemeral test-signed Bearers only through
the child process environment. It never logs/commits keys or modifies production
credentials/flags. Missing harness configuration fails the Node suite, not SKIP.

Eight Node cases exercise actual HTTP: scoped pages, two-Store details, foreign/
missing404 and invalidJWT401, maximum amount exactness, DOM list/detail/pagination,
session loss, access rejection, and a delayed real A response after switching to B.
One JUnit wrapper orchestrates those8; do **not** count the wrapper as a ninth case.
Four separate protocol tests use synthetic Responses for malformed-data guards;
they do not replace the real8. HappyDOM interaction is not Chromium/browser E2E.

## Product -> Cart -> Checkout blockers, kept separate

| Boundary | Current implementation | Remaining delivery / owner |
|---|---|---|
| Product Catalog | PR90/91 merged; Product DB owns immutable unique SKU and price/currency, internal snapshot/search sources exist. | TV2/TV1 deliver callable authenticated provider; internal Java interfaces do not authorize a wire adapter. |
| C01/E01 | Ordered complete batch/no READY subset and explicit absence/unavailable exist at design/internal scope. Product positive routes remain denied by default. | TV2+TV3+TV1 finalize HTTP DTO/version/decimal strings/error/privacy, request bounds and consumer/provider tests. |
| Actor | OD09 design, no enabled Cart/Order->Catalog concrete pair. | TV1 + TV2/TV3 approve per-caller path/operation credentials, original customer delegation and current actor/privacy. |
| Store/Seller/House | Seller current DB facts exist; Product's CurrentStoreFactsSource is an unregistered dependency. | TV2+TV1 implement trusted current cross-service facts and explicit House authority; missing House stays unavailable, never false. |
| Cart expectation | V1 Cart/Catalog ports lack currency; Order CatalogPort lacks SKU. | TV3+TV2+TV5 agree versioned expectations/storage transition; no invented currency or Product-ID-as-SKU. |
| Freshness/recovery | No cross-source lease; last-observed A/B revalidation is a proposal. Mapper106 stays unwired; positive Inventory transport OFF. | TV1+TV5/named business owner decide race policy; TV3/TV4 implement stable-ID journal/ownership/C06/UNKNOWN recovery before actual stock dispatch. |

This feature proves stored Order history can be read/rendered from a real backend.
It does not prove a new Product purchase, live Auth-issued session/Gateway, Inventory
reservation, COD/settlement, Finance policy or two-Store purchasing E2E. Website
acceptance still requires TV1's host wiring and real browser/session tests.
