package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.ApprovalRequest;
import com.thinklab.domain.model.ApprovalRequest.ApprovalStatus;
import com.thinklab.domain.model.ApprovalRequest.DecisionOutcome;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalRequestDocument.ApprovalRequestPersistenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalRequestDocumentTest {

    private static final String EXECUTOR = "op-1";

    @Test
    @DisplayName("toDocument/toDomain round-trip preserves the initiating audit entry, including its null fromStatus")
    void roundTripFreshRequest() {
        UUID approverA = UUID.randomUUID();
        ApprovalRequest request = ApprovalRequest.createNew(UUID.randomUUID(), UUID.randomUUID(), "ChangeRequest",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, List.of(approverA), EXECUTOR);

        ApprovalRequestDocument document = ApprovalRequestPersistenceMapper.toDocument(request);
        ApprovalRequest restored = ApprovalRequestPersistenceMapper.toDomain(document);

        assertEquals(request.getId(), restored.getId());
        assertEquals(request.getStatus(), restored.getStatus());
        assertEquals(1, restored.getAuditTrail().size());
        assertNull(restored.getAuditTrail().get(0).fromStatus());
        assertEquals(ApprovalStatus.PENDING, restored.getAuditTrail().get(0).toStatus());
        assertTrue(restored.getDecisions().isEmpty());
    }

    @Test
    @DisplayName("toDocument/toDomain round-trip preserves a captured decision and its resolved audit entry")
    void roundTripWithDecision() {
        UUID approverA = UUID.randomUUID();
        ApprovalRequest request = ApprovalRequest.createNew(UUID.randomUUID(), UUID.randomUUID(), "ChangeRequest",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, List.of(approverA), EXECUTOR);
        request.captureDecision(approverA, DecisionOutcome.APPROVE, "ok", EXECUTOR);

        ApprovalRequestDocument document = ApprovalRequestPersistenceMapper.toDocument(request);
        ApprovalRequest restored = ApprovalRequestPersistenceMapper.toDomain(document);

        assertEquals(ApprovalStatus.APPROVED, restored.getStatus());
        assertEquals(1, restored.getDecisions().size());
        assertEquals(approverA, restored.getDecisions().get(0).approverId());
        assertEquals(DecisionOutcome.APPROVE, restored.getDecisions().get(0).outcome());
        assertEquals(2, restored.getAuditTrail().size());
        assertEquals(ApprovalStatus.PENDING, restored.getAuditTrail().get(1).fromStatus());
        assertEquals(ApprovalStatus.APPROVED, restored.getAuditTrail().get(1).toStatus());
    }

    @Test
    @DisplayName("toDomain defaults null status/decisions/approvers/auditTrail to their safe empty forms")
    void toDomainNullDefaults() {
        ApprovalRequestDocument document = new ApprovalRequestDocument();
        document.setId(UUID.randomUUID());
        document.setOrganisationId(UUID.randomUUID());
        document.setSubjectType("ChangeRequest");
        document.setSubjectId(UUID.randomUUID());
        document.setRequesterId(UUID.randomUUID());
        document.setPolicyId(UUID.randomUUID());
        document.setStatus(null);
        document.setEligibleApproverIds(null);
        document.setDecisions(null);
        document.setAuditTrail(null);

        ApprovalRequest restored = ApprovalRequestPersistenceMapper.toDomain(document);

        assertEquals(ApprovalStatus.PENDING, restored.getStatus());
        assertTrue(restored.getEligibleApproverIds().isEmpty());
        assertTrue(restored.getDecisions().isEmpty());
        assertTrue(restored.getAuditTrail().isEmpty());
    }

    @Test
    @DisplayName("the persistence mapper is a non-instantiable utility class")
    void cannotInstantiate() throws Exception {
        var constructor = ApprovalRequestPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
        assertTrue(ex.getCause() instanceof UnsupportedOperationException);
    }
}
