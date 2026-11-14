# ADR-036: Filing a Request Against a Policy of Another Tenant Is Refused Only When Asked

## Status
Accepted (2026-11-11). Decided with the owner, who chose the optional switch over an always-on check.

## Context
`POST /initiate` found the policy by its id alone, so a tenant could file a request against another tenant's policy given its id. That mattered little while the policy ids were set by an operator, and more once procurement made the policy id data that a tenant's staff types into an approval band. Making the check always on would stop change management from working: its CAB and ECAB policy ids are one deployment-wide pair of environment variables, used by every tenant, and the policies belong to a single tenant.

## Decision
- `thinklab.approval.policy-tenant-scoped` (`THINKLAB_APPROVAL_POLICY_TENANT_SCOPED`, **false by default**): when true, `initiate` finds the policy only in the tenant that files the request, and another tenant's policy is the same as none (404 `ApprovalPolicyNotFoundException`). When false, nothing changes.
- Turn it on in a deployment whose clients all use per-tenant policies (procurement does, through its approval bands). Change management can only run with it on once its policies are per tenant, which it is not yet.
- The by-id routes are scoped by ADR-037 (requests and policy update always; policy retrieve through this same switch).

## Consequences
- Positive: the isolation is available now without breaking the deployments that share policies; the default leaves every existing deployment as it was.
- Negative: the default is the open behaviour, so an operator has to know to turn it on (this affects `initiate` and the policy read by id).
