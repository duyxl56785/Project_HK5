# PR #145 — main sync evidence, 2026-11-02

Normal merge commit: `8aa7f045448c3e729a0dcb26cdc03e20d30f5d69`. Parents: `a775773e65561067aa5167d104c3027b30759a78` (TV3) and `c11044d693eedd9b1b1d7d12b19d7daa7f7781a4` (main). Tested tree: `c4117c4a3a38553d886709507f219e0ac44f3c81`. This directory is a subsequent documentation-only publication; tests bound to the merge tree, production/test inputs unchanged by evidence publication.

## Merge and source audit

Fast-forwarded local branch to remote a775773, preserving user Javadoc fix. Normal merge had zero conflicts. All 66 incoming files are main Finance UI/lock/docs/previous evidence (see changed-files); no backend source difference from TV3 remote. Main-owned frontend files match main byte for byte; no manual edits to #115. User OrderEvents.java remains exact a775773 bytes.

V1–V4 unchanged; V5 remains unoccupied on main and identical to the reviewed TV3 migration. Per-file hashes in merge-source.json. Passing Order PostgreSQL suite exercises V5; no production/shared database migration. Existing rehearsal evidence remains historical.

T1/T2 source audit: REQUIRES_NEW draft commit precedes Inventory HTTP; ambient transaction rejected; immutable IDs/SKU/quantity/shipping/money binding retained; replay fingerprint+persisted shipping guards intact; PostgreSQL claim predicate requires PENDING atomically; generic caller rejects UNKNOWN; generation-fenced T2 cannot accept stale response; no automatic UNKNOWN retry/release/resolution. TV4 CONSUME-at-DISPATCHED behavior unchanged. Local source audit and regressions do not replace owner contract approval or production recovery acceptance.

## Actual results

| Group | PASS | FAIL/ERROR | SKIP |
| --- | ---: | ---: | ---: |
| Order unit | 156 | 0 | 0 |
| Order PostgreSQL | 103 | 0 | 0 |
| Cart unit | 11 | 0 | 0 |
| Cart PostgreSQL | 13 | 0 | 0 |
| Inventory unit | 27 | 0 | 0 |
| Inventory PostgreSQL | 19 | 0 | 0 |
| Focused subtotal (included below) | 329 | 0 | 0 |
| Full backend build, all final XML once | 1646 | 0 | 16 |

Full build final exit 0; frontend typecheck exit 0. Three earlier invocations genuinely FAILED due to environment setup; retained in failed-attempts-sanitized.json and raw hashes, not relabeled PASS. 15 skipped tests require existing `TV1_DATABASE_TESTS=true` source gates; one requires `AUTH_EMAIL_INVENTORY_JDBC_URL` to an owner-approved existing inventory target. No source skip added, no live/owner DB selected to force these green. Full local build is not a claim that those 16 tests ran.

Real PostgreSQL integrations ran using disposable Testcontainers. Unit suites include mocks, HTTP fixture suites remain fixtures. No browser/live business test was run by this sync. **Historical live acceptance remains 3 PASS / 2 UNKNOWN BLOCKED** at application bf8eb3a, not fresh live evidence for this merge. Fault fixture/operation identity/receipt still require TV4/TV5.

## Evidence and safety

Commands/exits: commands.md and task extracts. Final sanitized JUnit retains all cases, removes stdout/stderr/properties/hostname; raw local XML/log bytes remain unchanged and are hashed in raw-input-sha256.json. Sanitized failure extract retains actual stack and selected container root errors. Hashes of published artifacts: SHA256SUMS.

All 15 existing demo containers retained identical IDs, running state, start time, restart count and mounts; no demo volumes deleted, no live fault injection. Pre-existing dirty files in other worktrees preserved; candidate dfe9032 and saved b903a6d/04876a1 refs unchanged. No production flags/Auth/Gateway/Finance/Inventory source modifications. No PR #144 interaction or thread resolution.

GitHub API read currently fails `403 Forbidden` (gh GraphQL exit 1). PR description must preserve owner text; prepared append-only update in PR_DESCRIPTION_UPDATE.md for owner if API remains unavailable. Public PR/check pages and remote Git SHA are used for readback, not a fabricated CI success.
