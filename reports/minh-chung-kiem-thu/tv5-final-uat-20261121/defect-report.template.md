# Defect report — template only

| Field | Actual value |
|---|---|
| Defect ID | `<issue URL/ID>` |
| Related test ID / run ID | `<case and run>` |
| Frozen SHA | `<full SHA>` |
| Severity | `P2` |
| Owner | `<TV1/TV2/TV3/TV4/TV5>` |
| Reporter / timestamp | `<identity / ISO-8601 +07:00>` |
| Environment / flags | `<observed runtime config>` |
| Preconditions | `<fixture and actor>` |
| Steps to reproduce | `<exact steps actually performed>` |
| Expected | `<approved oracle>` |
| Actual | `<observed behavior/status>` |
| Frequency | `<observed count, not estimate>` |
| Side effects | `<Order/stock/COD/Finance changes or none observed>` |
| Raw evidence | `<restricted path + SHA256>` |
| Redacted evidence | `<shareable path>` |
| Workaround / risk | `<facts only>` |
| Triage decision | `PENDING OWNER` |

## Severity guide

- **P0:** release-blocking data/security/money/inventory/authorization corruption or core acceptance failure. TV2/owner triage required; no unilateral downgrade.
- **P1:** major feature/flow failure without a safe release workaround; owner and release scope decision required.
- **P2:** lower-impact issue, cosmetic/documentation/advisory or safely deferred defect with explicit owner.

Never label severity from speculation alone. Preserve failing raw artifacts.
