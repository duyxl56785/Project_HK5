# PR #177 — isolated real HTTP proxy regression, 04/11/2026

Refs #153. Application source: `66ff9838ec5b2555167bcb598ff91b8d4bd39517`. Only this evidence/probe directory was added after testing; production code/configuration was unchanged. Java 21, Gradle 8.10, Docker 29.9.1, PostgreSQL 16. This is an Auth proxy rehearsal, **not final marketplace UAT or production security approval**.

## Fresh results

| Scope | Result | Exit |
| --- | --- | --- |
| `:auth-service:test --tests '*LoginThrottleTest' --rerun-tasks --no-build-cache --no-daemon --console=plain` | 7 unit PASS, 0 FAIL/SKIP | 0 |
| Real nginx → Gateway → Auth, legitimate login | 10 × 401, then 429 | 0 |
| Same chain, different forged XFF on every login | 10 × 401, then 429 | 0 |
| Direct Gateway, different forged XFF on every login | 10 × 401, then 429 | 0 |
| nginx registration, different forged XFF per request | 10 × 201 CUSTOMER, then 429 | 0 |
| Unlisted private container → Auth directly, forged XFF per login | 10 × 401, then 429 | 0 |
| Same unlisted peer, registration with forged XFF | 10 × 201 CUSTOMER, then 429 | 0 |

Six HTTP scenarios / 66 asserted HTTP requests, separate from the seven unit cases. Read-only SQL confirmed **20 synthetic registrations persisted** in `auth_db`; Flyway V1–V4 succeeded. No DB fault injection, shared demo mutation, Inventory pair, Finance policy or retention purge was enabled.

## Isolation / reproduction

From a clean checkout of the source above, run the existing runner with an unused project/subnet/ports:

```powershell
$env:HK5_UAT_NET_PREFIX='172.30.252'
.\scripts\qa\uat\Start-IsolatedUat.ps1 -Build -ProjectName hk5-uat-tv2-auth-20261104b -GatewayPort 18083 -FrontendPort 5176
$env:HK5_THROTTLE_ISOLATED_PROJECT='hk5-uat-tv2-auth-20261104b'
node reports/minh-chung-kiem-thu/pr177-proxy-http/probe.mjs host
docker run --name hk5-uat-tv2-auth-20261104b-probe --network hk5-uat-tv2-auth-20261104b_default --read-only --cap-drop ALL --security-opt no-new-privileges --env HK5_THROTTLE_ISOLATED_PROJECT=hk5-uat-tv2-auth-20261104b --mount "type=bind,source=$((Get-Location).Path)/reports/minh-chung-kiem-thu/pr177-proxy-http/probe.mjs,target=/probe.mjs,readonly" node:22-alpine node /probe.mjs private-peer
```

Use a fresh isolated project or wait for the fixed five-minute rate window before repeating registration; do not clear production throttle state. Names/ports above describe this exact rehearsal, not a generic executor or permission to operate on another stack. No secrets are passed to the probe. Logs contain statuses only.

`receipt.json` records the topology and built JAR hashes. Gateway/nginx are explicitly trusted at `.10`/`.11`; all dynamic peers use `.128/25`, excluding both proxies. The separate unprivileged Node container uses the same private network, accesses `auth-service:8101` directly and receives no trusted-proxy identity. Only Gateway/frontend publish loopback ports. Existing per-project secret/volume isolation is unchanged. The test asserts 429 despite changing the claimed address each time.

## Initial failure retained

The first host run failed on its first request with HTTP 500 / exit 1. Gateway logged `Connection refused: auth-service/172.30.252.137:8101` at 14:48:03 UTC; Auth only began listening at 14:48:10 UTC. Compose `--wait` had considered services without healthchecks running before their HTTP ports were ready. The probe now waits for Auth's exact `403 AUTH_CSRF_REJECTED` through each route, without consuming throttle attempts; after readiness, every status/code/Retry-After assertion remains strict. `pr177-host-startup-failed.log` is preserved, not relabeled PASS. No application fix was necessary for that startup timing.

Raw logs/XML retain their original bytes. Verify `SHA256SUMS` against committed blobs or a checkout with the included attributes. The manifest covers evidence/probe/receipt, not itself. Runtime `.env`/secrets stay outside Git; an exact-value scan against the generated private credentials passed.

Still required: independent security re-review of the outstanding CHANGES_REQUESTED finding. No thread was resolved/dismissed and no self-approval or merge was performed. Multi-instance throttling and production ingress hardening remain outside this single-instance rehearsal.
