# Biên bản Final UAT — template only

## Thông tin phiên

| Field | Actual value |
|---|---|
| Date / time (+07:00) | `<start-end ISO-8601>` |
| Run ID | `<unique run ID>` |
| Frozen main SHA | `<TV2-confirmed full SHA>` |
| Freeze confirmation | `<TV2 decision URL>` |
| Operator | `<TV2 identity>` |
| Participants | `<TV2/TV3/TV4/TV5 identities>` |
| Environment | `<OS/tools/Compose project/image digests/DB identity/ports>` |
| Runtime flags | `<observed values and approval URLs>` |
| CI | `<readiness/frontend/E2E run IDs and conclusions>` |
| Evidence root | `<relative path>` |

## Phạm vi và kết quả

| Test ID/group | Executed? | PASS | FAIL | BLOCKED | NOT RUN | Evidence index |
|---|---|---:|---:|---:|---:|---|
| Baseline (17) | `<actual>` | `<n>` | `<n>` | `<n>` | `<n>` | `<path>` |
| Voucher C1 (5) | `<actual>` | `<n>` | `<n>` | `<n>` | `<n>` | `<path>` |
| Promotion (4) | `<actual>` | `<n>` | `<n>` | `<n>` | `<n>` | `<path>` |
| Voucher C2 (11) | `<actual>` | `<n>` | `<n>` | `<n>` | `<n>` | `<path>` |

## Decisions

- C2 decision and scope: `<TV2 decision; default OFF unless all gates pass>`
- Finance policy scope: `M01 MD01–MD11 local demo/submission only; production NOT APPROVED unless a later decision exists`
- P0/P1/P2 triage: `<links and owner decisions>`
- UNKNOWN claim-conflict/lost-response: `<BLOCKED/NOT RUN unless separately authorized and executed>`
- RET-05: `<BLOCKED unless TV4 contract/implementation and exactly-once receipt accepted>`
- Deviations/waivers: `<signed decision links>`

## Sign-off

| Role | Name/identity | Decision | Evidence / timestamp |
|---|---|---|---|
| TV2 operator / release owner | `<identity>` | `PENDING` | `<link/time>` |
| TV3 Checkout/Order | `<identity>` | `PENDING` | `<link/time>` |
| TV4 Inventory/COD/Return | `<identity>` | `PENDING` | `<link/time>` |
| TV5 QA Lead | `<identity>` | `NOT SIGNED` | `<reason and blockers>` |

This template is not a completed UAT record or QA sign-off.
