# Offline Finance acceptance evidence audit

Refs #5/#12, tasks6.5/6.6. This QA-only tool is independent of unsigned business choices. It does not implement payment/COD/settlement, a shared API/schema, authority, or a new business policy. Existing Finance rules/FD01–05 are not rewritten.

Run from repository root with Python3.12 standard library only:

```sh
python -m unittest discover -s scripts/qa/tests -p test_finance_evidence_audit.py -v
python scripts/qa/finance_evidence_audit.py --manifest tests/finance/acceptance-manifest.example.json --evidence-root . --output .verification/finance-acceptance-audit.json
```

The supplied template intentionally returns **exit2 / BLOCKED_DEPENDENCY**. There are no live runs or Finance/COD business/authority signatures. Choose a fresh output filename; existing output is never overwritten. Exit1 means input/output invalid or unavailable; exit0 only means references/artifacts are ready for **owner acceptance review**, never DONE/APPROVED. The tool never calls GitHub or services and never changes data, contracts, flags, reviews or PR state.

## Input and checks

This is a **local QA artifact format**, not an owner service wire contract or signature ledger. A run descriptor contains a root-relative `receipt`, exact40-character `head`, declared `level` (UNIT/POSTGRESQL/LOCAL_INTEGRATION/LIVE_E2E) and `usesMocks` boolean. Receipts reuse existing TV5 execution fields: `head`, `startedUtc`, `completedUtc`, `exitCode`, flat/per-task `counts` and `rawArtifacts[{localPath,bytes,sha256}]`. Paths resolve inside the explicit evidence root, including symlink checks; artifacts and XML are size-bounded. All hashes/lengths must match, counts are derived from actual JUnit testcase/failure/error/skipped nodes and must agree with the receipt. No XML/no executed cases gives NOT_RUN; initializationError/Docker is BLOCKED_ENVIRONMENT, not a production assertion finding; failures remain FAIL. Wrong expected SHA gives STALE_EVIDENCE.

There is no cross-run release total (`releaseTotal=null`). Different heads stay separate. Artifact integrity **does not authenticate the producer, prove the declared level ran real PostgreSQL/remote services, validate a deployment/source map, or independently attest business authority**. Those remain source/runtime/owner review responsibilities. For a declared LIVE_E2E descriptor the receipt must additionally declare `executionLevel=LIVE_E2E` and `usesMocks=false`; labels alone still cannot grant production approval. Unit/PG with mocks never fulfills the live gate.

Each gate is PENDING or REFERENCE_PROVIDED plus a safe project GitHub reference. A string APPROVED is rejected as a usable signature. REFERENCE_PROVIDED means **human must verify identity, version/digest, scope, authority and required co-signers**. Do not put the approved Product-price slice84 into the full Finance-policy gate as though it signed MD04–MD10. No automatic APPROVED/DONE is produced even when all references and declared live artifacts are present: WAITING_OWNER_ACCEPTANCE is the strongest possible result.

The auditor publishes only counters/static classifications and exact heads. It does not echo arbitrary commands, logs, XML, failure texts, local credentials or raw receipt metadata. Raw evidence remains private; publish sanitized derived output plus raw SHA256 through normal review.

Mixed-failure precedence: Docker initialization errors yield BLOCKED_ENVIRONMENT only
when every failing case is an environment initialization error. An independent failed
assertion or other test error keeps the run FAIL, even if another suite could not start.
All original failure counts remain visible; no failing case is converted to PASS.

DOCTYPE rejection is enforced by the XML parser before entity expansion, including
UTF-16 little/big endian input. Valid UTF-16 JUnit without a DOCTYPE remains usable;
the byte-size/hash checks still apply to the unchanged original artifact bytes.

## Current preparation / boundaries

Current main has pure Finance eligibility/ownership guards and Order V1/V2 snapshot/outbox/journal storage. It lacks an approved persisted COD fact/confirmation authority, settlement table/unique atomic settlement, Finance Store query/API and full monetary/House/refund/reconciliation policy. FD01–05 and FIN01/06 prove local Order durability/replay/storage behavior with synthetic inputs/remote mocks; they do not prove live payment or settlement.

Task6.5 therefore remains BLOCKED_DEPENDENCY. This tool completes an independent evidence-quality/preparation gap; its synthetic unit tests are **auditor tests**, not COD event/settlement runtime tests. The actual current receipts can be audited privately by pointing `--evidence-root` to the workspace and listing their ignored `.verification/.../summary.json` files; no private raw files enter Git.
