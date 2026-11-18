# Evidence audit — 18/11/2026

Receipt: `reports/minh-chung-kiem-thu/tv5-prefinal-552e8eb-20261115/`.

- Manifest entries: 16; all 16 files exist.
- Working-tree SHA256 recomputation: 16/16 MATCH.
- Relative paths: receipt-relative paths; no missing JDK/Gradle version logs are referenced.
- `test-summary.json`: JSON parse PASS; aggregate backend test count is recorded from JUnit XML in the tested checkout, which is not bundled.
- B.1 screenshot SHA256: `66510614854578543463737fbb653e630396208b872728041693c85269c03811`.
- `test-summary.json` was amended to preserve the historical Figure 6.4 label and add official 6.4/6.5/6.6 mapping; its final hash is included in the committed manifest.
- Git blob verification: 16/16 match at manifest commit `6ae6284`; subsequent audit-only commits `f74d08c`, `1a5c5ac`, `bc8547a` and `0f3c739` do not alter receipt payloads.
- Continuation working-tree recomputation at `0f3c7398c23bc07fb190b28deea4f9271e3b3180`: 16/16 MATCH. `git check-attr -a` confirms `diff` and `text` are unset for raw logs/JSON/screenshots and `SHA256SUMS.txt` remains `text`, `diff`, `eol=lf`.
- The follow-up audit commit `0bd80aa` changes only files under `reports/tv5-final-audit/`; the 16 immutable payload blobs and manifest are unchanged.

This is PRE-FINAL evidence, not final UAT or a frozen-SHA release receipt.
