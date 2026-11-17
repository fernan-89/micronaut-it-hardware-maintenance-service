# ADR 035: Work-order writes are guarded writes

## Status
Accepted (2026-11-17). Found by the platform race audit (ACTION-PLAN 0.5, `thinklab-platform/docs/race-audit.md`).

## Context
Every transition read the work order, validated it in the domain and wrote it by `_id` only. Measured by `TransitionRaceIT` on the old code: ten
cancellations of one work order at once all answered OK, and a schedule and a cancel at once both answered OK, so the stored status was whichever
write landed last while the trail held both entries. A work order cancelled after it was told SCHEDULED also leaves the loaner and the asset
(the events this service publishes, ADR-034) out of step with the status.

## Decision
Every `update*` and `add*` of the repository is a **guarded write** on the status the caller read (the `fromStatus` of the audit entry it carries, so no use
case changed): no match is `409 ERR-WO-00409` when the work order exists and `404` when it does not (ADR-019).

## Consequences
- **Positive:** one winner per race; the stored status and the trail always agree.
- **Note:** adding a part or a comment is guarded too: if the status moved in between, the caller gets a 409 and retries (the read model is cheap).
