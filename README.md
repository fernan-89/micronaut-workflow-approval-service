# Thinklab Workflow Approval Service

**Version:** v1.0.0-BIAN

**Status:** Reference implementation (ThinkLab portfolio project)

## Overview

The Thinklab Workflow Approval Service is the platform's shared CAB/ECAB-style approval capability
(BIAN `workflow-approval`, ADR-013). Any Service Domain that needs a quorum sign-off — starting with
`it-change-management`'s change requests — files an `ApprovalRequest` against a named `ApprovalPolicy`
(e.g. `"CAB"`) and reads back the resolved outcome.

The service knows nothing about what it is approving: `subjectType`/`subjectId` are an opaque
reference the calling Service Domain interprets on its own side (ADR-030). A policy's quorum and
approver roster are snapshotted onto the request the moment it is filed, so an in-flight request never
shifts under a later policy edit. A single `REJECT` decision resolves the request immediately
(fail-fast veto, ADR-031); reaching the required number of `APPROVE` decisions resolves it to
`APPROVED`. Client integration is synchronous HTTP, not an event — the caller reads the post-decision
status in the same response (ADR-032).

Built with Java 21 and Micronaut 4.4.2 on a strict Hexagonal Architecture and a fully reactive stack
(Project Reactor, reactive MongoDB driver).

## Technology Stack

* **Runtime:** Java 21 LTS
* **Framework:** Micronaut 4.4.2 (AOT optimized, reflection-free DI and Serde)
* **Reactive Engine:** Project Reactor (Mono / Flux)
* **Persistence:** Reactive MongoDB (`thinklab_workflow_approval_db`, collections `approval_policies` and `approval_requests`), BSON UUID standard representation, compound `(organisationId, name)` and `(organisationId, subjectType, subjectId)` indexes
* **Events:** none — client integration is synchronous HTTP (ADR-032)
* **Observability:** W3C Trace Context, SLF4J/Logback, Reactor MDC bridge
* **Containerization:** Google Distroless (nonroot), read-only root filesystem
* **Testing:** JUnit 5, Mockito, Reactor Test (domain FSM, use cases, controller, adapter, mapper, index initializer)
* **Documentation:** OpenAPI 3.0 / Swagger generated at compile time

## Domain Model

```text
ApprovalPolicy {
  id, organisationId, name,
  stages[ { requiredApprovals, eligibleApproverIds[] } ],   // an ordered chain, 1..10 stages (ADR-033)
  requiredApprovals, eligibleApproverIds[],                 // = the FIRST stage (the whole quorum of a one-stage policy)
  createdAt, updatedAt
}

ApprovalRequest {
  id, organisationId, subjectType, subjectId, requesterId, policyId,
  stages[ ... ], currentStage,                // the chain, snapshotted from the policy at creation (ADR-030); currentStage is 1-based
  requiredApprovals, eligibleApproverIds[],   // = the stage it is waiting on NOW
  status, decisions[ { approverId, outcome, comment?, decidedAt, stage } ],
  createdAt, updatedAt,
  auditTrail[ { occurredAt, action, executor, fromStatus?, toStatus, detail } ]
}
status: PENDING | APPROVED | REJECTED | RETURNED | CANCELLED
```

### Resolution rule (ADR-031)

```text
PENDING --decision/capture (APPROVE, stage not last, reaches its quorum)--> PENDING, next stage (ADR-033)
PENDING --decision/capture (APPROVE, last stage reaches quorum)--> APPROVED (terminal)
PENDING --decision/capture (REJECT, any single one)---> REJECTED (terminal)
PENDING --decision/capture (RETURN, any single one, comment required)--> RETURNED (terminal, ADR-035)
PENDING --control/cancel------------------------------> CANCELLED (terminal)
```

`ApprovalPolicy` has no lifecycle — it is tenant configuration, created and updated like reference
data.

### Chains (ADR-033, ADR-034)

A policy is created with either `stages` (an ordered chain; a person can belong to only one stage) or the original `requiredApprovals` + `eligibleApproverIds` (one stage) - never both. A request walks the stages one at a time; one REJECT at any stage ends it; an approver decides once across the chain. `GET /retrieve?pendingFor={approverId}` is the approver inbox (what they can decide now, oldest first). A decision is a guarded write: a vote that lost a race with another answers 409 and is retried.

## BIAN Behavior Qualifier Contract (`/workflow-approval/v1`)

`X-Tenant-Id` is mandatory on `policy/initiate`, `policy/retrieve` (collection), `initiate` and
`retrieve` (collection); `X-Executor` is mandatory on every mutation. There is no `DELETE`.

| Behavior Qualifier | Method & Path |
|---|---|
| policy/initiate | `POST /workflow-approval/v1/policy/initiate` |
| policy/retrieve (single) | `GET /workflow-approval/v1/policy/{id}/retrieve` |
| policy/retrieve (collection, filter `name`) | `GET /workflow-approval/v1/policy/retrieve` |
| policy/update | `PUT /workflow-approval/v1/policy/{id}/update` |
| initiate | `POST /workflow-approval/v1/initiate` |
| retrieve (single) | `GET /workflow-approval/v1/{id}/retrieve` |
| retrieve (collection, filter `subjectType`/`subjectId`/`status`) | `GET /workflow-approval/v1/retrieve` |
| decision/capture | `PUT /workflow-approval/v1/{id}/decision/capture` |
| control/cancel | `PUT /workflow-approval/v1/{id}/control/cancel` |
| audit-log/retrieve | `GET /workflow-approval/v1/{id}/audit-log/retrieve` |

### Error catalog (RFC 7807, `error_code` field)

| error_code | HTTP | Meaning |
|---|---|---|
| `ERR-WFA-00404` | 404 | ApprovalPolicy or ApprovalRequest not found |
| `ERR-WFA-00409` | 409 | Illegal/terminal-state transition, ineligible approver (or one whose stage is not reached yet), an approver deciding twice, or a vote that lost a race with another (ADR-019/ADR-031/ADR-033/ADR-034) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

Example:

```bash
curl -X POST http://localhost:8090/workflow-approval/v1/policy/initiate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 6f1c7a52-3d0b-4a44-9c3e-0a7d1f6e2b10" \
  -d '{"name":"CAB","requiredApprovals":2,"eligibleApproverIds":["...","...","..."]}'

curl -X PUT http://localhost:8090/workflow-approval/v1/{id}/decision/capture \
  -H "Content-Type: application/json" \
  -H "X-Executor: 6f1c7a52-3d0b-4a44-9c3e-0a7d1f6e2b10" \
  -d '{"outcome":"APPROVE","comment":"Looks fine"}'
```

## Operational Procedures

```bash
# Build, run AOT optimizations and test
./gradlew clean build

# Start the service (default port 8090)
./gradlew run

# Container image
docker build -t thinklab-workflow-approval-service:latest .
```

* **Health:** `http://localhost:8090/health`
* **Swagger UI:** `http://localhost:8090/swagger-ui`
* **Postman suite:** `docs/postman/` (policy CRUD + quorum/veto scenarios + negative/409 scenarios)

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `MICRONAUT_SERVER_PORT` | `8090` | HTTP port |
| `MONGODB_URI` | `mongodb://localhost:27017/thinklab_workflow_approval_db` | MongoDB connection |
| `HASH_SERVICE_URL` | `http://localhost:8080` | Hash Token Registry base URL |

## Architecture Decision Records

`docs/adr/`: 001 hexagonal reactive stack · 005 UUID identity sovereignty · 013 BIAN service domain
conventions · 019 HTTP 409 for state conflicts · 030 generic subject and quorum snapshot · 031
fail-fast veto quorum resolution · 032 synchronous HTTP integration, not events · 033 approval chains (ordered stages, one decision per person) · 034 guarded decision write and the approver inbox · 035 return for changes.

### Automated Tests

```bash
./gradlew test                          # unit suite + 100% line/branch coverage gate (no Docker needed)
./gradlew integrationTest               # Testcontainers suite against a real MongoDB (needs Docker)
./gradlew check                         # both, as CI runs it
```

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
