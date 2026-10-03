package com.thinklab.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * One step of an approval chain (ADR-033): how many distinct approvals the step needs and who may cast one. A policy lists its
 * stages in order; a request walks them one at a time. A one-stage chain is exactly the single quorum this service had before.
 *
 * <p>The record itself only copies (so that data stored before chains existed can always be read); {@link #of} is the validating
 * factory every new policy goes through.
 */
public record ApprovalStage(int requiredApprovals, List<UUID> eligibleApproverIds) {

    public ApprovalStage {
        eligibleApproverIds = eligibleApproverIds == null ? List.of() : List.copyOf(eligibleApproverIds);
    }

    /** A valid stage: a positive quorum, at least one approver, and no more approvals required than there are approvers. */
    public static ApprovalStage of(int requiredApprovals, List<UUID> eligibleApproverIds) {
        if (requiredApprovals <= 0) {
            throw new IllegalArgumentException("requiredApprovals must be positive.");
        }
        if (eligibleApproverIds == null || eligibleApproverIds.isEmpty()) {
            throw new IllegalArgumentException("At least one eligible approver is required.");
        }
        if (requiredApprovals > eligibleApproverIds.size()) {
            throw new IllegalArgumentException("requiredApprovals cannot exceed the number of eligible approvers.");
        }
        return new ApprovalStage(requiredApprovals, eligibleApproverIds);
    }
}
