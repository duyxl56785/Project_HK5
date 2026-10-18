# TV5 — PR audit execution evidence, 18/10/2026

Refs #5. Java21.0.7 / Gradle8.14.3 / Docker29.8.1. Each run uses an exact Git archive of the PR head, without modifying author branches; `run.json` records source, command, exit and process-only JVM options `-Duser.timezone=UTC -Dapi.version=1.44`.

| Snapshot | Command tasks (all with --rerun-tasks --no-daemon --continue --console=plain) | Actual outcome |
|---|---|---|
| #28 bd5ff09 | :cart-service:test :order-service:test |61 unit PASS (Cart6/Order55),0fail/error/skip;exit0/36s |
| #37 9c28338 | :product-service:build :seller-service:build |Product188 unit+25 PG;Seller103 unit+24 PG;0fail/error/skip;exit0/54s,17tasks |
| #38 9b8ae35 | :inventory-service:build :inventory-service:postgresIntegrationTest |17 unit+15 PG PASS,0fail/error/skip;exit0/52s,8tasks |

Do not sum three snapshots into a release result. #37's Product25 PG includes14 production Category cases and11 **test-only candidate SQL** cases, not Store/Product production persistence. #38's lifecycle regression calls a real controller/service/database inside the module, not HTTP Order→Inventory or live E2E. #28 now executes the5 InventoryOperationIntent methods with the real Gradle/JUnit runner; prior standalone/stand-in counts remain historical. Old unchanged Order PG source was inspected; those11 PG cases were not rerun in this audit.

## Credential-safe publication boundary

Three original JUnit XML files from #37 capture ephemeral generated test credentials in console output. They are retained **unchanged locally** under `.verification/audit-20261018/evidence/pr37/`, and are **not committed or uploaded**:

- product-service-integrationTest/TEST-com.projecthk5.productservice.category.CategoryIntegrationTest.xml (14 tests)
- product-service-test/TEST-com.projecthk5.productservice.category.CategorySecurityTest.xml (7 tests)
- product-service-test/TEST-com.projecthk5.productservice.security.ProductServiceSecurityTest.xml (20 tests)

`withheld-junit-summary.json` is explicitly a **derived summary**, with original SHA256, testcase names/results and counters for these41 tests. It does not masquerade as raw XML, does not contain captured console output and does not make the originals publicly available. Other logs/XML are original bytes. Reviewers can inspect the originals locally; public artifact verification alone cannot recompute those3 original hashes.

`manifest.sha256` covers the49 public execution/metadata artifacts (46 copied originals and3 generated summaries/metadata files), excluding this README, attributes and the manifest itself. Code/docs diff-check is separate from generated raw CRLF/trailing-whitespace warnings; raw bytes are not normalized to quiet the warnings. Secret-pattern scanning covers published artifacts; it is not an absolute security certification.

`static-checks.json` records per-PR checks: #19/#28/#37/#38 exit0; #39 exit2 only for deliberate Markdown hard-break spaces at lines3–4. This formatting warning is not a runtime test failure. The substantive #39 finding is the19-vs21 scenario count and reused baseline IDs with different meanings.

`pr-heads.json` records source heads and CI links. #28/#37/#38 each have a readiness success at the inspected SHA; those are CI results, distinct from the fresh local TV5 runs. #19/#39 have no checks/status contexts. All live cross-service/Auth/Store/Finance/2-Store E2E remains NOT_RUN/contract-gated.

Docker before/after inventories show0 missing prior containers/volumes; tests used newly created Testcontainers DBs only. No existing-volume deletion/prune or changes to the shared stack.
