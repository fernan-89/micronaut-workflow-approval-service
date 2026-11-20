# ADR-039: A Policy Is Always Found in the Caller's Tenant

## Status
Accepted (2026-11-20). Supersedes ADR-036, whose switch is removed. Decided with the owner, as the last step of the transition of ADR-039 of it-change-management.

## Context
ADR-036 made the tenant check on policies optional (`thinklab.approval.policy-tenant-scoped`, off by default) because change management used one deployment-wide CAB/ECAB pair, shared by every tenant. Change management now keeps its CAB and ECAB per tenant (its ADR-039) and the stack has run with the switch on since platform PR 58, with every e2e suite green. Every client of `policy/{id}/retrieve` and `POST /initiate` (change management, procurement, service-request) sends `X-Tenant-Id`.

## Decision
1. `POST /initiate` finds the policy in the request's own tenant and `policy/{id}/retrieve` in the caller's tenant. Another tenant's policy is the same as none: 404 `ApprovalPolicyNotFoundException`.
2. `X-Tenant-Id` is mandatory on `policy/{id}/retrieve`, as on every other route. A call without it is a 400 and no longer reads a policy by id alone.
3. The `thinklab.approval.policy-tenant-scoped` switch (`THINKLAB_APPROVAL_POLICY_TENANT_SCOPED`) is removed, and so is the repository method that found a policy by id alone. Setting the variable has no effect.

## Consequences
A deployment that still shares one policy among tenants stops working at the moment it takes this version: it must create a policy per tenant first. The stack, the smokes and the Postman suites already do.
