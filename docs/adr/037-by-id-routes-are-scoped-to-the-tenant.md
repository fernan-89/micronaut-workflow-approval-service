# ADR-037: The By-Id Routes Are Scoped to the Tenant

## Status
Accepted (2026-11-11). Decided with the owner: always on for the requests, callers first; policy update always scoped, policy read behind the switch of ADR-036.

## Context
ADR-036 closed one hole: filing a request against another tenant's policy. The by-id routes had the same hole and a worse one: policy retrieve and update, and request retrieve, decision capture, cancel and audit log looked the object up by id alone. Anyone who knew a UUID could read another tenant's request, decide it, cancel it, or change the approvers of its policy (the Postman suite showed a 204 for a policy update by another tenant).

## Decision
- **Every by-id route of a request** (`{id}/retrieve`, `{id}/decision/capture`, `{id}/control/cancel`, `{id}/audit-log/retrieve`) takes `X-Tenant-Id` (required, 400 if blank) and finds the request only in that tenant: another tenant's request is the same as none (404 `ApprovalRequestNotFoundException`), nothing is read or written. The repository has no unscoped `findById` for requests any more.
- **Policy update** (`policy/{id}/update`) takes `X-Tenant-Id` and finds the policy only in that tenant (404 otherwise), always. No client changes a policy of another tenant.
- **Policy retrieve** (`policy/{id}/retrieve`) takes `X-Tenant-Id` too but only scopes the lookup when `thinklab.approval.policy-tenant-scoped` is true (ADR-036): change management reads the CAB/ECAB policy by id, one policy shared by every tenant, so the read stays by id until that service has per-tenant policies.
- **Rollout:** the three services that call the approval service (change management, procurement, service request) now send `X-Tenant-Id` on decision/capture (and, for service request, cancel); the approval service of before ignored the header there, so they were merged first. The web already sends the tenant on every call.

## Consequences
- Positive: a UUID is no longer enough to read, decide, cancel or audit another tenant's approval, or to change another tenant's approvers.
- Negative: a caller that does not send `X-Tenant-Id` on these routes now gets a 400 (none of ours does not); policy read by id is still open by default.
