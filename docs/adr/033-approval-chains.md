# ADR-033: Approval Chains - Ordered Stages, One Decision Per Person

## Status
Accepted

## Context
A policy was one quorum: N of M approvers. Real sign-off is often a sequence - a team lead, then security, then a director - and every client that needs it (changes, purchases, service requests, access) would otherwise build its own sequencing around a service that only knows one step. The service is already generic about what is approved (ADR-030); it should also own the sequence.

## Decision
- A policy holds an **ordered list of stages** (`ApprovalStage`: its own `requiredApprovals` and `eligibleApproverIds`). A policy with one stage is exactly the old quorum: the request/response shape, the stored fields and every existing client keep working. A stage is limited to the usual rules (positive quorum, at least one approver, quorum not above the approvers); a chain has 1 to 10 stages.
- The request/response accepts either `stages` or the original `requiredApprovals` + `eligibleApproverIds` (one stage), never both (400).
- An `ApprovalRequest` snapshots the whole chain when it is filed (ADR-030): editing the policy later never changes a request in flight. It waits on **one stage at a time** (`currentStage`, shown one-based). Completing a stage that is not the last moves the request to the next stage and leaves it `PENDING`; completing the last one resolves `APPROVED`. `requiredApprovals` and `eligibleApproverIds` in the answer describe the stage it is waiting on now.
- **A single REJECT at any stage resolves the whole request `REJECTED`** (ADR-031 unchanged). There is no stepping back to an earlier stage and no reopening: a refused request is filed again.
- **Segregation of duties:** a person can be an approver of only one stage of a policy (refused when the policy is saved), and an approver can decide only once on a request across the whole chain, so no single person can satisfy a chain.
- Only the approvers of the current stage can decide; an approver of a later stage gets 409 until their stage is reached.
- Stored requests and policies from before chains existed have no stages: they are read as the one-stage chain they always were. Each stored vote carries the stage it was cast on (missing = the first).

## Not decided here (deliberately left out)
- **No delegation** of an approver's decision to someone else while they are away; an administrator cancels and files again.
- **No deadline/escalation per stage**; the request shows how long it has waited, and whoever reads it decides.
- **No notification** of the next approver; the approver inbox (ADR-034) is what a screen reads.

## Consequences
- Positive: one place owns sequencing; existing clients (the change management service) are untouched; the audit trail records each stage change in the decision entry.
- Negative: a stage cannot be skipped or run in parallel with another; a long chain with an absent approver blocks until the request is cancelled and filed again.
