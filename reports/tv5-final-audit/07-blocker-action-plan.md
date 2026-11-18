# Blocker action plan — 18/11/2026

| Priority | Blocker | Owner | Next action | Required evidence |
|---|---|---|---|---|
| P0 delivery gate | PR #204 corrections are delivered; current-head reviewer decision pending | TV2 / TV5 | TV2 re-review remote head `c761364`; PR merge cutoff and main freeze are 20/11 18:00 (20/11 12:00 is the P0 reporting checkpoint, not merge cutoff) | Review on exact head, CI/review decision, merge SHA if approved |
| P0 | No final frozen SHA / UAT receipt | TV2 | Publish full SHA and matching hosted CI after freeze 20/11 18:00; final UAT is scheduled 21/11 | SHA, workflow URLs, clean-stack receipt, final UAT logs |
| P0 | Clean clone on another machine incomplete | TV2 / TV1 | Run exact frozen SHA on separate machine and preserve receipt | Clone receipt, environment, exit code, E2E log |
| P1 | Final Figures 6.1–6.8 captures absent | TV2 captures; TV5 validates | Capture the official Issue #162 mapping on frozen SHA: 6.1 desktop homepage; 6.2 mobile 390px; 6.3 Seller product management; 6.4 Admin Seller approval; 6.5 two-Store Cart; 6.6 root Order/two SellerOrders; 6.7 Seller isolation; 6.8 platform finance/COD | Raw images, timestamps, SHA256, masked PII, API/DB oracle; 6.8 wording must say “đã ghi nhận đối soát”, not “tiền đã trả” |
| P1 | Finance runtime and settlement acceptance absent | TV5, TV2, business owner | Confirm scope and approvals; test approved isolated data | Fee snapshot, COD collection, Store scope, settlement receipt |
| P1 | Inventory RET-05 / UNKNOWN | TV4, TV3, TV2 | Resolve contract and case-level approvals | Approved operation tuple and provider/DB receipt |
| P1 | Retained Docker stack incomplete | TV2 environment owner | Restore required temporary secret mounts and PostgreSQL environment, or provide a clean approved runner | Container state, secret mount presence (without publishing values), PostgreSQL health, isolated stack health |

**Post-freeze rule (TV2, Issue #160, 17/11):** on 21/11 only P0 defects blocking Cart → Checkout → Order, incorrect money/inventory, or cross-Store data exposure may be fixed. Each fix needs a red test before and green after, a small PR from frozen SHA, TV2 review and merge with `--match-head-commit`. Final P0 merge cutoff is 21/11 14:00; after that, do not merge further changes and document known issues. P1/P2 are report limitations.

Do not reset Docker or remove shared data. Hosted CI and TV2's Windows rehearsal provide bounded evidence while the final environment is arranged.
