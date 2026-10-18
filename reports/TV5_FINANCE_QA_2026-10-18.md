# TV5 — Finance contract and current-head regression evidence

2026-10-18 (Asia/Bangkok), Refs #5/#12/#28/#37. **PARTIAL / OWNER SIGN-OFF BLOCKED**.

## Executed now, not borrowed from an earlier review

Windows, Java21.0.7, Gradle8.14.3, Docker29.8.1, Testcontainers PostgreSQL16-alpine.
Process-only `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`.
From repository worktree, using the locally installed Gradle distribution:

```powershell
gradle -p backend :cart-service:build :order-service:build :cart-service:postgresIntegrationTest :order-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain
```

| Fresh TV5 execution / exact source | Unit PASS | PG PASS | FAIL/errors/skip | Exit / build |
|---|---:|---:|---|---|
| Unchanged parent #28 `bd5ff09e89a50fea198fac38967d1565215fe2fe` | 61 (Cart6+Order55) | 11 (Cart1+Order10) | 0/0/0 | 0 / 41s / 16 tasks |
| Supplementary tests `44f283897133ac9e462eaa2cfc1dbdbd4939f02a` | 72 (Cart6+Order66) | 13 (Cart1+Order12) | 0/0/0 | 0 / 40s / 16 tasks |

Second execution includes first execution's tests, **not an additional release total**.
These are TV5 executions, separate from TV2's 61+11 result at the same parent, previous
TV5 61-unit-only audit, earlier 56+11 snapshot, Inventory tests and report V2 figures.
Supplementary branch changes only TV5 Finance tests/docs/evidence; no production source delta.
Source hashes in tested-sources.json identify exact Java/resources from the second run.

### Test additions (11 unit + 2 PG)

- `FinancePolicyConfigurationTest`: 4 methods load real application.yml through Spring ConfigData
  and environment. Explicit inputs alone leave policy closed; fixture approval with missing/malformed
  inputs fails startup; fully specified fixture tests rate/scale/House mapping. No production flag change.
- `FinanceConsumerParityTest`: 7 invocations, all scale0..4 HALF_UP boundary vectors checked against
  independent expected fees; exact subtotal rejection; House exemption; per-line vs per-Store vs parent
  rounding discrepancy. These tests characterize existing behavior, not new approved currency policy.
- `FinanceSnapshotPostgresIT`: 2 real PG tests. Committed two-Store gross/fee/line amounts retain exact
  scale2 values; actual V1 column scale and casts demonstrate higher-scale loss. Derived net is explicitly
  SQL arithmetic, **not proof of seller_net snapshot or production Store isolation**.

## JWT reviewer verification and replay/reconciliation

At current parent `bd5ff09`, `application.yml` maps issuer/audience/current+previous kid/key from JWT_*
environment. `ResourceSecurityConfig` builds ContractJwtDecoder only, no JWKS fallback. Existing
JwtEnvironmentConfigurationTest (2) loads real YAML via ConfigData + SystemEnvironmentPropertySource,
verifies HS256 token, rejects wrong audience, and requires missing-key context failure. Existing
ContractJwtDecoderTest (3) covers key/claim/algorithm rejection. Both suites actually ran in both runs.

The [TV5 JWT thread](https://github.com/duyxl56785/Project_HK5/pull/28#discussion_r4291061468)
is eligible for resolution of this specific finding after this current-head check; it does not approve PR28.
Thread action and published review links are recorded on GitHub/Issue5 after delivery, not fabricated here.
No other reviewer's thread is changed. Old reviews remain in history.

Existing current-head PG Replay4 and Scanner4 tests reran: successful/customer-scoped replay,
changed-payload rejection, foreign-customer denial, lost-commit response, concurrent conditional state
transitions and rollback behavior. Cart1 + Order2 PG provide other persistence/key uniqueness checks.
Catalog/Inventory calls in these scenarios are mocks, not HTTP integration/recovery-worker acceptance.

## Findings, policy and implementation gate

See [versioned six-contract matrix and concrete TV5-FIN-v0.1 candidate](../docs/TV5_FINANCE_CONTRACT_SIGNOFF.md).
No entire contract is APPROVED: **S01 PENDING; C01 REVISE; E01 REVISE; M01 PENDING;
Inventory PENDING/C06 REVISE; concrete service-pair Security PENDING**. OD-07/OD-09 baseline is settled.

FIN-01: NUMERIC(19,2) Cart/Order versus allowed helper scale0..4/high-precision Catalog can lose precision.
FIN-02: currency/rate/House/policyVersion/net snapshots missing from current SellerOrder persistence.
FIN-03: no production COD+settlement/atomic unique settlement in this scaffold.
FIN-04: no authoritative Store-scoped Finance API or live two-Store flow.
FIN-05: final rounding stage remains unsigned. All require the named owners and signatures, not a
unilateral Checkout/Inventory/schema change. Green characterization tests do not mark these findings fixed.

Runtime COD PAID/DELIVERED source, concurrent unique settlement, Finance Store query, Catalog batch
price/currency/auth contract and live UAT two Stores: **NOT_RUN / BLOCKED**. No Catalog or Inventory
adapter enabled; `HK5_FINANCE_POLICY_APPROVED=false` unchanged. No CI PASS inferred from local output.
No API/persistence/frontend/E2E feature implementation added for weeks2–3.

## Artifact integrity and data preservation

[Public evidence directory](evidence/finance-signoff-20261018/README.md) includes original stdout/stderr
build logs, original JUnit XML, exit files, source mapping, Docker inventory and manifest. **46 artifact
SHA256 checks** verified locally; committed blob bytes must also match before claiming delivery complete.
Raw bytes preserved with scoped -text attributes. Generated XML/log CRLF may cause unscoped whitespace
warnings; code/docs checked separately. No raw log was edited to suppress a failure or remove whitespace.
Secret-pattern inspection found no generated credentials/Bearer/private-key/token output in this bundle;
the existing literal key is a public synthetic fixture, never a deploy credential.

3 pre-existing containers and2 named volumes remained present after both executions; no delete/prune/
down-v was run. Testcontainers uses newly isolated databases. Existing `.vs/` and primary checkout left alone.

## Completed and awaiting

- [x] Read latest #28/#33/#37/#38/#39 reviews/proposals and Issue5/12; pin exact heads.
- [x] Rerun parent unit+PG and inspect real YAML/HS256 fix; preserve raw evidence.
- [x] Add and execute Finance QA/config/PG tests without changing shared runtime.
- [x] Record six contracts, concrete M01 candidate, finding owners, QA matrix and UAT limitations.
- [ ] All responsible owner/consumer/business signatures for a single version.
- [ ] Positive live service-pair, authoritative Product/Store/SKU/COD, settlement and two-Store E2E.
- [ ] Full acceptance of Issue5/12 or PR28. **Not DONE; no Approve/Merge/Close in this task.**
