package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ApprovalPolicyResponse;
import com.thinklab.application.mapper.ApprovalPolicyMapper;
import com.thinklab.domain.exception.ApprovalPolicyNotFoundException;
import com.thinklab.domain.repository.ApprovalPolicyRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for retrieving a single ApprovalPolicy (BIAN Behavior Qualifier: {@code policy/retrieve}). */
@Singleton
public class RetrievePolicyUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrievePolicyUseCase.class);

    private final ApprovalPolicyRepository policyRepository;

    public RetrievePolicyUseCase(ApprovalPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    /** @param organisationId the caller's tenant: only a policy of that tenant is found, another tenant's is the same as none (ADR-039) */
    public Mono<ApprovalPolicyResponse> execute(UUID id, UUID organisationId) {
        log.info("[USE CASE] Retrieving ApprovalPolicy by ID: {}", id);

        return policyRepository.findById(id, organisationId)
                .switchIfEmpty(Mono.error(new ApprovalPolicyNotFoundException(id)))
                .map(ApprovalPolicyMapper::toResponse);
    }
}
