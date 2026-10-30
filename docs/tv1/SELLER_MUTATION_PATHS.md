# Seller mutation paths and authority boundary

Refs #133. Internal orchestration only; no new HTTP handler, scheduled worker, default policy,
or production registration. Existing management handlers and disabled write HTTP behavior remain intact.

`SellerMutationQueue` stores immutable typed commands in the `SELLER_MUTATION` queue. Supported
commands are resubmit, approve, reject, suspend, reinstate and Store set-open. The binding includes
actor, target ID, expected version, reason and open state. Reusing an ID with different content
fails rather than replacing the command. Pending/leased/blocked commands retain the existing
priority barrier against new reservations.

## Local transaction protocol

The package-private engine is deliberately unreachable through a production bean/endpoint. Its
PostgreSQL harness uses explicit test-only policies, not authenticated live HTTP. It enters the
existing mutation guard before locking queue/data rows, validates claim ownership and lease, then
reuses Seller/Store persistence and lifecycle services. Seller outbox snapshots and the result
receipt commit in the same transaction as the mutation. A stale expected version rolls back the
whole transaction. A crash after commit but before queue completion replays the stored result
instead of reapplying the lifecycle or appending another outbox event. An expired claim can be
reclaimed; expiry never releases ACTIVE authority reservations. No force-unlock path exists.

## Production blocker: Auth commit proof

`recoverOne` performs fresh `CurrentAccountClient` verification for each attempt outside a DB
transaction, checks actor and operation role, then retains the command with
`AUTH_COMMIT_PROOF_UNAVAILABLE`. The Auth current-account result is a snapshot. Existing Seller to
Auth reservation RPC supports lookup, while the Auth terminal callback is bound to Product;
neither proves Seller commit authority. No caller-supplied lease/guarantee or cached policy is
accepted as a substitute. A real Auth reservation/admission protocol for Seller mutations and an
authenticated Seller terminal receipt/closure callback are required before connecting the local
engine to any production writer. Exhausted retries become BLOCKED and continue preventing new
admissions; explicit retry only requeues and cannot declare success.

## Existing mutation inventory

| Path | Existing guard / disposition |
| --- | --- |
| Seller registration | Existing statement-level mutation guard on insert; no supported queued registration command, because no Seller/Store authority can pre-exist for a new identity. |
| Seller resubmit/approve/reject/suspend/reinstate | Typed queue commands; legacy persistence remains protected by the DB mutation trigger. Production queued execution waits for Auth commit proof. |
| Store create/revise/set-open | Existing service calls mutation guard before its Seller row lock; statement-level Store trigger also guards DML. Set-open has a typed durable command. Create/revise remain existing internal paths; production write HTTP remains disabled. |
| Seller owner reference / Store seller ID transfer | No supported transfer endpoint, lifecycle or queued transfer command. Existing V5 triggers guard direct SQL reassignment while ACTIVE reservations exist; V8 does not introduce a permanent ownership policy. |
| Direct INSERT/UPDATE/DELETE/TRUNCATE | Existing V5 triggers continue refusing mutation while ACTIVE reservations exist. No trigger or reservation is disabled for recovery. |

## Verification scope

`SellerMutationQueuePostgresTest` migrates as the container administrator, then executes queue,
mutation and raw DML checks as the non-superuser `seller_mutation` login. It proves local ACTIVE
blocking, receipt replay after crash before ACK, binding conflict, stale-version rollback, Store
closure and direct-SQL ownership rejection while an ACTIVE reservation exists. These checks are **not** Auth/Seller live E2E or proof
that production queued writes are enabled. Focused execution passed five local PostgreSQL
tests, including privilege denial. The test applies `sql/mutation-seller-grants.sql` to a
dedicated NOLOGIN group/login: mutation code can read reservations but cannot release them,
update the fence clock, or update/delete receipts. Test setup alone uses the container
administrator to reset fixtures. The current full-run/source evidence is in the publication
PR/checkpoint, not a claim of production provisioning.
