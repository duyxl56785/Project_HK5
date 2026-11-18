# Evidence audit — 18/11/2026

Receipt: `reports/minh-chung-kiem-thu/tv5-prefinal-552e8eb-20261115/`.

- Manifest entries: 16; all 16 files exist.
- Working-tree SHA256 recomputation: 16/16 MATCH.
- Relative paths: receipt-relative paths; no missing JDK/Gradle version logs are referenced.
- `test-summary.json`: JSON parse PASS; aggregate backend test count is recorded from JUnit XML in the tested checkout, which is not bundled.
- B.1 screenshot SHA256: `66510614854578543463737fbb653e630396208b872728041693c85269c03811`.
- `test-summary.json` was amended to preserve the historical Figure 6.4 label and add official 6.4/6.5/6.6 mapping; its final hash is included in the committed manifest.
- Git blob verification: 16/16 match at manifest commit `6ae6284`; subsequent audit-only commits do not alter receipt payloads.
- Current continuation at `169fe16d15157d2a7b24da45798e90f9fe2e29d1`: Python read-only byte comparison confirms all 16 working files are byte-identical to `git cat-file blob HEAD:<path>` and each SHA-256 matches `SHA256SUMS.txt` (16/16 MATCH).
- `git check-attr -a` confirms `diff` and `text` are unset for raw logs/JSON/screenshots and `SHA256SUMS.txt` remains `text`, `diff`, `eol=lf`.

This is PRE-FINAL evidence, not final UAT or a frozen-SHA release receipt.
