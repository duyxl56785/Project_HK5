# Evidence audit — 18/11/2026

Receipt: `reports/minh-chung-kiem-thu/tv5-prefinal-552e8eb-20261115/`.

- Manifest entries: 16; all 16 files exist.
- Working-tree SHA256 recomputation: 16/16 MATCH.
- Relative paths: receipt-relative paths; no missing JDK/Gradle version logs are referenced.
- `test-summary.json`: JSON parse PASS; aggregate backend test count is recorded from JUnit XML in the tested checkout, which is not bundled.
- B.1 screenshot SHA256: `66510614854578543463737fbb653e630396208b872728041693c85269c03811`.
- `test-summary.json` was amended to preserve the historical Figure 6.4 label and add official 6.4/6.5/6.6 mapping; its final hash is included in the committed manifest.
- Git blob verification: 16/16 match at manifest commit `6ae6284`; subsequent audit-only commit `f74d08c` does not alter receipt payloads.

This is PRE-FINAL evidence, not final UAT or a frozen-SHA release receipt.
