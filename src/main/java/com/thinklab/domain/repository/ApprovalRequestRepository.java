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

    /**
     * Persists the vote that was just captured on {@code updated} (the aggregate AFTER the decision): the vote, the new status, the
     * stage the request now waits on and that stage's quorum and approvers, and the audit entry - all in one atomic update. The write
     * only applies while the request still has the decisions it had when it was loaded, so two votes racing for the same request cannot
     * both be applied on top of the same state: the loser gets {@link com.thinklab.domain.exception.InvalidApprovalRequestStatusException} (409, retry).
     */
    Mono<Void> addDecision(ApprovalRequest updated, Decision decision, ApprovalAuditEntry auditEntry);

    /** The PENDING requests whose current stage lists {@code approverId} and that this approver has not decided on yet. */
    Flux<ApprovalRequest> findPendingFor(UUID organisationId, UUID approverId);

    Mono<Void> updateStatus(UUID id, ApprovalStatus status, ApprovalAuditEntry auditEntry);
}
