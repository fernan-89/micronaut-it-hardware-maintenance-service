# micronaut-it-hardware-maintenance-service

BIAN-aligned Service Domain **it-hardware-maintenance** (Control Record: `WorkOrder`), port `8085`.

Scaffolded by `scripts/new-service.ps1`. Add the aggregate, use cases, controller (`/it-hardware-maintenance/v1/{id}/{behavior-qualifier}`),
persistence adapter, Postman suite and ADRs, keeping `gradlew check` at 100% line and branch coverage.

## Error catalog

| Code | HTTP | Meaning |
|---|---|---|
| `ERR-WO-00404` | 404 | WorkOrder not found |
| `ERR-WO-00409` | 409 | State conflict or duplicate (ADR-019) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

## License

Proprietary - all rights reserved. See [LICENSE](LICENSE). This software is not open source.