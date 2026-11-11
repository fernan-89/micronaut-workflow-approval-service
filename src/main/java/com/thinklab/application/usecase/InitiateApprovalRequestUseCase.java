package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateApprovalRequestRequest;
import com.thinklab.application.dto.response.ApprovalRequestResponse;
import com.thinklab.application.mapper.ApprovalRequestMapper;
import com.thinklab.domain.exception.ApprovalPolicyNotFoundException;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.ApprovalPolicyRepository;
import com.thinklab.domain.repository.ApprovalRequestRepository;
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

    public InitiateApprovalRequestUseCase(HashServicePort hashServicePort, ApprovalPolicyRepository policyRepository,
                                           ApprovalRequestRepository requestRepository) {
        this.hashServicePort = hashServicePort;
        this.policyRepository = policyRepository;
        this.requestRepository = requestRepository;
    }

    public Mono<ApprovalRequestResponse> execute(UUID organisationId, InitiateApprovalRequestRequest request, String executor) {
        log.info("[USE CASE] Initiating ApprovalRequest for organisation: {} subject: {}/{}", organisationId, request.subjectType(), request.subjectId());

        // A request is filed against a policy of its own tenant only: another tenant's policy is the same as none (404).
        return policyRepository.findById(request.policyId(), organisationId)
                .switchIfEmpty(Mono.error(new ApprovalPolicyNotFoundException(request.policyId())))
                .flatMap(policy -> hashServicePort.generateSovereignId("approval-request-creation")
                        .map(sovereignId -> ApprovalRequestMapper.toDomain(request, sovereignId, organisationId, policy, executor)))
                .flatMap(requestRepository::create)
                .map(ApprovalRequestMapper::toResponse);
    }
}
