package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiatePolicyRequest;
import com.thinklab.application.dto.request.UpdatePolicyRequest;
import com.thinklab.domain.exception.ApprovalPolicyNotFoundException;
import com.thinklab.domain.model.ApprovalPolicy;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.ApprovalPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalPolicyUseCaseTest {

    @Mock private HashServicePort hashServicePort;
    @Mock private ApprovalPolicyRepository policyRepository;

    private UUID organisationId;
    private ApprovalPolicy policy;

    @BeforeEach
    void setUp() {
        organisationId = UUID.randomUUID();
        policy = ApprovalPolicy.createNew(UUID.randomUUID(), organisationId, "CAB", 1, List.of(UUID.randomUUID()));
    }

    // --- InitiatePolicyUseCase ---

    @Test
    @DisplayName("initiate: fetches a sovereign id and persists the new policy")
    void initiate() {
        InitiatePolicyRequest request = new InitiatePolicyRequest("CAB", 1, List.of(UUID.randomUUID()));
        when(hashServicePort.generateSovereignId("approval-policy-creation")).thenReturn(Mono.just(policy.getId()));
        when(policyRepository.create(any(ApprovalPolicy.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        InitiatePolicyUseCase useCase = new InitiatePolicyUseCase(hashServicePort, policyRepository);

        StepVerifier.create(useCase.execute(organisationId, request))
                .assertNext(response -> org.junit.jupiter.api.Assertions.assertEquals("CAB", response.name()))
                .verifyComplete();
    }

    // --- RetrievePolicyUseCase ---

    @Test
    @DisplayName("retrieveById: not found surfaces ApprovalPolicyNotFoundException")
    void retrieveByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(policyRepository.findById(id)).thenReturn(Mono.empty());
        RetrievePolicyUseCase useCase = new RetrievePolicyUseCase(policyRepository);

        StepVerifier.create(useCase.execute(id)).expectError(ApprovalPolicyNotFoundException.class).verify();
    }

    @Test
    @DisplayName("retrieveById: maps the found policy to a response")
    void retrieveByIdFound() {
        when(policyRepository.findById(policy.getId())).thenReturn(Mono.just(policy));
        RetrievePolicyUseCase useCase = new RetrievePolicyUseCase(policyRepository);

        StepVerifier.create(useCase.execute(policy.getId())).expectNextCount(1).verifyComplete();
    }

    // --- RetrievePoliciesUseCase ---

    @Test
    @DisplayName("retrieveAll: delegates to the repository's tenant-scoped, optionally name-filtered query")
    void retrieveAll() {
        when(policyRepository.findAllByOrganisationId(organisationId, "CAB")).thenReturn(Flux.just(policy));
        RetrievePoliciesUseCase useCase = new RetrievePoliciesUseCase(policyRepository);

        StepVerifier.create(useCase.execute(organisationId, "CAB")).expectNextCount(1).verifyComplete();
    }

    // --- UpdatePolicyUseCase ---

    @Test
    @DisplayName("update: not found surfaces ApprovalPolicyNotFoundException")
    void updateNotFound() {
        UUID id = UUID.randomUUID();
        when(policyRepository.findById(id)).thenReturn(Mono.empty());
        UpdatePolicyUseCase useCase = new UpdatePolicyUseCase(policyRepository);

        StepVerifier.create(useCase.execute(id, new UpdatePolicyRequest("CAB-v2", 1, List.of(UUID.randomUUID()))))
                .expectError(ApprovalPolicyNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("update: applies the domain mutation and persists the granular update")
    void updateSuccess() {
        when(policyRepository.findById(policy.getId())).thenReturn(Mono.just(policy));
        when(policyRepository.updateBasicInfo(any(), any(), anyInt(), any())).thenReturn(Mono.empty());
        UpdatePolicyUseCase useCase = new UpdatePolicyUseCase(policyRepository);

        StepVerifier.create(useCase.execute(policy.getId(), new UpdatePolicyRequest("CAB-v2", 1, List.of(UUID.randomUUID()))))
                .verifyComplete();
    }
}
