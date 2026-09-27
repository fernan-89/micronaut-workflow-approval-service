package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.ApprovalPolicy;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalPolicyDocument.ApprovalPolicyPersistenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalPolicyDocumentTest {

    @Test
    @DisplayName("toDocument/toDomain round-trip preserves every field")
    void roundTrip() {
        ApprovalPolicy policy = ApprovalPolicy.createNew(UUID.randomUUID(), UUID.randomUUID(), "CAB", 2,
                List.of(UUID.randomUUID(), UUID.randomUUID()));

        ApprovalPolicyDocument document = ApprovalPolicyPersistenceMapper.toDocument(policy);
        ApprovalPolicy restored = ApprovalPolicyPersistenceMapper.toDomain(document);

        assertEquals(policy.getId(), restored.getId());
        assertEquals(policy.getOrganisationId(), restored.getOrganisationId());
        assertEquals(policy.getName(), restored.getName());
        assertEquals(policy.getRequiredApprovals(), restored.getRequiredApprovals());
        assertEquals(policy.getEligibleApproverIds(), restored.getEligibleApproverIds());
        assertEquals(policy.getCreatedAt(), restored.getCreatedAt());
        assertEquals(policy.getUpdatedAt(), restored.getUpdatedAt());
    }

    @Test
    @DisplayName("toDomain defaults a null approver list to empty")
    void toDomainNullApprovers() {
        ApprovalPolicyDocument document = new ApprovalPolicyDocument();
        document.setId(UUID.randomUUID());
        document.setOrganisationId(UUID.randomUUID());
        document.setName("CAB");
        document.setRequiredApprovals(1);
        document.setEligibleApproverIds(null);

        ApprovalPolicy restored = ApprovalPolicyPersistenceMapper.toDomain(document);

        assertTrue(restored.getEligibleApproverIds().isEmpty());
    }

    @Test
    @DisplayName("the persistence mapper is a non-instantiable utility class")
    void cannotInstantiate() throws Exception {
        var constructor = ApprovalPolicyPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
        assertTrue(ex.getCause() instanceof UnsupportedOperationException);
    }
}
