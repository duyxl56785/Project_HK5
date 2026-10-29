# Durable authority reservation components

Refs #2 #8 #9. Stacked on #128 `e378fcf`; includes unchanged #129 deny-write
guard via provenance-preserving cherry-pick. No runtime beans/routes or flags enabled.

## Safety contract

Admission takes an exclusive PostgreSQL transaction advisory guard; authority
mutations take the matching shared guard, preserving concurrent local mutations.
The clock row allocates epochs. Mutation isolation other than READ COMMITTED
fails closed (40001); stale repeatable-read snapshots cannot authorize changes.
Auth reads ACTIVE + current grant and verifies the original signed JWT; Seller
reads current APPROVED/OPEN, exact owner mapping and expected Store/Seller versions.
Every authority-table INSERT/UPDATE/DELETE/TRUNCATE participates through statement
triggers. An ACTIVE reservation prevents any such mutation from committing (55P03).
Reservations have no TTL. Aging, HTTP timeout, process restart and expired delivery
leases do not release them. Epochs are monotonically allocated and bound to request
ID + hash of account/token/Store/operation/versions/command. A changed retry conflicts.

Product operation states: PENDING → ADMITTED → COMMITTED, or PENDING/ADMITTED →
ABORTED. Both participant grants must be recorded before commit. A local SQL write
and its COMMITTED receipt share one transaction. Abort/recovery locks the same row;
it waits for an in-flight write and cannot turn COMMITTED into ABORTED. Late grant
ACKs may attach to ABORTED solely for cleanup; they cannot readmit the operation.
Release checks terminal request/binding/participant epoch before changing ACTIVE
to RELEASED. Terminal-source calls happen before the local release transaction.

New migrations only: Auth V4, Seller V5, Product V6 (V2–V5 are not rewritten).
V6 reserves a later version because this stack's Product branch does not yet contain
main Product/image persistence. Before integration/deployment, reconcile the full
V2–V6 upgrade sequence with pending image migrations; applying V6 first does not
make a later lower-version migration safe. No out-of-order production upgrade is approved.
Role Sync and Store writes take the shared guard before existing row locks to avoid
an inverted lock order. Guards are global per service: coarse reservation barriers and
temporary refusal of unrelated authority writes are deliberate availability costs.
TRUNCATE obtains a table lock before its trigger; its trigger therefore tries the
shared advisory guard without waiting and refuses (55P03) if admission holds it,
avoiding the advisory/table lock inversion.

## Runtime integration is BLOCKED

These are unregistered production ledger/authority components, **not an accepted
cross-service runtime protocol**. `AuthorityPort` and `TerminalSource` are trusted
host ports, not authenticated wire DTOs. No production implementation of the
reservation RPCs, authenticated terminal-receipt callback, recovery scheduler or
deployment ACL is registered. A caller-supplied Receipt/Grant is not authority.
Do not wire raw request JSON or client headers into those ports. Never allow a
release on elapsed time, an unverified receipt, a failed callback or unknown outcome.

The Product SQL callback is not Product lifecycle/expected-version integration.
Seller business writes need a distinct authority-mutation orchestration; an ordinary
Seller reservation intentionally blocks their own status/Store updates too. No
exclusive mutation permit or durable pending-revocation queue is implemented here.
Mutations currently fail/retry while reservations exist; liveness/fairness and
operator recovery procedures still require acceptance. Runtime DB roles/owners able
to edit ledgers or disable triggers are trusted service infrastructure, outside this proof.

Keep `ProductCurrentAuthority` write denial and all business HTTP defaults unchanged.
Do not declare strict cross-service write safety accepted until those ports, every
mutation orchestration and authenticated recovery are independently reviewed/tested.

## Verification scope

PostgreSQL tests execute actual Auth/Seller migrations and Product V1/V6, actual
JWT verification and current authority checks, revocation/status/ownership barriers,
READ COMMITTED and REPEATABLE READ races, commit-vs-abort locking, lost ACK recovery,
stale epochs, payload replay, rollback and age-without-release. The mutation is a
test-only SQL sink, not a production Product CRUD acceptance test.

HTTP timeout/retry tests use a loopback **test fixture adapter** around the actual
Auth reservation component and Product receipt ledger. They prove transport-fault
ordering and no idle DB transaction during the delayed response; they do not prove
production RPC authentication, strict JSON, proxy deployment or end-to-end security.
Existing embedded Auth/Seller security regression remains separate. Full live
reservation HTTP/security acceptance is NOT_RUN/BLOCKED, not inferred from mocks/CI.

## Current verification — 30/10/2026

```text
backend/gradlew.bat -p backend :security-core:build :auth-service:build :seller-service:build :product-service:build --rerun-tasks --no-daemon --console=plain
```

Java21/Gradle8.10/actual PostgreSQL16.6 Testcontainers. Exit0, 1m52s, 36 executed
tasks. JUnit XML: core47; Auth158+2optionalSKIP; Seller297unit+163PG+22HTTPPG;
Product306unit+38PG+18authorityHTTPPG (12 new reservation tests). Total **1049PASS,
0FAIL, 2SKIPPED** from this source tree, not totals added from independent branches.
Final raw log SHA256 `ac78c57aa1eb94022d2770a3c072afe8ef1d7a65758449a076d82de6f43f8ef9`.

Intermediate failures retained: initial test-fixture nonempty-schema baseline issue,
test compilation adapter error, old migration-version assertions, and a real overly
exclusive mutation guard that broke existing Store concurrency tests. The guard was
corrected to shared/exclusive admission ordering; original concurrency assertions
were retained. The new TRUNCATE contention test verifies immediate refusal instead
of deadlock. Fresh source reviewer found no additional scoped finding; independent
reviewer runtime tests NOT_RUN. These results are not a production security gate.
