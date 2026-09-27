package com.thinklab.domain.repository;

import com.thinklab.domain.model.ApprovalRequest;
import com.thinklab.domain.model.ApprovalRequest.ApprovalAuditEntry;
import com.thinklab.domain.model.ApprovalRequest.ApprovalStatus;
import com.thinklab.domain.model.ApprovalRequest.Decision;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound Port for ApprovalRequest persistence operations (Workflow Approval Service Domain).
 *
 * <p>ARCHITECTURAL RULE: Partial State Mutations (ADR-002). {@link #create(ApprovalRequest)} is the
 * only whole-document write. There is no {@code deleteById} - {@link #updateStatus} moves a request to
 * a terminal status instead of a physical delete, matching every other Service Domain in this platform.
 */
public interface ApprovalRequestRepository {

    Mono<ApprovalRequest> create(ApprovalRequest request);

    Mono<ApprovalRequest> findById(UUID id);

    /**
     * Tenant-scoped listing, optionally filtered by the referenced subject and/or status - this is
     * how a client Service Domain looks up "the approval request for my ChangeRequest [id]".
     */
    Flux<ApprovalRequest> findAllByOrganisationId(UUID organisationId, String subjectType, UUID subjectId, ApprovalStatus status);

    Mono<Void> addDecision(UUID id, Decision decision, ApprovalStatus status, ApprovalAuditEntry auditEntry);

    Mono<Void> updateStatus(UUID id, ApprovalStatus status, ApprovalAuditEntry auditEntry);
}
