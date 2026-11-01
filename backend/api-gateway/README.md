# api-gateway

**Owner:** TV1 (implemented by TV2 under the 2026-10-30 hand-over).

Single public entry point on port 8080 (Spring Cloud Gateway, reactive).

## Routing

| Public path | Service |
|---|---|
| `/api/auth/**` | auth-service (8101) |
| `/api/users/**` | user-service (8102) |
| `/api/sellers/**`, `/api/admin/sellers/**`, `/api/stores/**` | seller-service (8103) |
| `/api/categories/**`, `/api/catalog/**`, `/api/seller/products/**`, `/api/admin/products/**`, `/api/admin/reviews/**`, `/api/reviews/**` | product-service (8104) |
| `/api/cart/**` | cart-service (8106) |
| `/api/orders/**`, `/api/seller/orders/**`, `/api/seller/finance`, `/api/admin/store-orders/**`, `/api/admin/finance`, `/api/admin/outbox/**` | order-service (8107) |
| `/api/notifications/**` | notification-service (8108) |

Upstream URLs come from `AUTH_URL`, `USER_URL`, `SELLER_URL`, `PRODUCT_URL`, `CART_URL`, `ORDER_URL`, `NOTIFICATION_URL`.

## Edge policy

- `/internal/**` and `/api/inventory/**` are never routed (`EdgeGuardFilter` returns 404 as a second guard).
- Client-supplied `X-Internal-Token`, `X-Inventory-Internal-Token`, `X-User-Id`, `X-Roles`, `X-Forwarded-User` are removed. `Authorization` is forwarded; each service verifies the JWT itself.
- CORS only for `ALLOWED_ORIGINS` (comma-separated), with credentials for the refresh cookie.
- Request bodies are capped at 11 MB; responses get `nosniff`, `no-referrer` and `DENY` framing headers.
- The gateway pins Spring Cloud Gateway 4.1.5 (see `build.gradle`): 4.1.6 needs Spring Framework 6.1.15+.

Tests: `./gradlew :api-gateway:test` (routes against a local stub upstream, blocking, header stripping, CORS).
