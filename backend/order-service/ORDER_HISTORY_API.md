# Customer Order History — TV3

Refs #3/#10/#16. Backend read API only; Customer website integration is not complete.
Implemented against current Order Flyway V1/V2 without schema/entity/Finance/Auth/Gateway edits.

## Endpoints

Both endpoints require an access JWT verified by the existing HS256/kid/issuer/audience/
time/role contract. `CustomerIdentity` then requires CUSTOMER and uses the exact JWT
subject as customer ID. No query/body/header-supplied customer identity is used.
Tokens with SELLER/ADMIN only receive 403; absent/invalid JWT receives 401 under the
existing resource-server contract. No Auth verifier, credential or runtime flag is changed.

`GET /api/orders?page=0&size=20`

- Zero-based page; default size20, allowed1..100. Invalid/negative/out-of-range integer
  inputs receive400. A page beyond the last page returns an empty content array with
  the actual total. Unknown query parameters do not select customer or sorting.
- Fixed newest-first order: `created_at DESC, id DESC`; UUID breaks timestamp ties.
- Response: `{content:[{orderId,status,total,createdAt,sellerOrderCount}],page,size,totalElements,totalPages}`.
- Amounts are lossless plain decimal **strings**, e.g. `"60.00"`, from PostgreSQL
  snapshots; no floating-point conversion, currency/fee inference or repricing.
  `createdAt` is an ISO8601 UTC instant; IDs are UUID strings. Page metadata is numeric.

`GET /api/orders/{id}`

- Response: `{orderId,status,total,createdAt,sellerOrders:[{sellerOrderId,storeId,gross,items:[{productId,quantity,unitPriceSnapshot,lineTotal}]}]}`.
- Reads only stored snapshots. It does not call Product, Seller, Inventory or Finance.
  Missing product titles/currency/fulfillment states are not invented.
- A foreign Order and an absent UUID both receive404 with the same current error
  envelope: `{"code":"REQUEST_REJECTED","message":"Order not found"}`.
  Malformed UUID receives400. No customer ID, seller owner identity, idempotency key,
  outbox/journal/attempt or security metadata is returned.
- Child queries are also scoped to the authenticated customer; Store/items are ordered
  by their persisted IDs for deterministic responses. Stores with no items return `[]`.
- Monetary strings are stored values, not approval of any commission/COD policy.

## Storage and pagination

`OrderHistoryRepository` uses parameterized JdbcTemplate projections. List requires
exactly two SELECTs: scoped count and root-Order LIMIT/OFFSET with Store count. Detail
uses a scoped root query plus one joined Store/item query (one query for404). There
is no ORM collection fetch pagination or lazy per-Order loading. Existing checkout
OrderRepository/entities/migrations remain untouched.

Service read transactions use REPEATABLE_READ for a consistent count/page or
root/children snapshot within each request. Offset pages across separate requests
can shift when orders are inserted/deleted; this is not a persistent cursor/lease.
For very deep histories, evaluate cursor pagination/indexing separately using real
workload evidence rather than changing the shared schema in this feature.

## Verification

From repository root, with existing Java21/Gradle8.10/Docker:

```bash
bash backend/gradlew -p backend :order-service:test --tests '*OrderHistoryServiceTest' :order-service:postgresIntegrationTest --tests '*OrderHistoryPostgresIT' --rerun-tasks --no-daemon --max-workers=2 --console=plain
bash backend/gradlew -p backend --init-script "$PWD/tests/finance/fin06-regression.init.gradle" :order-service:test :order-service:postgresIntegrationTest :order-service:fin06Regression --rerun-tasks --no-daemon --max-workers=2 --console=plain
```

Unit tests mock only the read repository to exercise boundary failures. Integration
tests run a real random-port HTTP server, unchanged JWT verifier/security filters,
real JPA seeding, Flyway and PostgreSQL16.6 Testcontainers. No PostgreSQL/repository/
security beans are mocked. Ephemeral locally signed test JWTs verify resource-server
behavior; they do not prove tokens issued by the live Auth service or Gateway routing.

The test-only DataSource decorator delegates to the real PostgreSQL driver and
counts actual SELECT executions on the service thread: 2 for page sizes1/25, 2 for
detail with multiple Stores/items. Tests also cover owner/foreign isolation, query
identity spoofing, pagination/timestamp ties, empty/missing data, invalid pagination,
real bad signature/expired/absent JWT and non-Customer roles, large decimal exactness
and empty child collections. Successful HTTP samples are generated under ignored
`backend/order-service/build/order-history-samples/`.

## Frontend/TV1 handoff

- TV1 must review integration of GET collection/detail through Gateway, trusted Auth
  session/access-token delivery, shared API client, router and 401/403 handling. This
  PR changes none of those modules and requests no production credential or flag.
- Customer UI should paginate, preserve decimal strings, show loading/empty/error,
  and handle404 uniformly. A money string is not permission to append a currency
  absent from the stored schema. Read the backend contract above before adapting UI.
- Historical rows can be read while live Checkout/provider gates remain closed.
  No claim of real Product purchase, current Store/House authority, Inventory stock,
  COD settlement, Finance policy, live Auth/Gateway E2E or website completion follows.
