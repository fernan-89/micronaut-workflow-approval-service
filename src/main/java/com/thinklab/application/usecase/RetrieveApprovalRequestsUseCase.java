package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ApprovalRequestResponse;
import com.thinklab.application.mapper.ApprovalRequestMapper;
import com.thinklab.domain.model.ApprovalRequest;
import com.thinklab.domain.model.ApprovalRequest.ApprovalStatus;
import com.thinklab.domain.repository.ApprovalRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Comparator;
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

    /**
     * With {@code pendingFor} this is that approver's inbox: the PENDING requests whose current stage lists them and that they have not
     * decided yet, oldest first (the other filters do not apply). Without it, the filtered collection.
     */
    public Flux<ApprovalRequestResponse> execute(UUID organisationId, String subjectType, UUID subjectId, ApprovalStatus status, UUID pendingFor) {
        log.info("[USE CASE] Retrieving ApprovalRequests for organisation: {} subject: {}/{} status: {} pendingFor: {}", organisationId, subjectType, subjectId, status, pendingFor);

        if (pendingFor != null) {
            return requestRepository.findPendingFor(organisationId, pendingFor)
                    .sort(Comparator.comparing(ApprovalRequest::getCreatedAt))
                    .map(ApprovalRequestMapper::toResponse);
        }
        return requestRepository.findAllByOrganisationId(organisationId, subjectType, subjectId, status)
                .map(ApprovalRequestMapper::toResponse);
    }
}
