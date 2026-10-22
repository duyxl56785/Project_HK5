# Seller/Store Overview — TV2

Refs #2 #9. One private-management presentation feature, independent of Category PR74. No Auth/Gateway/Router/API client or backend edits. No registration/save/delete requests, fake success, authority adapter or live API provider.

## Scope / plan

- [x] Read merged `SellerStoreResponses.java`, `CanonicalVersion`, SellerStatus and StoreDetails. Model only actual display fields and preserve canonical version text; validate consistency and omit private reasons/unknown fields.
- [ ] Build controlled overview: four Seller statuses, Store open/closed/profile/version, no-Store and loading/error/retry/empty/unauthorized/unavailable states. Scoped CSS and no write actions.
- [ ] Test/render/browser/viewport/typecheck/app+feature bundle, review, commit/push one separate feature PR. Keep API/Auth/Router/E2E pending.

## Data and security boundary

Merged backend `SellerPrivate` fields are `id,version,status,reviewReason`; `StoreOwner` fields are `id,sellerId,name,slug,open,version,createdAt,updatedAt`. These are **internal mapped DTOs / HTTP candidate**, not exposed endpoints. This frontend uses display projections: Seller `id,version,status`; Store `id,sellerId,name,slug,open,version`. It introduces no wire contract. No reviewReason, ownerReference, role, token, timestamps or arbitrary object is rendered/exported through the projection.

**Ready props must come from a server-authorized own-management response.** A future TV1 container must verify authenticated identity/current authority/resource scope on the server before constructing ready state. The component/state/parser cannot authorize anyone, and no role/verified boolean from a client is accepted as authority. Do not mount private data on a public Catalog route. Unauthorized, unavailable, loading or error states do not render stale/private payloads even if extra fields are present.

`store:null` means the backend has proved no Store exists; undefined/missing/malformed Store or mismatched Seller ID is a data error. Do not map timeout/403/503 to null or an empty success. `empty` is reserved for proved Seller absence. Store `open` is a stored flag, not current Catalog sellability or a permission grant. A suspended/rejected Seller may still have a stored Store record; UI must not invent a selling permission.

Versions follow the existing canonical `0|[1-9][0-9]*` and Java Long.MAX_VALUE cap. Validation compares string length/lexical order, never JS Number, parseInt or floating point. Display preserves the exact string, including `9007199254740993` and `9223372036854775807`. Names/IDs are not normalized; Store slug/name lengths follow existing DTO/domain sources.

## Foundation checks

From `frontend`: `node --test src/features/seller/sellerStoreModel.test.mjs`, `npm run typecheck`. Reuse the installed toolchain; no project dependencies/config/lockfile changes. Test data remains in test/preview entries only.
