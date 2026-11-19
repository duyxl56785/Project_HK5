# P0/P1/P2 triage summary — template only

| Defect ID | Severity | Test ID | Frozen SHA | Observed impact | Owner | State | Evidence | Fix/waiver decision |
|---|---|---|---|---|---|---|---|---|
| `<issue>` | `P0/P1/P2` | `<ID>` | `<full SHA>` | `<observed facts>` | `<owner>` | `OPEN` | `<raw/redacted path + hash>` | `<decision URL or PENDING>` |

## Release gate summary

| Gate | Current value | Evidence | Authorized decision maker |
|---|---|---|---|
| P0 open count | `NOT TRIAGED` | `<Issue/search snapshot>` | TV2 + affected owners |
| P1 accepted/deferred | `NOT TRIAGED` | `<issue + waiver>` | TV2 + affected owners |
| P2 backlog | `NOT TRIAGED` | `<issue + owner>` | Relevant owner |
| UNKNOWN claim-conflict | `BLOCKED_DESIGN / NOT RUN` | `<runner/tuple/provider oracle missing>` | TV2/TV3/TV4 |
| UNKNOWN lost-response | `BLOCKED_OWNER / NOT RUN` | `<reviewed runner/provider COMMIT receipt missing>` | TV2/TV3/TV4 |
| RET-05 restock | `BLOCKED` | `<TV4 contract + exactly-once evidence missing>` | TV4 + TV2 |
| Finance production | `NOT APPROVED` | `<separate production policy decision missing>` | TV2/business owner |
| Voucher C2 | `OFF / BLOCKED pending frozen-SHA CI + P0 triage` | `<run and decision URLs>` | TV2 |

Do not close or downgrade any defect without owner decision and retained evidence. A waiver must identify scope, risk, expiry and signer.
