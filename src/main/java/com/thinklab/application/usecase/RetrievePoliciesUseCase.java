package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ApprovalPolicyResponse;
import com.thinklab.application.mapper.ApprovalPolicyMapper;
import com.thinklab.domain.repository.ApprovalPolicyRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** Use Case for the tenant-scoped ApprovalPolicy collection (BIAN Behavior Qualifier: {@code policy/retrieve}). */
@Singleton
public class RetrievePoliciesUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrievePoliciesUseCase.class);

    private final ApprovalPolicyRepository policyRepository;

    public RetrievePoliciesUseCase(ApprovalPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    public Flux<ApprovalPolicyResponse> execute(UUID organisationId, String name) {
        log.info("[USE CASE] Retrieving ApprovalPolicies for organisation: {} name: {}", organisationId, name);

        return policyRepository.findAllByOrganisationId(organisationId, name)
                .map(ApprovalPolicyMapper::toResponse);
    }
}
