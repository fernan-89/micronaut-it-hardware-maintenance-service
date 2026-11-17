# ADR-036: The By-Id Routes Are Scoped to the Tenant

## Status
Accepted (2026-11-17). Part of the tenant audit of the platform (thinklab-platform `docs/TENANT-AUDIT-2026-11.md`); same rule as workflow-approval ADR-037, change management ADR-038 and asset registry ADR-029.

## Context
Every route of a work order that takes the id found it by id alone (a requester seeing another requester's ticket was already a 404, but a caller of another tenant was not). The cross-tenant probe (platform `scripts/e2e/tenant-probe.sh`) showed a work order of tenant X answering 200 to tenant Y, retrieve and audit log alike.

## Decision
- Every by-id route takes `X-Tenant-Id` (required, 400 if blank) and finds the work order only in that tenant: another tenant's work order is the same as none (404 `WorkOrderNotFoundException`), nothing is read or written. The repository has no unscoped `findById` any more.
- The self-service rule of ADR-032 is unchanged and applies after the tenant: a `REQUESTER` sees only their own tickets.
- Callers: the web and the gateway already send the tenant on every call; no other service calls these routes (the asset registry hears of a repair through the events, which carry the organisation).

## Consequences
- Positive: a UUID is no longer enough to read or change another tenant's work order.
- Negative: a client that sends no tenant on a by-id route now gets a 400.
