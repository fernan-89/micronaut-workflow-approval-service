package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidApprovalRequestStatusException;
import com.thinklab.domain.model.ApprovalRequest.ApprovalStatus;
import com.thinklab.domain.model.ApprovalRequest.Decision;
import com.thinklab.domain.model.ApprovalRequest.DecisionOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Approval chains (ADR-033): stages, the policy that lists them, and a request walking them one at a time. */
class ApprovalChainTest {

    private static final String EXECUTOR = "op-1";

    private final UUID id = UUID.randomUUID();
    private final UUID organisationId = UUID.randomUUID();
    private final UUID subjectId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();
    private final UUID policyId = UUID.randomUUID();
    private final UUID teamLead = UUID.randomUUID();
    private final UUID securityA = UUID.randomUUID();
    private final UUID securityB = UUID.randomUUID();
    private final UUID director = UUID.randomUUID();

    /** Stage 1: one team lead. Stage 2: two of two security reviewers. Stage 3: the director. */
    private List<ApprovalStage> chain() {
        return List.of(ApprovalStage.of(1, List.of(teamLead)), ApprovalStage.of(2, List.of(securityA, securityB)), ApprovalStage.of(1, List.of(director)));
    }

    private ApprovalRequest request() {
        return ApprovalRequest.createNew(id, organisationId, "ChangeRequest", subjectId, requesterId, policyId, chain(), EXECUTOR);
    }

    // ------------------------------------------------------------ stage

    @Test
    @DisplayName("a stage needs a positive quorum, at least one approver and no more approvals than approvers")
    void stageValidation() {
        ApprovalStage ok = ApprovalStage.of(2, List.of(teamLead, securityA));
        assertEquals(2, ok.requiredApprovals());

        assertThrows(IllegalArgumentException.class, () -> ApprovalStage.of(0, List.of(teamLead)));
        assertThrows(IllegalArgumentException.class, () -> ApprovalStage.of(1, null));
        assertThrows(IllegalArgumentException.class, () -> ApprovalStage.of(1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ApprovalStage.of(3, List.of(teamLead, securityA)));
    }

    @Test
    @DisplayName("a stage read from storage may be incomplete: the record only copies, it never refuses")
    void stageRecordOnlyCopies() {
        assertTrue(new ApprovalStage(2, null).eligibleApproverIds().isEmpty());
    }

    // ------------------------------------------------------------ policy

    @Test
    @DisplayName("a policy keeps its stages in order and shows the first one as its quorum")
    void policyWithChain() {
        ApprovalPolicy policy = ApprovalPolicy.createNew(id, organisationId, "Production change", chain());

        assertEquals(3, policy.getStages().size());
        assertEquals(1, policy.getRequiredApprovals());
        assertEquals(List.of(teamLead), policy.getEligibleApproverIds());
        assertEquals(List.of(securityA, securityB), policy.getStages().get(1).eligibleApproverIds());
    }

    @Test
    @DisplayName("a chain needs at least one stage, at most ten, and a person can belong to only one stage")
    void policyChainRules() {
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.createNew(id, organisationId, "P", (List<ApprovalStage>) null));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.createNew(id, organisationId, "P", List.of()));

        List<ApprovalStage> eleven = new ArrayList<>();
        for (int i = 0; i < ApprovalPolicy.MAX_STAGES + 1; i++) {
            eleven.add(ApprovalStage.of(1, List.of(UUID.randomUUID())));
        }
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.createNew(id, organisationId, "P", eleven));
        assertEquals(ApprovalPolicy.MAX_STAGES, ApprovalPolicy.createNew(id, organisationId, "P", eleven.subList(0, ApprovalPolicy.MAX_STAGES)).getStages().size());

        var overlapping = List.of(ApprovalStage.of(1, List.of(teamLead)), ApprovalStage.of(1, List.of(securityA, teamLead)));
        var error = assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.createNew(id, organisationId, "P", overlapping));
        assertTrue(error.getMessage().contains("Segregation of duties"));

        // a stage the record would accept but that is not valid on its own is refused too
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.createNew(id, organisationId, "P", List.of(new ApprovalStage(5, List.of(teamLead)))));
    }

    @Test
    @DisplayName("update replaces the name and the whole chain, validating it like creation")
    void policyUpdate() {
        ApprovalPolicy policy = ApprovalPolicy.createNew(id, organisationId, "P", chain());

        policy.update("P2", List.of(ApprovalStage.of(1, List.of(director))));

        assertEquals("P2", policy.getName());
        assertEquals(1, policy.getStages().size());
        assertThrows(IllegalArgumentException.class, () -> policy.update(" ", chain()));
        assertThrows(IllegalArgumentException.class, () -> policy.update("P3", List.of()));
    }

    @Test
    @DisplayName("a policy stored before chains existed becomes the one-stage chain it always was")
    void policyLegacyReconstitute() {
        ApprovalPolicy legacy = ApprovalPolicy.reconstitute(id, organisationId, "CAB", 2, List.of(teamLead, securityA), null, null, null);
        ApprovalPolicy emptyStages = ApprovalPolicy.reconstitute(id, organisationId, "CAB", 1, List.of(teamLead), List.of(), null, null);
        ApprovalPolicy chained = ApprovalPolicy.reconstitute(id, organisationId, "CAB", 1, List.of(teamLead), chain(), Instant.now(), Instant.now());

        assertEquals(1, legacy.getStages().size());
        assertEquals(2, legacy.getRequiredApprovals());
        assertEquals(1, emptyStages.getStages().size());
        assertEquals(3, chained.getStages().size());
        assertNotNull(legacy.getCreatedAt());
    }

    // ------------------------------------------------------------ request

    @Test
    @DisplayName("a request starts on the first stage, with that stage's quorum and approvers")
    void requestStartsOnTheFirstStage() {
        ApprovalRequest request = request();

        assertEquals(0, request.getCurrentStage());
        assertEquals(3, request.getStages().size());
        assertEquals(1, request.getRequiredApprovals());
        assertEquals(List.of(teamLead), request.getEligibleApproverIds());
        assertEquals(ApprovalStatus.PENDING, request.getStatus());
    }

    @Test
    @DisplayName("completing a stage moves to the next one and the request resolves APPROVED only when the last stage does")
    void walksTheWholeChain() {
        ApprovalRequest request = request();

        var first = request.captureDecision(teamLead, DecisionOutcome.APPROVE, "ok", EXECUTOR);
        assertEquals(ApprovalStatus.PENDING, request.getStatus());
        assertEquals(1, request.getCurrentStage());
        assertEquals(List.of(securityA, securityB), request.getEligibleApproverIds());
        assertEquals(2, request.getRequiredApprovals());
        assertTrue(first.detail().contains("Stage 1 of 3 complete; now waiting on stage 2."));

        var half = request.captureDecision(securityA, DecisionOutcome.APPROVE, "ok", EXECUTOR);
        assertEquals(1, request.getCurrentStage());
        assertEquals(half.detail(), "Approver [" + securityA + "] decided [APPROVE].");

        request.captureDecision(securityB, DecisionOutcome.APPROVE, "ok", EXECUTOR);
        assertEquals(2, request.getCurrentStage());
        assertEquals(ApprovalStatus.PENDING, request.getStatus());

        var last = request.captureDecision(director, DecisionOutcome.APPROVE, "ok", EXECUTOR);
        assertEquals(ApprovalStatus.APPROVED, request.getStatus());
        assertEquals(ApprovalStatus.APPROVED, last.toStatus());
        assertEquals(2, request.getCurrentStage());
        assertEquals(List.of(0, 1, 1, 2), request.getDecisions().stream().map(Decision::stage).toList());
    }

    @Test
    @DisplayName("a REJECT at any stage resolves the whole request REJECTED at once")
    void rejectAtALaterStageEndsTheChain() {
        ApprovalRequest request = request();
        request.captureDecision(teamLead, DecisionOutcome.APPROVE, null, EXECUTOR);

        request.captureDecision(securityA, DecisionOutcome.REJECT, "no", EXECUTOR);

        assertEquals(ApprovalStatus.REJECTED, request.getStatus());
        assertEquals(1, request.getCurrentStage());
        assertThrows(InvalidApprovalRequestStatusException.class, () -> request.captureDecision(securityB, DecisionOutcome.APPROVE, null, EXECUTOR));
    }

    @Test
    @DisplayName("only the approvers of the current stage can decide, and nobody decides twice across the chain")
    void stageGuards() {
        ApprovalRequest request = request();

        var early = assertThrows(InvalidApprovalRequestStatusException.class, () -> request.captureDecision(director, DecisionOutcome.APPROVE, null, EXECUTOR));
        assertTrue(early.getMessage().contains("not eligible"));

        request.captureDecision(teamLead, DecisionOutcome.APPROVE, null, EXECUTOR);
        assertThrows(InvalidApprovalRequestStatusException.class, () -> request.captureDecision(teamLead, DecisionOutcome.APPROVE, null, EXECUTOR));
        request.captureDecision(securityA, DecisionOutcome.APPROVE, null, EXECUTOR);
        var twice = assertThrows(InvalidApprovalRequestStatusException.class, () -> request.captureDecision(securityA, DecisionOutcome.APPROVE, null, EXECUTOR));
        assertTrue(twice.getMessage().contains("already decided"));
    }

    @Test
    @DisplayName("a request needs at least one stage")
    void requestNeedsStages() {
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.createNew(id, organisationId, "ChangeRequest", subjectId, requesterId, policyId, (List<ApprovalStage>) null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ApprovalRequest.createNew(id, organisationId, "ChangeRequest", subjectId, requesterId, policyId, List.<ApprovalStage>of(), EXECUTOR));
    }

    @Test
    @DisplayName("a request stored before chains existed is the one-stage request it always was; a stored chain keeps its stage")
    void requestReconstitute() {
        ApprovalRequest legacy = ApprovalRequest.reconstitute(id, organisationId, "ChangeRequest", subjectId, requesterId, policyId, 2,
                List.of(teamLead, securityA), null, 0, ApprovalStatus.PENDING, null, null, null, null);
        ApprovalRequest empty = ApprovalRequest.reconstitute(id, organisationId, "ChangeRequest", subjectId, requesterId, policyId, 1,
                List.of(teamLead), List.of(), 0, ApprovalStatus.PENDING, null, null, null, null);
        ApprovalRequest chained = ApprovalRequest.reconstitute(id, organisationId, "ChangeRequest", subjectId, requesterId, policyId, 9,
                List.of(), chain(), 1, ApprovalStatus.PENDING, List.of(), null, null, null);

        assertEquals(1, legacy.getStages().size());
        assertEquals(2, legacy.getRequiredApprovals());
        assertEquals(1, empty.getStages().size());
        assertEquals(1, chained.getCurrentStage());
        assertEquals(List.of(securityA, securityB), chained.getEligibleApproverIds());
    }

    @Test
    @DisplayName("a vote written without a stage is a first-stage vote")
    void decisionDefaultsToTheFirstStage() {
        assertEquals(0, new Decision(teamLead, DecisionOutcome.APPROVE, "ok", Instant.now()).stage());
        assertEquals(2, new Decision(teamLead, DecisionOutcome.APPROVE, "ok", Instant.now(), 2).stage());
    }
}
