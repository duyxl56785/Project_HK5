# TV5 pre-final test matrix — main@552e8eb

| Test ID | Preconditions and steps | Expected | Actual | State | Evidence |
|---|---|---|---|---|---|
| BE-01 | Run Gradle wrapper `--version` via H: | Gradle 8.10, JDK 21 | Gradle 8.10 / JDK 21.0.7 | PASS | `logs/backend/tv5_backend_build_forced_552e8eb.log` |
| BE-02 | Clean compile/build and rerun all backend tasks serially | Build and tests complete | 97 tasks executed; exit 0 | PASS | same build log |
| BE-03 | Aggregate backend JUnit XML | No failures/errors | 1,180 unit + 497 integration; 1,661 pass, 16 skipped | PASS | module `build/test-results` in tested checkout |
| FE-01 | npm ci, typecheck, unit, production build | All commands exit 0 | 178 unit pass; install/typecheck/build pass | PASS | `logs/frontend/` |
| SM-01 | Start isolated Compose rehearsal and wait for services | Stack ready in isolated project | 15 containers running; command exit 0 | PASS (smoke only) | `logs/backend/tv5_isolated_uat_smoke_552e8eb.log` |
| SM-02 | Read Gateway health, categories, Catalog, frontend | HTTP 200 | All four returned 200 | PASS (smoke only) | smoke log; ports in `rehearsal-before-uat.json` |
| DB-01 | Verify application DB migration histories | 8 application DBs have Flyway history | 8/8 true | PASS (smoke only) | `rehearsal-before-uat.json` |
| CAT-01 | Check API Catalog and DB seed | Active products and Stores are visible | 6 API items; 6 ACTIVE DB products across 3 OPEN Stores | PASS (seed/API check only) | product/store read-only files |
| UI-B1 | Open the real running frontend and capture homepage/Catalog | Screenshot shows Catalog | Real Chrome capture saved; no search/filter action or SKU visible | PRE-FINAL PARTIAL | `screenshots/B1-catalog-pre-final.png` |
| UAT-B2 | Customer cart contains products from two Stores | One session, both Store groups, totals | Not attempted | NOT RUN | Gate/owner approval pending |
| UAT-B3 | Single root Order and two SellerOrders with matching states | Same-run API/DB receipts | Not attempted | NOT RUN | Checkout not run; Inventory pair gate OFF |
| UAT-B4 | Seller Store isolation and Finance amount | Cross-Store denial and DB snapshot | Not attempted | NOT RUN | Finance authorization/gate OFF |
| UAT-B5 | Admin moderation and COD | Actor, transition, audit/DB receipt | Not attempted | NOT RUN | Finance authorization/gate OFF |
| CHK-6.4 | Figure 6.4 real two-Store cart/checkout image | Cart/checkout screenshot and matching receipt | No two-Store cart or checkout created | BLOCKED / NOT RUN | Await approved final UAT runner and frozen SHA |
| UAT-COD | COD business flow | Approved order/payment transitions | Not attempted | BLOCKED / NOT RUN | Finance policy authorization not provided |
| UAT-SET | Settlement business flow | Consistent per-Store settlement | Not attempted | BLOCKED / NOT RUN | Finance policy authorization not provided |
