# ADR 038: Cancelling is a guarded write

## Status
Accepted (2026-11-17). Found by the platform race audit (ACTION-PLAN 0.5, `thinklab-platform/docs/race-audit.md`).

## Context
Recording a decision was already a guarded write (ADR-033): it applies only while the request is `PENDING` and has the decisions it was loaded with.
Cancelling was not: it read the request, validated `cancel` against `PENDING` in memory and wrote the status by `_id` only. Measured by `CancelRaceIT` on
the old code: ten cancellations of one request at once all answered OK and left eleven entries in the trail. The worse variant is a cancel that lands
after the last approval: the caller of the decision was already told `APPROVED` (and the change-management service acted on it), yet the stored
status becomes `CANCELLED`. (Five repetitions of that exact interleaving did not reproduce on the old code, the window is narrow; the test stays as a
regression guard.)

## Decision
`updateStatus` is a **guarded write** on the status the caller read (the `fromStatus` of the audit entry it carries): no match is
`409 ERR-WFA-00409` when the request exists and `404` when it does not. No use case changed.

## Consequences
- **Positive:** a cancel and a final approval have one winner; the stored status and the audit trail always agree with what the callers were told.
- **Not changed, on purpose:** `ApprovalPolicy` edits are a whole-definition replace by `_id`; a policy has no lifecycle, so two simultaneous edits are
  last-write-wins. Requests copy the policy when they are created, so an in-flight request is never affected. Optimistic versioning of policies would be a separate decision.
