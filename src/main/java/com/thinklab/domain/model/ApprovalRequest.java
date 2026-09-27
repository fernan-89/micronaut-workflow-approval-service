package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidApprovalRequestStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Core Domain Model representing the ApprovalRequest Aggregate Root (BIAN Service Domain:
 * {@code workflow-approval}).
 *
 * <p><b>Generic by design (ADR-030):</b> this aggregate knows nothing about what it is approving —
 * {@code subjectType} + {@code subjectId} are an opaque reference the calling Service Domain (its
 * first client: {@code it-change-management}) interprets on its own side. Approval is a shared
 * capability across changes, procurement and access requests; hardcoding a domain-specific shape here
 * would be exactly the coupling this service exists to avoid.
 *
 * <p><b>Quorum snapshot (ADR-030):</b> {@code requiredApprovals}/{@code eligibleApproverIds} are
 * copied from the referenced {@link ApprovalPolicy} at creation time, not looked up live on every
 * decision — an in-flight request's rules never shift under it because someone edited the policy.
 *
 * <p><b>Fail-fast veto (ADR-031):</b> a single {@code REJECT} decision resolves the whole request to
 * {@code REJECTED} immediately; reaching {@code requiredApprovals} distinct {@code APPROVE} decisions
 * (with zero rejects) resolves it to {@code APPROVED}. There is no partial-quorum "still open after a
 * reject" state — the simplest defensible CAB semantics for v1.
 *
 * <p>Strictly pure Java. Agnostic of frameworks, databases, or web layers.
 */
public class ApprovalRequest {

    private final UUID id;
    private final UUID organisationId;
    private final String subjectType;
    private final UUID subjectId;
    private final UUID requesterId;
    private final UUID policyId;
    private final int requiredApprovals;
    private final List<UUID> eligibleApproverIds;
    private ApprovalStatus status;
    private final List<Decision> decisions;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<ApprovalAuditEntry> auditTrail;

    private ApprovalRequest(UUID id, UUID organisationId, String subjectType, UUID subjectId, UUID requesterId,
                             UUID policyId, int requiredApprovals, List<UUID> eligibleApproverIds, String executor) {
        this.id = id;
        this.organisationId = organisationId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.requesterId = requesterId;
        this.policyId = policyId;
        this.requiredApprovals = requiredApprovals;
        this.eligibleApproverIds = new ArrayList<>(eligibleApproverIds);
        this.status = ApprovalStatus.PENDING;
        this.decisions = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new ApprovalAuditEntry(this.createdAt, "INITIATED", executor, null, ApprovalStatus.PENDING,
                String.format("Approval requested for %s [%s].", subjectType, subjectId)));
    }

    private ApprovalRequest(UUID id, UUID organisationId, String subjectType, UUID subjectId, UUID requesterId,
                             UUID policyId, int requiredApprovals, List<UUID> eligibleApproverIds, ApprovalStatus status,
                             List<Decision> decisions, Instant createdAt, Instant updatedAt, List<ApprovalAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.requesterId = requesterId;
        this.policyId = policyId;
        this.requiredApprovals = requiredApprovals;
        this.eligibleApproverIds = eligibleApproverIds != null ? new ArrayList<>(eligibleApproverIds) : new ArrayList<>();
        this.status = status != null ? status : ApprovalStatus.PENDING;
        this.decisions = decisions != null ? new ArrayList<>(decisions) : new ArrayList<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    public static ApprovalRequest createNew(UUID id, UUID organisationId, String subjectType, UUID subjectId, UUID requesterId,
                                             UUID policyId, int requiredApprovals, List<UUID> eligibleApproverIds, String executor) {
        if (id == null || organisationId == null || subjectId == null || requesterId == null || policyId == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Subject ID, Requester ID and Policy ID are mandatory for ApprovalRequest creation.");
        }
        if (subjectType == null || subjectType.isBlank()) {
            throw new IllegalArgumentException("Subject Type is mandatory for ApprovalRequest creation.");
        }
        if (eligibleApproverIds == null || eligibleApproverIds.isEmpty()) {
            throw new IllegalArgumentException("At least one eligible approver is required.");
        }
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable ApprovalRequest creation.");
        }
        return new ApprovalRequest(id, organisationId, subjectType, subjectId, requesterId, policyId, requiredApprovals, eligibleApproverIds, executor);
    }

    public static ApprovalRequest reconstitute(UUID id, UUID organisationId, String subjectType, UUID subjectId, UUID requesterId,
                                                UUID policyId, int requiredApprovals, List<UUID> eligibleApproverIds, ApprovalStatus status,
                                                List<Decision> decisions, Instant createdAt, Instant updatedAt, List<ApprovalAuditEntry> auditTrail) {
        if (id == null || organisationId == null || subjectType == null || subjectId == null || requesterId == null || policyId == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Subject Type, Subject ID, Requester ID and Policy ID are mandatory to reconstitute an ApprovalRequest.");
        }
        return new ApprovalRequest(id, organisationId, subjectType, subjectId, requesterId, policyId, requiredApprovals,
                eligibleApproverIds, status, decisions, createdAt, updatedAt, auditTrail);
    }

    /**
     * Behavior Qualifier: {@code decision/capture}. Records one approver's vote and resolves the
     * overall request as soon as the outcome is determined (a reject, or reaching quorum) - the
     * caller reads the post-decision {@link #getStatus()} to react synchronously, no polling or event
     * needed for the common case.
     */
    public ApprovalAuditEntry captureDecision(UUID approverId, DecisionOutcome outcome, String comment, String executor) {
        requireExecutor(executor);
        Objects.requireNonNull(approverId, "approverId is mandatory to capture a decision.");
        Objects.requireNonNull(outcome, "outcome is mandatory to capture a decision.");
        if (status != ApprovalStatus.PENDING) {
            throw new InvalidApprovalRequestStatusException(String.format(
                    "Illegal transition: ApprovalRequest is [%s], expected [PENDING].", status));
        }
        if (!eligibleApproverIds.contains(approverId)) {
            throw new InvalidApprovalRequestStatusException(String.format(
                    "Compliance Violation: approver [%s] is not eligible to decide on this ApprovalRequest.", approverId));
        }
        if (decisions.stream().anyMatch(d -> d.approverId().equals(approverId))) {
            throw new InvalidApprovalRequestStatusException(String.format(
                    "Compliance Violation: approver [%s] has already decided on this ApprovalRequest.", approverId));
        }

        Instant now = Instant.now();
        Decision decision = new Decision(approverId, outcome, comment, now);
        decisions.add(decision);
        ApprovalStatus previous = this.status;
        this.status = resolve();
        this.updatedAt = now;
        ApprovalAuditEntry entry = new ApprovalAuditEntry(now, "DECISION_CAPTURED", executor, previous, this.status,
                String.format("Approver [%s] decided [%s].", approverId, outcome));
        auditTrail.add(entry);
        return entry;
    }

    private ApprovalStatus resolve() {
        if (decisions.stream().anyMatch(d -> d.outcome() == DecisionOutcome.REJECT)) {
            return ApprovalStatus.REJECTED;
        }
        // Past the REJECT check, every decision is APPROVE (DecisionOutcome has only these two values),
        // so counting them directly avoids a filter predicate whose false branch could never be exercised.
        return decisions.size() >= requiredApprovals ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING;
    }

    /** Behavior Qualifier: {@code control/cancel} (terminal, replaces DELETE). Only legal while PENDING. */
    public ApprovalAuditEntry cancel(String executor) {
        requireExecutor(executor);
        if (status != ApprovalStatus.PENDING) {
            throw new InvalidApprovalRequestStatusException(String.format(
                    "Illegal transition: ApprovalRequest is [%s], expected [PENDING].", status));
        }
        ApprovalStatus previous = this.status;
        this.status = ApprovalStatus.CANCELLED;
        this.updatedAt = Instant.now();
        ApprovalAuditEntry entry = new ApprovalAuditEntry(this.updatedAt, "CANCELLED", executor, previous, ApprovalStatus.CANCELLED, "Cancelled by the requester.");
        auditTrail.add(entry);
        return entry;
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable ApprovalRequest mutations.");
        }
    }

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getSubjectType() { return subjectType; }
    public UUID getSubjectId() { return subjectId; }
    public UUID getRequesterId() { return requesterId; }
    public UUID getPolicyId() { return policyId; }
    public int getRequiredApprovals() { return requiredApprovals; }
    public List<UUID> getEligibleApproverIds() { return Collections.unmodifiableList(eligibleApproverIds); }
    public ApprovalStatus getStatus() { return status; }
    public List<Decision> getDecisions() { return Collections.unmodifiableList(decisions); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ApprovalAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    public enum ApprovalStatus { PENDING, APPROVED, REJECTED, CANCELLED }

    public enum DecisionOutcome { APPROVE, REJECT }

    public record Decision(UUID approverId, DecisionOutcome outcome, String comment, Instant decidedAt) {
        public Decision {
            Objects.requireNonNull(approverId, "approverId cannot be null.");
            Objects.requireNonNull(outcome, "outcome cannot be null.");
            decidedAt = decidedAt != null ? decidedAt : Instant.now();
        }
    }

    /**
     * Immutable forensic ledger entry, mirroring the platform's established audit-trail pattern.
     *
     * @param fromStatus status before the action ({@code null} for the initiating entry)
     * @param toStatus   status after the action (equal to {@code fromStatus} when a decision leaves it PENDING)
     */
    public record ApprovalAuditEntry(Instant occurredAt, String action, String executor,
                                      ApprovalStatus fromStatus, ApprovalStatus toStatus, String detail) {}
}
