# ADR-035: An Approver Can Return a Request for Changes

## Status
Accepted

## Context
Approve and reject are not enough. An approver often finds a request that is not wrong, only incomplete ("say which laptop model and why"). Rejecting it forces the requester to start over without knowing what to fix; leaving it pending blocks the queue. The subject (a service request today, a change later) needs to hand the work back to its requester with a reason.

## Decision
- A third decision outcome, `RETURN`, next to `APPROVE` and `REJECT`. It **needs a comment** saying what to fix (blank is 400 `ERR-VALIDATION-00400`; nothing is recorded).
- Like a `REJECT`, a single `RETURN` from any eligible approver of the current stage **resolves the request at once**, whatever was approved before it: the status becomes `RETURNED`, a **terminal** state (it leaves every inbox, cannot be decided or cancelled afterwards - 409 `ERR-WFA-00409`). The decision and its comment are kept on the request and in the audit trail.
- The workflow does not reopen it. A returned subject is edited by its owner and **resubmitted as a new approval request** (a fresh chain from stage one), so each round of approval is its own immutable record; the owning service links them.
- Backward compatible: the new enum values are additive. A caller that only knows `APPROVE`/`REJECT` and the `PENDING/APPROVED/REJECTED/CANCELLED` statuses keeps working until it receives a `RETURNED` it never asked for; the first client to use `RETURN` is service-request.

## Consequences
- Positive: one place decides what "returned" means for every subject; the trail says who returned it and why; no approval state is ever mutated back to pending.
- Negative: a returned request needs a new approval request to continue (the earlier stages' approvals do not carry over, which is deliberate: the content changed). A client that calls `RETURN` must handle the `RETURNED` status; change-management does not use it.
