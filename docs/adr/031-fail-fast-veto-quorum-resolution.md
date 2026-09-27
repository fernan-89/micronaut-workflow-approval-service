# ADR-031: Fail-Fast Veto — a Single REJECT Resolves the Request Immediately

## Status
Accepted

## Context
`ApprovalRequest.captureDecision` records one approver's vote at a time and must decide, after each
vote, whether the request is still open or has resolved. CAB-style approval boards commonly allow any
single reviewer to block a change outright, rather than requiring every reviewer to reject before the
request is considered blocked.

## Decision
- A single `REJECT` decision resolves the whole `ApprovalRequest` to `REJECTED` immediately, regardless
  of how many `APPROVE` decisions already exist or how many eligible approvers have not yet voted.
- Reaching `requiredApprovals` distinct `APPROVE` decisions, with zero rejects, resolves the request to
  `APPROVED`.
- There is no partial-quorum "still open after a reject" state, and no mechanism to reverse a
  `REJECTED` outcome — filing a corrected request is the only path forward. This is the simplest
  defensible v1 semantics; a future policy-level toggle (e.g. "majority overrides a single veto") is
  deferred until a real client needs it.
- `captureDecision` rejects (via `InvalidApprovalRequestStatusException`, HTTP 409) a decision from an
  approver not in `eligibleApproverIds`, a second decision from an approver who already voted, and any
  decision once the request has left `PENDING`.

## Consequences
- Positive: the resolution rule is a two-line method (`resolve()`) with no configuration surface to
  misuse; predictable for both the approver and the calling Service Domain.
- Positive: `captureDecision` returns the post-decision `ApprovalRequestResponse` directly, so a
  synchronous caller (GMUD) reacts to `REJECTED`/`APPROVED` in the same request/response cycle — no
  polling or event subscription needed for the common case.
- Negative: a single accidental or bad-faith reject blocks the whole request with no override; the
  only recovery is filing a new `ApprovalRequest`.
