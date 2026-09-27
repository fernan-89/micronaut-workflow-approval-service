package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ApprovalRequestResponse;
import com.thinklab.application.mapper.ApprovalRequestMapper;
import com.thinklab.domain.model.ApprovalRequest.ApprovalStatus;
import com.thinklab.domain.repository.ApprovalRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Use Case for the tenant-scoped ApprovalRequest collection (BIAN Behavior Qualifier: {@code retrieve}).
 * The {@code subjectType}/{@code subjectId} filters are how a client Service Domain looks up "the
 * approval request for my ChangeRequest [id]".
 */
@Singleton
public class RetrieveApprovalRequestsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveApprovalRequestsUseCase.class);

    private final ApprovalRequestRepository requestRepository;

    public RetrieveApprovalRequestsUseCase(ApprovalRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    public Flux<ApprovalRequestResponse> execute(UUID organisationId, String subjectType, UUID subjectId, ApprovalStatus status) {
        log.info("[USE CASE] Retrieving ApprovalRequests for organisation: {} subject: {}/{} status: {}", organisationId, subjectType, subjectId, status);

        return requestRepository.findAllByOrganisationId(organisationId, subjectType, subjectId, status)
                .map(ApprovalRequestMapper::toResponse);
    }
}
