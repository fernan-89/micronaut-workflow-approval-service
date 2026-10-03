# ADR-034: A Decision Is a Guarded Write, and the Approver Has an Inbox

## Status
Accepted

## Context
With one stage, two approvers voting at the same instant was an unlikely corner. With a chain it matters more: a vote can change the stage, and two votes loaded from the same state would each write their own idea of the stage and status over the other's. Separately, an approver needs to see what is waiting for them without knowing every subject.

## Decision
- `addDecision` writes with a **guard**: the update only applies while the request is still `PENDING` and has exactly the number of decisions it had when it was loaded. The vote, the new status, the new current stage, the new stage's quorum and approvers, and the audit entry go in one atomic update. If nothing matches, the use case answers `InvalidApprovalRequestStatusException` (409, `ERR-WFA-00409`: read again and retry) - never a merged or lost vote.
- `GET /workflow-approval/v1/retrieve?pendingFor={approverId}` is the **approver inbox**: the `PENDING` requests of the tenant whose current stage lists that approver and that they have not decided yet, oldest first. The other filters do not apply when `pendingFor` is given. The current stage's approvers live in the original `eligibleApproverIds` field (kept up to date on every stage change), so a multikey index `(organisationId, status, eligibleApproverIds)` serves the query.

## Consequences
- Positive: no lost vote under concurrency; the inbox is one indexed query and needs no new collection.
- Negative: the loser of a race must retry (the web app shows the 409 as it comes back); the inbox is per approver id, so a person with several identities sees several inboxes.
