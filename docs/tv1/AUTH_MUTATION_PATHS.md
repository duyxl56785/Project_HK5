# Auth authority mutation paths — Refs #133

Scope: internal preparation only. No new HTTP endpoint, production bean, feature flag,
JWT payload persistence, or business enablement. TV1 review is required.

| Existing source | Authority writes | Behavior while reservation ACTIVE |
| --- | --- | --- |
| `AccountPersistence.create`, called by `AuthSessionService.register` | Account INSERT and initial role INSERT, plus provisioning task | Existing V4 triggers fail closed; transaction rolls back. Registration is not queued because it contains credential material. |
| `AuthSessionService` login/refresh/logout/me | Reads account/roles; writes refresh-family/token state only | No account status/role mutation to reroute. |
| `SellerRoleSyncService.apply` | SELLER role INSERT/DELETE with source-version, binding and receipt state | Existing shared fence entry and V4 triggers block. Direct service calls remain blocked. |
| `SellerRoleRevocationRecovery.apply/tick` | Same RoleSync transaction via proxy | All statuses, including APPROVED, now use the existing durable ROLE_SYNC queue; pending barrier precedes new reservations. |
| `SellerRoleSyncHttpConfiguration.Receiver` | Delegates every authenticated valid status to recovery | No direct-service fallback; missing recovery returns 503. |
| Repository/entity/manual SQL bypass | `Account` has no status/role setters; repository/JPA/SQL can still write | V4 statement triggers cover INSERT/UPDATE/DELETE and TRUNCATE on accounts and role grants. No bypass exemptions were added. |

There was no existing account status/role administration operation to wrap. The new
`AccountMutationCommands` is an internal, unwired command source. Its enqueue entry accepts
only the signature verifier's private-constructor `VerifiedPrincipal`, requiring signed
ADMIN. An actor ID from a DTO cannot substitute for that principal. Only the verified
identity and verified expiration are retained; no password, JWT, or service credential is stored.

Execution rechecks active current actor status and current local ADMIN grant, locks actor
and target, compares explicit target version/status/roles, checks verified authorization
expiration against the database clock, and writes status/roles plus a durable receipt in
one READ COMMITTED transaction on the same JDBC data source. RoleSync and account commands
share the existing durable mutation lock. Queue acknowledgment follows transaction commit;
receipt replay after a lost acknowledgment does not repeat the mutation. Worker failures
retry with the existing bounded queue policy; malformed/precondition/authorization failures
reject permanently. New admissions remain blocked for PENDING/LEASED/BLOCKED work.

V7 adds AUTH_MUTATION to the local queue constraint, transaction receipts and an independent
account authority version table. Protected SECURITY DEFINER row triggers increment versions
for account/role writes, including direct SQL; this detects ABA without changing the accounts
column layout. Runtime has SELECT only on versions and no receipt UPDATE/DELETE grant.

Limits: migration/application database owners remain trusted and can disable triggers; this
is not a defense against privileged DBA tampering. Current actor authorization requires
exactly ACTIVE and ADMIN; target status vocabulary and administration policy still require
owner approval before wiring any caller. Persisted
commands are verified intents valid only until their original verified expiration, not an
indefinite authorization capability. No authenticated production administration caller exists;
that integration is BLOCKED on an owner-approved source/contract. Existing default-OFF
RoleSync configuration is unchanged.

Validation: focused PostgreSQL test source covers mutation/replay, priority after ACTIVE,
stale/ABA preconditions, current actor revocation, signed-role denial, expiration, rollback,
and queued RoleSync APPROVED. Focused execution on Java 21 / PostgreSQL 16 passed all nine
Auth command cases. HTTP tests also cover every RoleSync status going through recovery and
503 without the adapter. The current full-run result and source revision are recorded in the
publication PR/checkpoint; these checks do not enable administration HTTP or replace review.

Persisted provenance relies on trusted Auth application/queue writers. The payload SHA256
binds content and request identity; it is not a signature and does not defend against a
compromised Auth application database credential. No SQL-only hash is accepted as an RPC
authentication proof.
