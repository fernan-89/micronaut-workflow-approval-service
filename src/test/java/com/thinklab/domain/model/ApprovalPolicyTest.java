package com.thinklab.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalPolicyTest {

    private final UUID id = UUID.randomUUID();
    private final UUID organisationId = UUID.randomUUID();
    private final List<UUID> approvers = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Test
    @DisplayName("createNew builds a valid policy")
    void createNewBuildsAValidPolicy() {
        ApprovalPolicy policy = ApprovalPolicy.createNew(id, organisationId, "CAB", 2, approvers);

        assertEquals("CAB", policy.getName());
        assertEquals(2, policy.getRequiredApprovals());
        assertEquals(approvers, policy.getEligibleApproverIds());
        assertNotNull(policy.getCreatedAt());
        assertEquals(policy.getCreatedAt(), policy.getUpdatedAt());
    }

    @Test
    @DisplayName("createNew rejects missing identity, blank name, and an invalid quorum")
    void createNewGuards() {
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.createNew(null, organisationId, "CAB", 2, approvers));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.createNew(id, null, "CAB", 2, approvers));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.createNew(id, organisationId, null, 2, approvers));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.createNew(id, organisationId, "", 2, approvers));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.createNew(id, organisationId, "CAB", 0, approvers));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.createNew(id, organisationId, "CAB", 2, null));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.createNew(id, organisationId, "CAB", 2, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.createNew(id, organisationId, "CAB", 4, approvers));
    }

    @Test
    @DisplayName("reconstitute rejects missing identity or name")
    void reconstituteGuards() {
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.reconstitute(null, organisationId, "CAB", 2, approvers, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.reconstitute(id, null, "CAB", 2, approvers, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ApprovalPolicy.reconstitute(id, organisationId, null, 2, approvers, null, null, null));
    }

    @Test
    @DisplayName("reconstitute defaults missing timestamps and a null approver list")
    void reconstituteDefaults() {
        ApprovalPolicy policy = ApprovalPolicy.reconstitute(id, organisationId, "CAB", 2, null, null, null, null);

        assertNotNull(policy.getCreatedAt());
        assertEquals(policy.getCreatedAt(), policy.getUpdatedAt());
        assertTrue(policy.getEligibleApproverIds().isEmpty());
    }

    @Test
    @DisplayName("update replaces name, quorum and approvers, and rejects an invalid quorum")
    void update() {
        ApprovalPolicy policy = ApprovalPolicy.createNew(id, organisationId, "CAB", 2, approvers);

        policy.update("CAB-v2", List.of(new ApprovalStage(3, approvers)));

        assertEquals("CAB-v2", policy.getName());
        assertEquals(3, policy.getRequiredApprovals());

        assertThrows(IllegalArgumentException.class, () -> policy.update(null, List.of(new ApprovalStage(2, approvers))));
        assertThrows(IllegalArgumentException.class, () -> policy.update("", List.of(new ApprovalStage(2, approvers))));
        assertThrows(IllegalArgumentException.class, () -> policy.update("CAB", List.of(new ApprovalStage(0, approvers))));
        assertThrows(IllegalArgumentException.class, () -> policy.update("CAB", List.of(new ApprovalStage(2, null))));
        assertThrows(IllegalArgumentException.class, () -> policy.update("CAB", List.of(new ApprovalStage(2, List.of()))));
        assertThrows(IllegalArgumentException.class, () -> policy.update("CAB", List.of(new ApprovalStage(10, approvers))));
    }
}
