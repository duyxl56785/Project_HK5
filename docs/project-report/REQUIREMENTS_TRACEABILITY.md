# V2 requirements traceability — baseline 29/10/2026

**Source:** user-supplied `Bao_cao_phan_tich_thiet_ke_Smart_ECommerce_V2_FINAL(1).docx` (55 pages, report dated 12/10/2026). **The original DOCX is NOT contained in this PR**; the maintainer must add the vetted original file separately. Reference on `main`: `reports/TV5_V2_RECONCILIATION.md`. This table is a requirements inventory, **not** a claim of live implementation.

**Evidence model:** DONE only after exact production source + automated tests + relevant real HTTP/DB/E2E verification at the reviewed SHA. PARTIAL = some implementation evidence but no complete end-to-end acceptance; MISSING = source not located after focused review; BLOCKED = identifiable dependency; UNVERIFIED = requires code/test audit. Draft PR, green unit tests or historical M8 results alone never imply DONE. The statuses below are conservative and refer to `main@bb0b9a2` and cited Draft PR snapshots on 29/10, not a full audit of every service.

| Req | Baseline acceptance (report §2.2, §3.2; pages 9–11, 18–19) | Owner / implementation references | Status at snapshot | Next evidence |
|---|---|---|---|---|
| F01 | Register, login, refresh, logout; reject invalid JWT | Auth; #102, #120, #123 | PARTIAL | Current live Gateway/Auth flow, review and integration |
| F02 | Own profile/shipping addresses; at most one default | User service | UNVERIFIED | Source + PG ownership + HTTP |
| F03 | Seller apply, edit rejected application, resubmit | Seller; #85, #118 | PARTIAL | JWT-verified Seller HTTP + PG |
| F04 | Admin approve/reject/suspend/reinstate Seller, reason, role grant/revoke | Seller/Auth; #85, #118, #121–#123 | BLOCKED | Dispatcher + current authority + live HTTP/PG |
| F05 | One Store per approved Seller; unique slug | Seller; #94 and #85 | PARTIAL | Store HTTP + owner/role tests |
| F06 | Seller products, max 5 JPEG/PNG/WebP images <=2MB, inventory and submit | Product; #88/#89/#116, Inventory | PARTIAL | Protected HTTP, image lifecycle, inventory E2E |
| F07 | Admin Product moderation and acyclic Category tree | Product/Category | PARTIAL | Admin JWT/current-role HTTP + PG |
| F08 | Public search/filter/sort/pagination; only sellable products | Product/Seller; Catalog, Store facts | BLOCKED | Exact Store facts provider, pagination + HTTP |
| F09 | Multi-Store Cart grouped by Store, revision/claim | Cart; TV3 | UNVERIFIED | Current code and HTTP/PG E2E |
| F10 | One checkout -> 1 parent + N Store orders, idempotent, no partial stock deduction | Cart/Order/Inventory/Product/Seller | UNVERIFIED | Real multi-service checkout/retry E2E |
| F11 | Seller own order confirmation/pack/ship/deliver/reject | Order/Inventory | UNVERIFIED | Signed authority, ownership + status E2E |
| F12 | Customer own suborder tracking/cancel while pending; restore stock once | Order/Inventory | UNVERIFIED | HTTP + PG cancellation/release evidence |
| F13 | Admin COD paid only after delivery; idempotent; house Store | Order/Finance | UNVERIFIED | Role + COD + financial invariants |
| F14 | In-app notifications from outbox, consumer idempotency/DLT | Notification/Order | UNVERIFIED | Kafka outage/replay + live UI |
| F15 | Verified delivered-item reviews, 1–5 stars, Admin hide/unhide | Product/Order | UNVERIFIED | Delivered event + reviews HTTP/PG |
| F16 | Four revenue/reporting groups, 5% commission (house 0%), settlement only delivered & paid | Order/Finance | UNVERIFIED | Finance contract and reconciliation tests |
| F17 | Marketplace homepage, images, admin house-product images | Frontend/Product; #95/#105/#110/#116 | PARTIAL | Mounted frontend + signed live HTTP + screenshots |

## Required quality / architecture constraints

- Actors: guest, CUSTOMER, SELLER, ADMIN; one account may have CUSTOMER + SELLER (report pp.18,23).
- One Store per Seller; 8 business services behind Gateway, service-owned PostgreSQL databases, no cross-service SQL reads (pp.7,30–35).
- Sellable = Product ACTIVE AND Seller APPROVED AND Store sellable; backend owns permission/ownership, fail-closed on uncertain current state (pp.20–21).
- One parent order plus Store-specific child orders; COD simulation, commission 5% (house 0%); stock decrement/compensation idempotent (pp.7–8,25,37–39).
- Product 1–5 validated images, 2MB each; image metadata/ownership and moderation transitions (pp.10,20,27,29,54–55).
- Gateway routes `/api/**` only; `/internal/**` pair-scoped, nonpublic (pp.31,40–41). New approved exact wire contracts may supersede the older report examples; record the delta rather than silently reverting them.
- Responsive UI >=375px, loading/empty/error, confirm destructive actions and adequate contrast (pp.20,42–43).
- Acceptance requires the **12-step real Docker demonstration** (p.7); verify source-specific evidence. Report p.44 logs historical M8 16 live PASS/1 NOT_RUN, not proof of the current `main`.

## Differences / review queue

1. The original report is an October 12 design snapshot and includes historical M8 claims; it is **not** a substitute for present-day code and CI. See `reports/TV5_V2_RECONCILIATION.md`.
2. Report §9.2 p.41 describes a direct Seller→Auth role grant path. Current proposed implementation uses full-state versioned Role Sync (`/internal/auth/seller-role-sync`) and scoped current-account RPC. Keep final, reviewed implementation contract and record divergence.
3. Current source policy for exact integer VND and ceilings may supersede the report's older NUMERIC/HALF_UP description; validate approved M01 before touching financial schema.
4. PR #119 CI Docker fix, #120 Security Core, #121 transactional Role Sync, #122 HTTP receiver and #123 current-account RPC are stacked **Draft** code, not merged production approval. Their CI results must not be labeled live business E2E.
5. Before marking any Fxx DONE, link exact file(s), SHA, JUnit/Playwright run URL and cross-service scenario; request TV5 independent review.
6. Report Appendix B (p.53) still contains five TODO live screenshots; these must be obtained from real running UI before final submission.

## Next development priority

1. Product→Seller Store Facts batch provider with scoped pair, real PostgreSQL/HTTP and fail-closed semantics.
2. Seller #118 durable outbox dispatcher/ACK/reconciliation and Role Sync E2E.
3. Verified authority middleware before business transactions, protected Seller/Product HTTP (#85 etc.), Gateway allowlist.
4. React shared router/API client integration, 12-step live Docker E2E, then remaining MUST gaps in F02/F09–F13; SHOULD F14–F16 and F17 per baseline priorities.

**Document status:** baseline inventory only; pending full code-by-requirement review and original DOCX incorporation.
