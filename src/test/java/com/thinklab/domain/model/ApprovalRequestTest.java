package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidApprovalRequestStatusException;
import com.thinklab.domain.model.ApprovalRequest.ApprovalStatus;
import com.thinklab.domain.model.ApprovalRequest.Decision;
import com.thinklab.domain.model.ApprovalRequest.DecisionOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalRequestTest {

    private static final String EXECUTOR = "op-1";

    private UUID id;
    private UUID organisationId;
    private UUID subjectId;
    private UUID requesterId;
    private UUID policyId;
    private UUID approverA;
    private UUID approverB;
    private UUID approverC;
    private List<UUID> eligibleApprovers;
    private ApprovalRequest request;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        policyId = UUID.randomUUID();
        approverA = UUID.randomUUID();
        approverB = UUID.randomUUID();
        approverC = UUID.randomUUID();
        eligibleApprovers = List.of(approverA, approverB, approverC);
        request = ApprovalRequest.createNew(id, organisationId, "ChangeRequest", subjectId, requesterId, policyId, 2, eligibleApprovers, EXECUTOR);
    }

    @Test
    @DisplayName("createNew starts PENDING with an INITIATED audit entry")
    void createNewStartsPending() {
        assertEquals(ApprovalStatus.PENDING, request.getStatus());
        assertEquals(1, request.getAuditTrail().size());
        assertEquals("INITIATED", request.getAuditTrail().get(0).action());
        assertNull(request.getAuditTrail().get(0).fromStatus());
        assertTrue(request.getDecisions().isEmpty());
        assertEquals(request.getCreatedAt(), request.getUpdatedAt());
    }

    @Test
    @DisplayName("createNew rejects missing identity, blank subjectType, an empty approver list, or a blank executor")
    void createNewGuards() {
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.createNew(null, organisationId, "ChangeRequest", subjectId, requesterId, policyId, 2, eligibleApprovers, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.createNew(id, null, "ChangeRequest", subjectId, requesterId, policyId, 2, eligibleApprovers, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.createNew(id, organisationId, "ChangeRequest", null, requesterId, policyId, 2, eligibleApprovers, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.createNew(id, organisationId, "ChangeRequest", subjectId, null, policyId, 2, eligibleApprovers, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.createNew(id, organisationId, "ChangeRequest", subjectId, requesterId, null, 2, eligibleApprovers, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.createNew(id, organisationId, null, subjectId, requesterId, policyId, 2, eligibleApprovers, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.createNew(id, organisationId, "", subjectId, requesterId, policyId, 2, eligibleApprovers, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.createNew(id, organisationId, "ChangeRequest", subjectId, requesterId, policyId, 2, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.createNew(id, organisationId, "ChangeRequest", subjectId, requesterId, policyId, 2, List.of(), EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.createNew(id, organisationId, "ChangeRequest", subjectId, requesterId, policyId, 2, eligibleApprovers, null));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.createNew(id, organisationId, "ChangeRequest", subjectId, requesterId, policyId, 2, eligibleApprovers, " "));
    }

    @Test
    @DisplayName("reconstitute rejects missing mandatory identity")
    void reconstituteGuards() {
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.reconstitute(null, organisationId, "ChangeRequest", subjectId, requesterId, policyId, 2, eligibleApprovers, null, 0, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.reconstitute(id, null, "ChangeRequest", subjectId, requesterId, policyId, 2, eligibleApprovers, null, 0, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.reconstitute(id, organisationId, null, subjectId, requesterId, policyId, 2, eligibleApprovers, null, 0, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.reconstitute(id, organisationId, "ChangeRequest", null, requesterId, policyId, 2, eligibleApprovers, null, 0, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.reconstitute(id, organisationId, "ChangeRequest", subjectId, null, policyId, 2, eligibleApprovers, null, 0, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.reconstitute(id, organisationId, "ChangeRequest", subjectId, requesterId, null, 2, eligibleApprovers, null, 0, null, null, null, null, null));
    }

    @Test
    @DisplayName("reconstitute defaults a missing status to PENDING and null lists to empty")
    void reconstituteDefaults() {
        ApprovalRequest restored = ApprovalRequest.reconstitute(id, organisationId, "ChangeRequest", subjectId, requesterId, policyId, 2, null, null, 0, null, null, null, null, null);

        assertEquals(ApprovalStatus.PENDING, restored.getStatus());
        assertTrue(restored.getEligibleApproverIds().isEmpty());
        assertTrue(restored.getDecisions().isEmpty());
        assertTrue(restored.getAuditTrail().isEmpty());
        assertNotNull(restored.getCreatedAt());
        assertEquals(restored.getCreatedAt(), restored.getUpdatedAt());
    }

    @Test
    @DisplayName("reaching requiredApprovals distinct APPROVE decisions resolves to APPROVED")
    void quorumReachedResolvesApproved() {
        request.captureDecision(approverA, DecisionOutcome.APPROVE, "looks fine", EXECUTOR);
        assertEquals(ApprovalStatus.PENDING, request.getStatus());

        var entry = request.captureDecision(approverB, DecisionOutcome.APPROVE, "agreed", EXECUTOR);

        assertEquals(ApprovalStatus.APPROVED, request.getStatus());
        assertEquals(ApprovalStatus.PENDING, entry.fromStatus());
        assertEquals(ApprovalStatus.APPROVED, entry.toStatus());
        assertEquals(2, request.getDecisions().size());
    }

    @Test
    @DisplayName("a single REJECT immediately resolves to REJECTED, even with earlier approvals")
    void singleRejectResolvesRejected() {
        request.captureDecision(approverA, DecisionOutcome.APPROVE, null, EXECUTOR);
        var entry = request.captureDecision(approverB, DecisionOutcome.REJECT, "not now", EXECUTOR);

        assertEquals(ApprovalStatus.REJECTED, request.getStatus());
        assertEquals(ApprovalStatus.REJECTED, entry.toStatus());
    }

    @Test
    @DisplayName("captureDecision rejects an ineligible approver")
    void ineligibleApproverRejected() {
        UUID stranger = UUID.randomUUID();
        assertThrows(InvalidApprovalRequestStatusException.class, () -> request.captureDecision(stranger, DecisionOutcome.APPROVE, null, EXECUTOR));
    }

    @Test
    @DisplayName("captureDecision rejects a second decision from the same approver")
    void duplicateDecisionRejected() {
        request.captureDecision(approverA, DecisionOutcome.APPROVE, null, EXECUTOR);
        assertThrows(InvalidApprovalRequestStatusException.class, () -> request.captureDecision(approverA, DecisionOutcome.APPROVE, null, EXECUTOR));
    }

    @Test
    @DisplayName("captureDecision is illegal once the request is no longer PENDING")
    void captureDecisionIllegalOnceResolved() {
        request.captureDecision(approverA, DecisionOutcome.APPROVE, null, EXECUTOR);
        request.captureDecision(approverB, DecisionOutcome.APPROVE, null, EXECUTOR);

        assertThrows(InvalidApprovalRequestStatusException.class, () -> request.captureDecision(approverC, DecisionOutcome.APPROVE, null, EXECUTOR));
    }

    @Test
    @DisplayName("captureDecision requires a non-null approverId, outcome and a non-blank executor")
    void captureDecisionGuards() {
        assertThrows(NullPointerException.class, () -> request.captureDecision(null, DecisionOutcome.APPROVE, null, EXECUTOR));
        assertThrows(NullPointerException.class, () -> request.captureDecision(approverA, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> request.captureDecision(approverA, DecisionOutcome.APPROVE, null, null));
        assertThrows(IllegalArgumentException.class, () -> request.captureDecision(approverA, DecisionOutcome.APPROVE, null, " "));
    }

    @Test
    @DisplayName("cancel is legal only while PENDING")
    void cancel() {
        var entry = request.cancel(EXECUTOR);

        assertEquals(ApprovalStatus.CANCELLED, request.getStatus());
        assertEquals("CANCELLED", entry.action());

        assertThrows(IllegalArgumentException.class, () -> request.cancel(null));
    }

    @Test
    @DisplayName("cancel is illegal once resolved")
    void cancelIllegalOnceResolved() {
        request.captureDecision(approverA, DecisionOutcome.REJECT, null, EXECUTOR);
        assertThrows(InvalidApprovalRequestStatusException.class, () -> request.cancel(EXECUTOR));
    }

    @Test
    @DisplayName("Decision rejects a null approverId or outcome, and defaults a missing decidedAt")
    void decisionValueObjectGuards() {
        assertThrows(NullPointerException.class, () -> new Decision(null, DecisionOutcome.APPROVE, null, null));
        assertThrows(NullPointerException.class, () -> new Decision(approverA, null, null, null));
        assertNotNull(new Decision(approverA, DecisionOutcome.APPROVE, null, null).decidedAt());
        assertNotNull(new Decision(approverA, DecisionOutcome.APPROVE, null, Instant.now()).decidedAt());
    }
}
