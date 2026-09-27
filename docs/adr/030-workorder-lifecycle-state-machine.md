# ADR-030: WorkOrder Lifecycle — Named Actions, Not a Generic control/{status} Endpoint

## Status
Accepted

## Context
A hardware repair ticket has more states and branches than the platform's other lifecycles: intake,
triage, scheduling, repair (with a parts wait and a vendor-escalation detour), quality check (which
can fail back into repair), resolution (which can be disputed and reopened), and two distinct
terminal states (closed vs. cancelled before work started). Several transitions also carry data of
their own — triage sets the priority and computes SLA targets, escalation records the vendor and case
number, completing repair records the diagnosis and labor time.

The platform's simpler FSMs (`Asset`, `Site`) expose one generic `control/{action}` endpoint backed by
a `changeStatus(newStatus, executor)` method and a `canTransitionTo` adjacency matrix, because every
one of their transitions is a bare status change.

## Decision
- `WorkOrder` has ten states (`REQUESTED, TRIAGED, SCHEDULED, ESCALATED_TO_VENDOR, IN_REPAIR,
  AWAITING_PARTS, QUALITY_CHECK, RESOLVED, CLOSED, CANCELLED`) and thirteen named domain methods
  (`triage`, `schedule`, `escalateToVendor`, `start`, `awaitParts`, `resume`, `vendorReturned`,
  `completeRepair`, `pass`, `fail`, `close`, `reopen`, `cancel`), each validating its own single
  legal source status (or, for `cancel`, one of two) via a small `requireStatus(WorkOrderStatus...)`
  helper instead of a full transition matrix nobody needs, since no two named methods share a target.
- Each method's HTTP route name matches its domain method name directly
  (`control/start`, `control/complete-repair`, ...) rather than a generic `control/{targetStatus}`,
  because several of these routes take a request body the generic shape couldn't express.
- `ControlWorkOrderUseCase.Action` (mirroring `ControlAssetUseCase`) is reserved for the seven
  transitions that really are a bare status change with no extra data or side effect
  (`SCHEDULE, AWAIT_PARTS, RESUME, FAIL, CLOSE, REOPEN, CANCEL`); `start` and `pass` are their own use
  cases instead, because both also publish an outbox event (ADR-034).
- Repair failing quality check (`fail`) returns to `IN_REPAIR`, not to `SCHEDULED` — the technician is
  still the one working it, no rescheduling step makes sense.

## Consequences
- Positive: every route's request/response shape matches exactly what that specific transition needs;
  no dead parameters, no generic endpoint trying to serve incompatible transitions.
- Negative: more use case classes than the generic-dispatch services (13 vs. Asset's 1 `ControlAssetUseCase`)
  — a reasonable size for the aggregate's real complexity, not accidental duplication.
