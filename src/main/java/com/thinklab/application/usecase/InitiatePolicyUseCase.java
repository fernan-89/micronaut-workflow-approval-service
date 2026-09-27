package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiatePolicyRequest;
import com.thinklab.application.dto.response.ApprovalPolicyResponse;
import com.thinklab.application.mapper.ApprovalPolicyMapper;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.ApprovalPolicyRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for creating a new ApprovalPolicy (BIAN Behavior Qualifier: {@code policy/initiate}). */
@Singleton
public class InitiatePolicyUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiatePolicyUseCase.class);

    private final HashServicePort hashServicePort;
    private final ApprovalPolicyRepository policyRepository;

    public InitiatePolicyUseCase(HashServicePort hashServicePort, ApprovalPolicyRepository policyRepository) {
        this.hashServicePort = hashServicePort;
        this.policyRepository = policyRepository;
    }

    public Mono<ApprovalPolicyResponse> execute(UUID organisationId, InitiatePolicyRequest request) {
        log.info("[USE CASE] Initiating ApprovalPolicy '{}' for organisation: {}", request.name(), organisationId);

        return hashServicePort.generateSovereignId("approval-policy-creation")
                .map(sovereignId -> ApprovalPolicyMapper.toDomain(request, sovereignId, organisationId))
                .flatMap(policyRepository::create)
                .map(ApprovalPolicyMapper::toResponse);
    }
}
