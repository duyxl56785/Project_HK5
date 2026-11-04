# PR #170 — one-shot proxy regression, 05/11/2026

Refs #18, #170, #175. Base source: `f90754deb6047796f554a2f6122e7d0079f69801`. All HTTP peers are explicitly fake loopback harnesses. No Docker command, Inventory/Finance flag, real provider request, demo database access or fault injection was used.

Command (Node v24.21.0):
`node --test --test-reporter=tap scripts/qa/tv4-unknown/lost-response.test.mjs`

| Run | PASS | FAIL | SKIPPED | Exit |
| --- | ---: | ---: | ---: | ---: |
| Original source plus six regressions (`red-tap.log`) | 4 | 6 | 0 | 1 |
| Fixed source plus two additional negative guards (`green-full-tap.log`) | 12 | 0 | 0 | 0 |

RED reproduces sequential replay forwarding three times, eight concurrent duplicates forwarding eight times, synchronous/asynchronous callback failures terminating child processes, timeout reopening the upstream path, and invalid-input-then-replay forwarding twice. Existing four tests were retained. GREEN additionally checks operation mismatch despite a matching byte fingerprint and a truncated upstream response producing no receipt.

The one-shot claim is set synchronously after validation and before the upstream call, with no await between check/set. It never resets on transport/receipt failure. Matching replay destroys downstream without forwarding or synthesizing a status. Invalid input retains its 400 semantics and does not consume the slot. Request bytes, operationId, headers, the 30-second production timeout and external retry semantics are unchanged.

Callback throw/rejected Promise emits only the fixed `UNKNOWN_PROXY_EVIDENCE_MISSING` diagnostic. Tests use real child Node processes to prove exit 0, no leaked exception detail and no reopened admission. The timeout test replaces only the socket timer with 100 ms and asserts the configured production value is still 30000 ms. No fake upstream 409/500, new key, RELEASE or compensation is introduced.

This is per-handler single-process mechanics, not durable deduplication across process restart, a provider COMMIT receipt or live UNKNOWN acceptance. #175 stays inactive; any later integration must update helper fingerprints and obtain fresh scoped review. Claim-conflict remains BLOCKED_DESIGN and final UAT NOT_RUN/BLOCKED.

Raw TAP logs are byte-preserved (`-text`); SHA256SUMS covers those logs only. Documentation/source diff checks are separate from raw CRLF/trailing whitespace. Do not normalize logs to conceal RED. GitHub CI and independent review status must be checked at the eventual published HEAD, not inferred from these local results.
