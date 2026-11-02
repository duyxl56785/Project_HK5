# TV5 Product owner-resource visibility diagnostic

Refs #5 #12 #97. Independent PostgreSQL acceptance, not a live HTTP test or production enablement.
Decision: [TV1 security-owner receipt6430723502](https://github.com/duyxl56785/Project_HK5/pull/97#issuecomment-6430723502),
27/10/2026. TV2 has CONFIRMED this narrow decision and pushed owner fix97/d376176.
The TV5 branch incorporates that source without editing production. The original eight-test Java file
is byte-identical to diagnostic51be73e. Exact run receipts on PR104 determine acceptance; source publication
alone is not verification. This does not approve other contracts, production HTTP or global404remapping.

After current actor/eligible Store scope has been independently verified, future owner-targeted
GET/edit/submit/deactivate must conceal missing and foreign Product IDs with the same generic404.
Historical source97/aa790f4 mapped foreign to403 and missing to404, producing four assertion failures
and four controls PASS. Preserve those original RED logs/XML/hashes. The new owner source verifies
management scope before lookup and conceals foreign IDs; rerun all eight unchanged tests. Rollback/no-write
assertions run before the intended parity assertion. Positive controls preserve same-Store access,
409version/state,403self-moderation and403/503independent authority refusal; a global403→404patch fails them.

The QA source reuses #97's existing test-only FixturePolicy and main application/JPA/Flyway code;
it does not add a production policy or modify owner source. Same Java package is necessary to reuse
that existing package-private TEST fixture. The init task includes test output only for that fixture.

```powershell
$env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC -Dapi.version=1.44'
$qa=(Resolve-Path tests/security/product-visibility).Path
$init=(Resolve-Path tests/security/product-visibility.init.gradle).Path
./backend/gradlew.bat -p backend --init-script $init "-Ptv5VisibilitySources=$qa" :product-service:tv5ProductVisibility --rerun-tasks --no-daemon --console=plain
```

Compare the existing private Failure record (status/code/message), not an invented public HTTP
envelope. Protected401, real signedJWT/current Auth/Store verification, network response headers/body
and actual ControllerAdvice remain BLOCKED and require future HTTP regressions. The actor/Store
fixture is not proof of those requirements. No endpoint, default authority, migration, business
approval, flags or compensation logic is introduced. No generated log/XML or credentials are committed.
Keep this task separate from normal GREEN tests; do not disable cases, weaken assertions, or count
controls as overall fix PASS. Preserve original RED bytes/hashes, rerun unchanged cases on exact
owner fix SHA, then require all eight PASS before calling this diagnostic GREEN.
