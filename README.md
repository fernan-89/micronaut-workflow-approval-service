# micronaut-workflow-approval-service

BIAN-aligned Service Domain **workflow-approval** (Control Record: `ApprovalRequest`), port `8090`.

Scaffolded by `scripts/new-service.ps1`. Add the aggregate, use cases, controller (`/workflow-approval/v1/{id}/{behavior-qualifier}`),
persistence adapter, Postman suite and ADRs, keeping `gradlew check` at 100% line and branch coverage.

## Error catalog

| Code | HTTP | Meaning |
|---|---|---|
| `ERR-WFA-00404` | 404 | ApprovalRequest not found |
| `ERR-WFA-00409` | 409 | State conflict or duplicate (ADR-019) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

## License

Proprietary - all rights reserved. See [LICENSE](LICENSE). This software is not open source.