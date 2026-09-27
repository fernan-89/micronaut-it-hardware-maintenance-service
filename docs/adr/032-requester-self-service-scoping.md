# ADR-032: REQUESTER Self-Service Scoping via Application-Layer Ownership Checks

## Status
Accepted

## Context
The blueprint's decided scope (2026-09-26) requires requester authentication to be mandatory — an
unauthenticated ticket path has no audit trail and is an abuse vector unacceptable for
Enterprise/Global. Two affordances follow: a `REQUESTER` can file their own tickets and can only see
their own tickets, with internal (staff-only) comments hidden from them.

The platform's existing RBAC (`thinklab-service-kit`'s `Role.allows(HttpMethod)`) is deliberately
coarse and method-based only (VIEWER reads, OPERATOR/REQUESTER read and write, ADMIN/SERVICE do
everything) — it has no concept of resource ownership, and extending it to know about "does this
caller own this WorkOrder" would couple a generic platform-wide filter to one service's domain model.

## Decision
- Kit 0.5.1 adds `Role.REQUESTER` with the same method envelope as `OPERATOR` (see the kit's own
  README). It carries no ownership logic — that stays out of the kit by design.
- `SecurityFilter` already derives `X-Role` from the verified token the same way it derives
  `X-Executor` (`principal.subject()`, the caller's own User id) and `X-Tenant-Id`; this service reads
  `X-Role` as an optional header (`@Nullable`), so it behaves identically whether
  `thinklab.security.enabled` is on or off, like every other header-derived value on the platform.
- Every use case that needs scoping receives `role` as a plain `String` and does its own three checks,
  all keyed off comparing `X-Executor` (the caller's own id) against the WorkOrder's `requesterId`:
  1. **Initiate**: a `REQUESTER` caller's `requesterId` is always forced to their own `X-Executor`,
     ignoring anything the request body claims — nobody can file a ticket as someone else.
  2. **Retrieve (single/collection/audit-log)**: a `REQUESTER` viewing a ticket that is not theirs
     gets `WorkOrderNotFoundException` (404) — the same "denied is indistinguishable from unknown"
     posture the gateway already applies to whole routes (ADR-022), applied here to one resource.
  3. **Comments**: a `REQUESTER`'s `internal` flag is always forced to `false` on write, and any
     `internal=true` comment is filtered out of every read they're allowed.
- No new `public/...` route family exists for the self-service path (unlike the blueprint's earlier
  route sketch) — a `REQUESTER` uses the exact same `/it-hardware-maintenance/v1/...` routes an
  `OPERATOR` does, with the scoping above as the only difference. A separate route family would have
  doubled the surface area for no behavior a role check can't already express.

## Consequences
- Positive: the kit stays a thin, reusable, domain-agnostic RBAC layer; ownership logic lives exactly
  once, next to the aggregate it protects, testable with plain unit tests (no security context needed).
- Negative: every new read/write use case must remember to thread `role` through and apply the check —
  there is no framework-level guarantee a future use case won't forget it. Mitigated by keeping the
  pattern uniform and small (a two-line check, copy-pasted deliberately rather than abstracted
  prematurely across four call sites).
