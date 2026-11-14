package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ApprovalPolicyResponse;
import com.thinklab.application.mapper.ApprovalPolicyMapper;
import com.thinklab.domain.exception.ApprovalPolicyNotFoundException;
import com.thinklab.domain.repository.ApprovalPolicyRepository;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
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

    private final boolean policyTenantScoped;

    /** @param policyTenantScoped when true (ADR-036) only a policy of the caller's tenant is found; when false (the default) it is found by id alone */
    public RetrievePolicyUseCase(ApprovalPolicyRepository policyRepository, @Value("${thinklab.approval.policy-tenant-scoped:false}") boolean policyTenantScoped) {
        this.policyRepository = policyRepository;
        this.policyTenantScoped = policyTenantScoped;
    }

    /** @param organisationId the caller's tenant; only needed (and then mandatory) when the tenant scoping is on, because change management reads one shared CAB/ECAB policy without it */
    public Mono<ApprovalPolicyResponse> execute(UUID id, @Nullable UUID organisationId) {
        log.info("[USE CASE] Retrieving ApprovalPolicy by ID: {}", id);

        if (policyTenantScoped && organisationId == null) {
            return Mono.error(new IllegalArgumentException("The X-Tenant-Id header is required to read a policy while the tenant scoping is on."));
        }
        return (policyTenantScoped ? policyRepository.findById(id, organisationId) : policyRepository.findById(id))
                .switchIfEmpty(Mono.error(new ApprovalPolicyNotFoundException(id)))
                .map(ApprovalPolicyMapper::toResponse);
    }
}
