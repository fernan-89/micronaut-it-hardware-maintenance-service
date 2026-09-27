# ADR-034: Asset Maintenance Status via Events, Best-Effort Outbox

## Status
Accepted

## Context
The blueprint calls for the affected Asset to move into `MAINTENANCE` when repair actually begins and
back to service once verified fixed, "evento, não chamada síncrona" (event, not a synchronous call) —
this service must never call `it-asset-registry` directly, and must never block a WorkOrder mutation
on that service being reachable.

Two moments in the FSM (ADR-030) genuinely change the Asset's own state:
- `control/start` (`SCHEDULED -> IN_REPAIR`) is when work genuinely begins on the physical asset.
- `control/pass` (`QUALITY_CHECK -> RESOLVED`) is when the repair is verified successful — chosen
  deliberately over waiting for the separate, purely administrative `control/close` step, so the asset
  returns to service as soon as it is actually fixed rather than when the paperwork is closed.

Per Asset's own FSM (`AssetStatus`), only `DEPLOYED` transitions into `MAINTENANCE`, and `MAINTENANCE`
only exits to `DEPLOYED`, `READY` or `DECOMMISSIONED` — so "the asset's previous state" is always
unambiguously `DEPLOYED` for this integration; no state needs to be remembered or round-tripped in the
event payload.

## Decision
- `WorkOrderEventPublisher` appends an `OutboxEvent` (kit ADR-003) on `control/start`
  (`thinklab.it-hardware-maintenance.workorder.repair-started`) and on `control/pass`
  (`...repair-completed`), payload `WorkOrderRepairEvent(workOrderId, organisationId, assetId,
  occurredAt)`.
- **Best-effort, not transactional**: unlike party-authentication's `UserCreationWriter` (Micronaut
  Data `@Transactional` joining the same MongoDB session, kit ADR-003's 0.4.2 addendum), this service
  persists `WorkOrder` through the raw reactive-streams Mongo driver (matching Asset/Site), not
  Micronaut Data. A true multi-document transaction there would need manual `ClientSession`
  orchestration this journey's scope does not justify. The status write always happens first; a
  failed outbox append is logged and swallowed (`WorkOrderEventPublisher`), never failing the
  `control/start`/`control/pass` request itself.
- On the consuming side, `it-asset-registry` (previously never an event consumer) gets a durable
  JetStream pull subscriber mirroring `notification-dispatch`'s `JetStreamNotificationSubscriber`
  pattern exactly, and reacts by calling its own existing `ControlAssetUseCase` with
  `Action.MAINTENANCE` (on `repair-started`) or `Action.DEPLOY` (on `repair-completed`) — no new
  Asset domain logic needed, since both actions and their FSM already existed.
- The consumer is idempotent (a `ProcessedEventRepository`, same shape as notification-dispatch's) and
  fail-open on a state mismatch: if the Asset is not `DEPLOYED` when `repair-started` arrives (or not
  `MAINTENANCE` when `repair-completed` arrives) — for instance a redelivered event, or an operator
  already moved the asset by hand — the resulting `InvalidAssetStatusException` is caught and logged,
  never crashing the consumer or blocking the next message.

## Consequences
- Positive: no synchronous coupling between the two services; either can be down without blocking the
  other; the Asset FSM's own invariants (only `DEPLOYED` enters `MAINTENANCE`) are reused as-is rather
  than re-modeled here.
- Negative: at-least-once delivery plus best-effort (non-transactional) append means a narrow window
  where a WorkOrder's status changes but the event is never appended (process crash between the two
  writes) — the Asset then silently stays out of sync with the WorkOrder until a human notices. No
  reconciliation sweep exists in v1; accepted the same way kit ADR-003 originally accepted best-effort
  outbox for every producer before the transactional upgrade landed for user creation specifically.
