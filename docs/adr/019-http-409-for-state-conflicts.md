# ADR-019: HTTP 409 Conflict for Every State Conflict

## Status
Accepted

## Context
RFC 9110 defines two adjacent status codes precisely:

- **409 Conflict** — the request is valid but cannot be applied because of the *current state* of
  the target resource. The client may succeed later if the state changes.
- **422 Unprocessable Content** — the request is well formed but its *content* is semantically
  invalid on its own, whatever the state of the resource.
- **400 Bad Request** — malformed syntax, a missing header or an unparsable identifier.

Every WorkOrder transition (`triage`, `schedule`, `control/start`, `control/complete-repair`, ...) is
a named action with exactly one (or, for `control/cancel`, two) legal source status. Calling one from
the wrong status is decided entirely by the aggregate's current state, not by anything wrong with the
request body — so it is 409, matching the platform-wide contract already adopted by the four sibling
Service Domains (Asset Registry's own ADR-019).

## Decision
1. Platform contract carried over unchanged: **409 for every state conflict** (illegal transition,
   mutating a terminal CLOSED/CANCELLED WorkOrder), **400** for malformed input. This service never
   needed a 422 case — every transition is decided by state alone.
2. `InvalidWorkOrderStatusException` carries `ERR-WO-00409` and maps to 409 for every named transition
   method's `requireStatus` check and for `requireNotTerminal` (blocking `update`/`part/initiate` on a
   CLOSED or CANCELLED WorkOrder).
3. A `WorkOrderNotFoundException` for a missing id, or for a `REQUESTER`-role caller viewing someone
   else's ticket (ADR-032), is 404, not 409 — a genuinely absent (or hidden) resource is a different
   failure than a valid one refusing a transition.

## Consequences
- Positive: one predictable error contract across the platform; no 422 code path had to be built,
  tested or later retired.
- Negative: none specific to this service.
