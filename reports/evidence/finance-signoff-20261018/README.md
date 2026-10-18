# Finance sign-off QA evidence

Refs #5/#12/#28. [Report](../../TV5_FINANCE_QA_2026-10-18.md).

- head-evidence: fresh TV5 execution of unchanged PR28 bd5ff09, 61 unit +11 PG PASS.
- final-evidence: fresh TV5 execution of supplementary test source44f2838, 72 unit +13 PG PASS.
- Every result above:0 failures/errors/skips; original logs/XML/exit files retained byte-for-byte.
- run.json records command, executor/environment/source and per-task counters. No live external E2E.
- tested-sources.json hashes final Cart/Order source/resources; no runtime source changes in the patch.
- docker-preservation.json compares before/after:0 missing pre-existing containers/volumes.
- manifest.sha256 covers46 raw/metadata artifacts, excluding this README, attributes and manifest itself.
- Scoped -text attributes preserve CRLF generated artifacts. Do not normalize logs to satisfy diff-check.
- Fresh TV5 results are not TV2 results, old repo evidence or report V2 claims; do not sum executions.

The scale-boundary PG case is a characterization of FIN-01, not approval of precision loss.
Production unique settlement, verified Store Finance queries, COD authority and live UAT remain blocked.
