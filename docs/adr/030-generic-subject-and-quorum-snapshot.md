# ADR-030: A Generic ApprovalRequest, Not a Per-Client Approval Model, With a Quorum Snapshot

## Status
Accepted

## Context
Approval is a capability several BIAN Service Domains need — starting with `it-change-management`'s
CAB/ECAB sign-off, but foreseeably also procurement or access requests. Building the workflow inside
each client would duplicate the same quorum/veto logic; hardcoding this service to `ChangeRequest`
would recreate the coupling a shared Service Domain exists to remove.

`ApprovalRequest` needs to know how many approvals it requires and who may cast one at the moment it
is filed. Looking that up live from `ApprovalPolicy` on every decision would let a policy edit made
mid-flight (someone added or removed an approver) silently change the rules under an in-progress
request.

## Decision
- `ApprovalRequest` carries `subjectType` (a string, e.g. `"ChangeRequest"`) and `subjectId` (a UUID)
  as an opaque reference. This service never interprets either value — the calling Service Domain's
  own vocabulary is not this service's concern.
- `requiredApprovals` and `eligibleApproverIds` are copied from the referenced `ApprovalPolicy` onto
  the `ApprovalRequest` at creation time (`InitiateApprovalRequestUseCase`), not looked up live on
  each `decision/capture`. A request's rules are fixed the moment it is filed.
- `ApprovalPolicy` itself has no lifecycle state machine — it is tenant configuration (name, quorum,
  approver roster), created and updated like reference data. No `ApprovalRequest` depends on its
  policy continuing to exist after creation, since every field it needs is already snapshotted.
- `requesterId` is explicit on `InitiateApprovalRequestRequest` rather than derived from `X-Executor`,
  because the typical caller is another service (e.g. GMUD) acting on a human's behalf, not the human
  calling directly.

## Consequences
- Positive: one place owns quorum/veto semantics; any number of client Service Domains can reuse it
  without coupling to each other or to this service's internals beyond the opaque subject reference.
- Positive: an in-flight `ApprovalRequest` is immune to a concurrent policy edit — auditable, predictable behavior.
- Negative: editing a policy has no effect on requests already in flight against it; a client that
  needs the new rule must wait for the request to resolve and file a new one.
