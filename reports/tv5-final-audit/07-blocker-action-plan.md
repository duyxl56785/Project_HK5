# Blocker action plan — 18/11/2026

| Priority | Blocker | Owner | Next action | Required evidence |
|---|---|---|---|---|
| P0 delivery gate | PR #204 corrections are delivered; current-head reviewer decision pending | TV2 / TV5 | TV2 re-review `0bd80aa`; merge only through approved maintainer flow before 20/11 12:00 | Review on exact head, CI/review decision, merge SHA if approved |
| P0 | No final frozen SHA / UAT receipt | TV2 | Publish full SHA and matching hosted CI | SHA, workflow URLs, clean-stack receipt, final UAT logs |
| P0 | Clean clone on another machine incomplete | TV2 / TV1 | Run exact frozen SHA on separate machine | Clone receipt, environment, exit code, E2E log |
| P1 | Final figures 6.1–6.8 absent | TV2, TV5 | Capture official mapping on frozen SHA | Raw images, timestamps, SHA256, masked PII, API/DB oracle |
| P1 | Finance runtime and settlement acceptance absent | TV5, TV2, business owner | Confirm scope and approvals; test approved isolated data | Fee snapshot, COD collection, Store scope, settlement receipt |
| P1 | Inventory RET-05 / UNKNOWN | TV4, TV3, TV2 | Resolve contract and case-level approvals | Approved operation tuple and provider/DB receipt |
| P1 | Retained Docker stack incomplete | TV2 environment owner | Restore required temporary secret mounts and PostgreSQL environment, or provide a clean approved runner | Container state, secret mount presence (without publishing values), PostgreSQL health, isolated stack health |

Do not reset Docker or remove shared data. Hosted CI and TV2's Windows rehearsal provide bounded evidence while the final environment is arranged.
