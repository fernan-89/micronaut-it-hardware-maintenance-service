# ADR-033: SLA Response/Resolution Targets Behind a CalendarPort

## Status
Accepted

## Context
The blueprint's decided scope requires an SLA expressed as an absolute duration (e.g. "4 business
hours"), with a fixed-UTC calendar as the only v1 implementation, so a future `calendar-sla` Service
Domain (real business hours, per-tenant time zones, holidays) can be swapped in later without a data
migration. `WorkOrder.Priority` (`P1`..`P4`) already carries a response and a resolution target
duration as domain constants.

## Decision
- `domain/port/CalendarPort` declares one method, `addBusinessDuration(Instant from, Duration
  duration)`. It lives in the application's domain layer, not the kit — this is business logic
  specific to one service, not cross-cutting infrastructure.
- The domain aggregate itself never touches the port: `WorkOrder.triage(...)` takes
  `slaResponseDueAt`/`slaResolutionDueAt` as already-resolved `Instant`s. `TriageWorkOrderUseCase` is
  the one caller of `CalendarPort`, computing both dates from `Instant.now()` and the chosen
  `Priority`'s target durations before handing them to the domain method. This keeps the aggregate
  pure Java and calendar-implementation-agnostic, the same reason `HashServicePort` is called from use
  cases and never from `Asset`/`Site`/`WorkOrder` themselves.
- `FixedUtcCalendarAdapter` is the only v1 implementation: `addBusinessDuration` is a plain
  `from.plus(duration)` — every instant counts as business time, no weekends, no holidays, no
  per-tenant time zone. It is the default `@Singleton` bean; nothing gates it behind a feature flag,
  since there is no second implementation yet to choose between.
- Both SLA due dates are stored as plain `Instant` fields on `WorkOrder` (`slaResponseDueAt`,
  `slaResolutionDueAt`), not recomputed on read. Swapping in a real calendar later changes what
  duration a *future* `triage` call resolves to; it does not need to touch or reinterpret dates
  already persisted.

## Consequences
- Positive: the seam for `calendar-sla` is exactly one bean substitution
  (`@Requires`/`@Replaces` on a new adapter) with zero changes to `WorkOrder`, its repository, or any
  use case besides the constructor injection point.
- Negative: v1's SLA due dates are not calendar-accurate (a ticket triaged at 11pm Friday gets the
  same 4-hour P1 window as one triaged at 9am Tuesday) — an accepted, documented gap until
  `calendar-sla` exists, not a silent one.
