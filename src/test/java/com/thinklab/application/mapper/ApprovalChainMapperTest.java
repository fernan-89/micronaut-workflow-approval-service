package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateApprovalRequestRequest;
import com.thinklab.application.dto.request.InitiatePolicyRequest;
import com.thinklab.application.dto.request.StageRequest;
import com.thinklab.domain.model.ApprovalPolicy;
import com.thinklab.domain.model.ApprovalRequest;
import com.thinklab.domain.model.ApprovalRequest.DecisionOutcome;
import com.thinklab.domain.model.ApprovalStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a policy request becomes a chain, and how a chain is shown (ADR-033). */
class ApprovalChainMapperTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    @DisplayName("stages become the chain; the original pair becomes a one-stage chain")
    void toStages() {
        var chain = ApprovalPolicyMapper.toStages(null, null, List.of(new StageRequest(1, List.of(a)), new StageRequest(1, List.of(b))));
        var single = ApprovalPolicyMapper.toStages(1, List.of(a), null);
        var singleWithEmptyStages = ApprovalPolicyMapper.toStages(1, List.of(a), List.of());

        assertEquals(2, chain.size());
        assertEquals(List.of(b), chain.get(1).eligibleApproverIds());
        assertEquals(List.of(new ApprovalStage(1, List.of(a))), single);
        assertEquals(single, singleWithEmptyStages);
    }

    @Test
    @DisplayName("stages together with the original pair is ambiguous, and giving neither (or half the pair) is incomplete")
    void toStagesRefusals() {
        var stages = List.of(new StageRequest(1, List.of(a)));

        assertTrue(assertThrows(IllegalArgumentException.class, () -> ApprovalPolicyMapper.toStages(1, List.of(a), stages)).getMessage().contains("not both"));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicyMapper.toStages(null, List.of(a), stages));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicyMapper.toStages(1, null, stages));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicyMapper.toStages(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicyMapper.toStages(null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicyMapper.toStages(1, null, null));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicyMapper.toStages(null, List.of(a), null));
    }

    @Test
    @DisplayName("a policy with stages is built from the request and answers with its chain")
    void policyWithStagesRoundTrip() {
        var request = new InitiatePolicyRequest("Production change", null, null, List.of(new StageRequest(1, List.of(a)), new StageRequest(1, List.of(b))));

        ApprovalPolicy policy = ApprovalPolicyMapper.toDomain(request, UUID.randomUUID(), UUID.randomUUID());
        var response = ApprovalPolicyMapper.toResponse(policy);

        assertEquals(2, response.stages().size());
        assertEquals(1, response.requiredApprovals());
        assertEquals(List.of(a), response.eligibleApproverIds());
        assertEquals(List.of(b), response.stages().get(1).eligibleApproverIds());
    }

    @Test
    @DisplayName("a request answers with its chain, the stage it waits on (one-based) and the stage of each vote (one-based)")
    void requestResponseShowsTheChain() {
        var policy = ApprovalPolicy.createNew(UUID.randomUUID(), UUID.randomUUID(), "P",
                List.of(ApprovalStage.of(1, List.of(a)), ApprovalStage.of(1, List.of(b))));
        var filed = new InitiateApprovalRequestRequest("ChangeRequest", UUID.randomUUID(), UUID.randomUUID(), policy.getId());
        ApprovalRequest request = ApprovalRequestMapper.toDomain(filed, UUID.randomUUID(), policy.getOrganisationId(), policy, "op-1");
        request.captureDecision(a, DecisionOutcome.APPROVE, "ok", "op-1");

        var response = ApprovalRequestMapper.toResponse(request);

        assertEquals(2, response.currentStage());
        assertEquals(2, response.stages().size());
        assertEquals(List.of(b), response.eligibleApproverIds());
        assertEquals(1, response.decisions().get(0).stage());
        assertEquals("PENDING", response.status());
    }
}
