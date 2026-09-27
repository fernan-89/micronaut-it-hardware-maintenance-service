# ADR-031: Parts as a Sub-Resource, Not a Free-Form String

## Status
Accepted

## Context
The blueprint's hardware-maintenance design (decided 2026-09-26, before this journey started) called
for parts to be modeled as their own sub-resource in v1 rather than a free-form list, specifically so
a later migration to a dedicated `consumable-inventory-service` is a storage swap, not a contract
rewrite. `WorkOrder` also needs to accumulate labor minutes and, later, cost per repair — a bare
string blob can't support either.

## Decision
- `WorkOrder.Part` is a value object (`partId, description, quantity`) held in the aggregate's own
  `parts` list, added via its own Behavior Qualifier (`part/initiate`, `InitiatePartUseCase`) — never
  through `update`.
- `partId` is a sovereign identity from the Hash Token Registry (`InitiatePartUseCase` calls
  `HashServicePort.generateSovereignId("workorder-part-creation")`), matching every other
  individually-addressable id in the platform, not a locally-generated `UUID.randomUUID()`.
- Parts are still embedded inside the single WorkOrder document (`ADR-002`'s "Partial State
  Mutations" rule, same as `Site`'s Building/Room/Rack) — appended via a granular `$push`
  (`WorkOrderMongoRepositoryAdapter.addPart`), never a whole-document rewrite.
- No `part/{id}/update` or `part/{id}/retrieve` route exists in v1: parts are write-once line items on
  a ticket, read back only as part of the WorkOrder itself. A dedicated part lifecycle (quantities
  consumed vs. reserved, stock levels) is exactly the `consumable-inventory-service`'s future job, not
  this service's.

## Consequences
- Positive: the eventual migration to `consumable-inventory-service` only needs to change where a
  `Part` id resolves to (a real catalog item vs. free text), not this service's route contract.
- Negative: no part-level correction today — fixing a typo'd part description requires the same
  discipline as any other immutable ledger-adjacent entry (a comment, not an edit).
