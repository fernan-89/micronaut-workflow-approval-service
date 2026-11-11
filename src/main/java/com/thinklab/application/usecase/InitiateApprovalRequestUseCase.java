package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateApprovalRequestRequest;
import com.thinklab.application.dto.response.ApprovalRequestResponse;
import com.thinklab.application.mapper.ApprovalRequestMapper;
import com.thinklab.domain.exception.ApprovalPolicyNotFoundException;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.ApprovalPolicyRepository;
import com.thinklab.domain.repository.ApprovalRequestRepository;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for filing a new ApprovalRequest (BIAN Behavior Qualifier: {@code initiate}). Loads the
 * referenced {@link com.thinklab.domain.model.ApprovalPolicy} first and snapshots its quorum onto the
 * new request - an in-flight request never shifts under a later policy edit.
 */
@Singleton
public class InitiateApprovalRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateApprovalRequestUseCase.class);

    private final HashServicePort hashServicePort;
    private final ApprovalPolicyRepository policyRepository;
    private final ApprovalRequestRepository requestRepository;
    private final boolean policyTenantScoped;

    /**
     * @param policyTenantScoped when true (ADR-036) a request can only be filed against a policy of its own tenant; when false (the default, so nothing changes for
     *                           a deployment whose policy ids are shared by every tenant, like the CAB/ECAB ones of change management) the policy is found by id alone
     */
    public InitiateApprovalRequestUseCase(HashServicePort hashServicePort, ApprovalPolicyRepository policyRepository,
                                           ApprovalRequestRepository requestRepository,
                                           @Value("${thinklab.approval.policy-tenant-scoped:false}") boolean policyTenantScoped) {
        this.hashServicePort = hashServicePort;
        this.policyRepository = policyRepository;
        this.requestRepository = requestRepository;
        this.policyTenantScoped = policyTenantScoped;
    }

    public Mono<ApprovalRequestResponse> execute(UUID organisationId, InitiateApprovalRequestRequest request, String executor) {
        log.info("[USE CASE] Initiating ApprovalRequest for organisation: {} subject: {}/{}", organisationId, request.subjectType(), request.subjectId());

        return (policyTenantScoped ? policyRepository.findById(request.policyId(), organisationId) : policyRepository.findById(request.policyId()))
                .switchIfEmpty(Mono.error(new ApprovalPolicyNotFoundException(request.policyId())))
                .flatMap(policy -> hashServicePort.generateSovereignId("approval-request-creation")
                        .map(sovereignId -> ApprovalRequestMapper.toDomain(request, sovereignId, organisationId, policy, executor)))
                .flatMap(requestRepository::create)
                .map(ApprovalRequestMapper::toResponse);
    }
}
