# Thinklab IT Hardware Maintenance Service

**Version:** v1.0.0-BIAN

**Status:** Reference implementation (ThinkLab portfolio project)

## Overview

The Thinklab IT Hardware Maintenance Service is the repair/maintenance ticketing Service Domain
(BIAN `it-hardware-maintenance`, ADR-013): from a reported defect through triage, scheduling, repair
(with a parts wait and an optional vendor RMA detour), quality check and resolution, to a closed or
cancelled ticket. It implements the BIAN Control Record `WorkOrder`, scoped to an Organisation from
the Party Reference Data Directory and to an Asset from the IT Asset Registry.

Requesters authenticate like any other platform user (`REQUESTER` role, party-authentication) and can
file and track their own tickets self-service, scoped to only their own work and never seeing
staff-internal notes (ADR-032) — the same routes an operator uses, not a separate public API.

Built with Java 21 and Micronaut 4.4.2 on a strict Hexagonal Architecture and a fully reactive
stack (Project Reactor, reactive MongoDB driver).

## Technology Stack

* **Runtime:** Java 21 LTS
* **Framework:** Micronaut 4.4.2 (AOT optimized, reflection-free DI and Serde)
* **Reactive Engine:** Project Reactor (Mono / Flux)
* **Persistence:** Reactive MongoDB (`thinklab_hardware_maintenance_db`, collection `work_orders`), BSON UUID standard representation, compound `(organisationId, status, requesterId)` index
* **Events:** kit outbox + NATS JetStream (`thinklab.events.enabled`) — publishes `repair-started`/`repair-completed` so IT Asset Registry can move the affected Asset into/out of `MAINTENANCE` (ADR-034)
* **Observability:** W3C Trace Context, SLF4J/Logback, Reactor MDC bridge
* **Containerization:** Google Distroless (nonroot), read-only root filesystem
* **Testing:** JUnit 5, Mockito, Reactor Test (FSM, use cases, controller, adapter, mapper, index initializer, event publisher)
* **Documentation:** OpenAPI 3.0 / Swagger generated at compile time

## Domain Model

```text
WorkOrder {
  id, organisationId, assetId, requesterId,
  title, symptom, diagnosis?, resolutionCode?,
  type, priority?, status, location?, loanerAssetId?,
  parts[], laborMinutes, rma?, externalReference?, comments[],
  slaResponseDueAt?, slaResolutionDueAt?, createdAt, updatedAt,
  auditTrail[ { occurredAt, action, executor, fromStatus?, toStatus, detail } ]
}
type:     CORRECTIVE | PREVENTIVE | WARRANTY_RMA | UPGRADE | DECOMMISSION_PREP
priority: P1 | P2 | P3 | P4   (response/resolution SLA targets, ADR-033)
```

### Lifecycle (ADR-030)

```text
REQUESTED -> TRIAGED -> SCHEDULED -> IN_REPAIR <-> AWAITING_PARTS
                |                       |
                v                       v
        ESCALATED_TO_VENDOR -> IN_REPAIR   QUALITY_CHECK -> RESOLVED -> CLOSED (terminal)
                                             |  ^                |
                                             v  |                v
                                          IN_REPAIR         TRIAGED (reopen)
REQUESTED, TRIAGED -> CANCELLED (terminal)
```

Each transition is its own named route (`triage`, `schedule`, `control/start`, `control/await-parts`,
`control/resume`, `control/escalate`, `control/vendor-returned`, `control/complete-repair`,
`control/pass`, `control/fail`, `control/close`, `control/reopen`, `control/cancel`) rather than a
generic `control/{status}` endpoint, since several carry their own payload (ADR-030).

## BIAN Behavior Qualifier Contract (`/it-hardware-maintenance/v1`)

`X-Tenant-Id` is mandatory on `initiate` and the collection `retrieve`; `X-Executor` is mandatory on
every mutation and read; `X-Role` is optional and, when `REQUESTER`, scopes every read/write to the
caller's own tickets and hides internal comments (ADR-032). There is no `DELETE`.

| Behavior Qualifier | Method & Path |
|---|---|
| initiate | `POST /it-hardware-maintenance/v1/initiate` |
| retrieve (single) | `GET /it-hardware-maintenance/v1/{id}/retrieve` |
| retrieve (collection, filter `status`) | `GET /it-hardware-maintenance/v1/retrieve` |
| update | `PUT /it-hardware-maintenance/v1/{id}/update` |
| triage | `PUT /it-hardware-maintenance/v1/{id}/triage` |
| schedule | `PUT /it-hardware-maintenance/v1/{id}/schedule` |
| control/start, await-parts, resume, escalate, vendor-returned, complete-repair, pass, fail, close, reopen, cancel | `PUT /it-hardware-maintenance/v1/{id}/control/{action}` |
| part/initiate | `POST /it-hardware-maintenance/v1/{id}/part/initiate` |
| comment/initiate | `POST /it-hardware-maintenance/v1/{id}/comment/initiate` |
| audit-log/retrieve | `GET /it-hardware-maintenance/v1/{id}/audit-log/retrieve` |

### Error catalog (RFC 7807, `error_code` field)

| error_code | HTTP | Meaning |
|---|---|---|
| `ERR-WO-00404` | 404 | WorkOrder not found (or hidden from a REQUESTER who doesn't own it, ADR-032) |
| `ERR-WO-00409` | 409 | Illegal or terminal-state lifecycle transition (ADR-019/ADR-030) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

Example:

```bash
curl -X POST http://localhost:8085/it-hardware-maintenance/v1/initiate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 6f1c7a52-3d0b-4a44-9c3e-0a7d1f6e2b10" \
  -H "X-Executor: admin-user-01" \
  -d '{"assetId":"...","requesterId":"...","title":"Laptop screen flickers","symptom":"Flickers on battery","type":"CORRECTIVE","location":"Room 101"}'
```

## Operational Procedures

```bash
# Build, run AOT optimizations and test
./gradlew clean build

# Start the service (default port 8085)
./gradlew run

# Container image
docker build -t thinklab-it-hardware-maintenance-service:latest .
```

* **Health:** `http://localhost:8085/health`
* **Swagger UI:** `http://localhost:8085/swagger-ui`
* **Postman suite:** `docs/postman/` (lifecycle + parts/comments + negative/409 scenarios)

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `MICRONAUT_SERVER_PORT` | `8085` | HTTP port |
| `MONGODB_URI` | `mongodb://localhost:27017/thinklab_hardware_maintenance_db` | MongoDB connection |
| `HASH_SERVICE_URL` | `http://localhost:8080` | Hash Token Registry base URL |
| `THINKLAB_EVENTS_ENABLED` | `false` | Publish `repair-started`/`repair-completed` to NATS JetStream |
| `THINKLAB_EVENTS_NATS_URL` | `nats://localhost:4222` | NATS connection, when events are enabled |

## Architecture Decision Records

`docs/adr/`: 001 hexagonal reactive stack · 005 UUID identity sovereignty · 013 BIAN service domain
conventions · 019 HTTP 409 for state conflicts · 030 lifecycle state machine (named actions) · 031
parts as a sub-resource · 032 REQUESTER self-service scoping · 033 SLA behind a calendar port · 034
Asset integration via events (best-effort outbox).

### Automated Tests

```bash
./gradlew test                          # unit suite + 100% line/branch coverage gate (no Docker needed)
./gradlew integrationTest               # Testcontainers suite against a real MongoDB (needs Docker)
./gradlew check                         # both, as CI runs it
```

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
