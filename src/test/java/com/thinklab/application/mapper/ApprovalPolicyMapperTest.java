package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiatePolicyRequest;
import com.thinklab.application.dto.response.ApprovalPolicyResponse;
import com.thinklab.domain.model.ApprovalPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalPolicyMapperTest {

    @Test
    @DisplayName("toDomain builds a new ApprovalPolicy from the request, id and organisation")
    void toDomain() {
        List<UUID> approvers = List.of(UUID.randomUUID());
        InitiatePolicyRequest request = new InitiatePolicyRequest("CAB", 1, approvers);
        UUID id = UUID.randomUUID();
        UUID organisationId = UUID.randomUUID();

        ApprovalPolicy policy = ApprovalPolicyMapper.toDomain(request, id, organisationId);

        assertEquals(id, policy.getId());
        assertEquals(organisationId, policy.getOrganisationId());
        assertEquals("CAB", policy.getName());
        assertEquals(1, policy.getRequiredApprovals());
        assertEquals(approvers, policy.getEligibleApproverIds());
    }

    @Test
    @DisplayName("toResponse projects every field")
    void toResponse() {
        ApprovalPolicy policy = ApprovalPolicy.createNew(UUID.randomUUID(), UUID.randomUUID(), "CAB", 1, List.of(UUID.randomUUID()));

        ApprovalPolicyResponse response = ApprovalPolicyMapper.toResponse(policy);

        assertEquals(policy.getId(), response.id());
        assertEquals(policy.getOrganisationId(), response.organisationId());
        assertEquals(policy.getName(), response.name());
        assertEquals(policy.getRequiredApprovals(), response.requiredApprovals());
        assertEquals(policy.getEligibleApproverIds(), response.eligibleApproverIds());
        assertEquals(policy.getCreatedAt(), response.createdAt());
        assertEquals(policy.getUpdatedAt(), response.updatedAt());
    }

    @Test
    @DisplayName("the utility class cannot be instantiated")
    void cannotInstantiate() throws NoSuchMethodException {
        Constructor<ApprovalPolicyMapper> constructor = ApprovalPolicyMapper.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
    }
}
