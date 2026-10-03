package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.ApprovalPolicy;
import com.thinklab.domain.model.ApprovalRequest;
import com.thinklab.domain.model.ApprovalRequest.DecisionOutcome;
import com.thinklab.domain.model.ApprovalStage;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalPolicyDocument.ApprovalPolicyPersistenceMapper;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalRequestDocument.ApprovalRequestPersistenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** How a chain is stored and, as important, how data stored before chains existed is still read (ADR-033). */
class ApprovalChainDocumentTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final List<ApprovalStage> chain = List.of(ApprovalStage.of(1, List.of(a)), ApprovalStage.of(1, List.of(b)));

    @Test
    @DisplayName("a request keeps its chain, the stage it waits on and the stage of every vote through storage")
    void requestRoundTrip() {
        ApprovalRequest request = ApprovalRequest.createNew(UUID.randomUUID(), UUID.randomUUID(), "ChangeRequest", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), chain, "op-1");
        request.captureDecision(a, DecisionOutcome.APPROVE, "ok", "op-1");

        ApprovalRequestDocument document = ApprovalRequestPersistenceMapper.toDocument(request);
        ApprovalRequest restored = ApprovalRequestPersistenceMapper.toDomain(document);

        // the original fields describe the stage the request waits on now: that is what the approver inbox searches
        assertEquals(List.of(b), document.getEligibleApproverIds());
        assertEquals(1, document.getCurrentStage());
        assertEquals(2, document.getStages().size());
        assertEquals(1, restored.getCurrentStage());
        assertEquals(chain, restored.getStages());
        assertEquals(0, restored.getDecisions().get(0).stage());
    }

    @Test
    @DisplayName("a request document written before chains existed (no stages, no stage on its votes) reads as a one-stage request")
    void legacyRequestDocument() {
        ApprovalRequest request = ApprovalRequest.createNew(UUID.randomUUID(), UUID.randomUUID(), "ChangeRequest", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 2, List.of(a, b), "op-1");
        ApprovalRequestDocument document = ApprovalRequestPersistenceMapper.toDocument(request);
        document.setStages(null);

        ApprovalRequest restored = ApprovalRequestPersistenceMapper.toDomain(document);

        assertEquals(1, restored.getStages().size());
        assertEquals(2, restored.getRequiredApprovals());
        assertEquals(List.of(a, b), restored.getEligibleApproverIds());
        assertEquals(0, restored.getCurrentStage());
    }

    @Test
    @DisplayName("a policy keeps its chain through storage, and its first stage in the original fields")
    void policyRoundTrip() {
        ApprovalPolicy policy = ApprovalPolicy.createNew(UUID.randomUUID(), UUID.randomUUID(), "Production change", chain);

        ApprovalPolicyDocument document = ApprovalPolicyPersistenceMapper.toDocument(policy);
        ApprovalPolicy restored = ApprovalPolicyPersistenceMapper.toDomain(document);

        assertEquals(1, document.getRequiredApprovals());
        assertEquals(List.of(a), document.getEligibleApproverIds());
        assertEquals(chain, restored.getStages());
    }

    @Test
    @DisplayName("a policy document written before chains existed reads as a one-stage policy")
    void legacyPolicyDocument() {
        ApprovalPolicy policy = ApprovalPolicy.createNew(UUID.randomUUID(), UUID.randomUUID(), "CAB", 2, List.of(a, b));
        ApprovalPolicyDocument document = ApprovalPolicyPersistenceMapper.toDocument(policy);
        document.setStages(null);

        ApprovalPolicy restored = ApprovalPolicyPersistenceMapper.toDomain(document);

        assertEquals(List.of(new ApprovalStage(2, List.of(a, b))), restored.getStages());
    }

    @Test
    @DisplayName("stage documents convert both ways, and nothing stored means no chain")
    void stageDocuments() {
        var documents = StageDocument.fromDomain(chain);

        assertEquals(chain, StageDocument.toDomain(documents));
        assertNull(StageDocument.toDomain((List<StageDocument>) null));
        assertEquals(1, documents.get(0).getRequiredApprovals());
        assertEquals(List.of(a), documents.get(0).getEligibleApproverIds());
    }
}
