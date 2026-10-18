# FIN-06 Checkout diagnostic evidence

[Report](../../TV5_FIN06_CHECKOUT_RED_2026-10-18.md), [run instructions](../../../tests/finance/README.md).
Refs #5/#12/#28/#37/#42. **Known RED, not a passing Finance implementation.**

- red/: original Gradle log +JUnit XML:3tests,1PASS/2FAIL/0errors/0skips,exit1. Real PG, mocked remote ports.
- catalog-evidence/: targeted changed Catalog unit suite:50PASS/0FAIL/error/skip,exit0,source63668b8.
- qa-union.json: derived exact-byte merge/content and15relative-link verification of parent28dd0cf.
- source-binding.json: derived binding of diagnostic5fa10ce to files executed before commit; raw metadata intact.
- manifest.sha256 hashes11raw/metadata artifacts (excluding thisREADME/attributes/manifest).
- Original bytes preserved with -text; never alter failure output or normalize CRLF to satisfy diff-check.
- Docker preservation:0missing existing containers/volumes. No live Inventory, settlement or E2E claim.
- Separate these executions from historical73unit+17PG and from CI readiness; never aggregate release counts.
