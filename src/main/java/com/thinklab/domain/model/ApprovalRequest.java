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
 * <p><b>Chain snapshot (ADR-030, ADR-033):</b> the {@link ApprovalStage}s are copied from the referenced
 * {@link ApprovalPolicy} at creation time, not looked up live on every decision — an in-flight request's rules never shift under it
 * because someone edited the policy. The request waits on one stage at a time ({@link #getCurrentStage()}); when that stage reaches
 * its quorum the request moves to the next, and the whole request is {@code APPROVED} when the last stage does.
 *
 * <p><b>Fail-fast veto (ADR-031):</b> a single {@code REJECT} decision, at any stage, resolves the whole request to
 * {@code REJECTED} immediately. There is no partial-quorum "still open after a reject" state and no stepping back to an earlier
 * stage - the simplest defensible CAB semantics.
 *
 * <p><b>One decision per person (ADR-033):</b> an approver can decide once on a request, across every stage.
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
    private final List<ApprovalStage> stages;
    private int currentStage;
    private ApprovalStatus status;
    private final List<Decision> decisions;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<ApprovalAuditEntry> auditTrail;

    private ApprovalRequest(UUID id, UUID organisationId, String subjectType, UUID subjectId, UUID requesterId,
                             UUID policyId, List<ApprovalStage> stages, String executor) {
        this.id = id;
        this.organisationId = organisationId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.requesterId = requesterId;
        this.policyId = policyId;
        this.stages = List.copyOf(stages);
        this.currentStage = 0;
        this.status = ApprovalStatus.PENDING;
        this.decisions = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new ApprovalAuditEntry(this.createdAt, "INITIATED", executor, null, ApprovalStatus.PENDING,
                String.format("Approval requested for %s [%s].", subjectType, subjectId)));
    }

    private ApprovalRequest(UUID id, UUID organisationId, String subjectType, UUID subjectId, UUID requesterId,
                             UUID policyId, List<ApprovalStage> stages, int currentStage, ApprovalStatus status,
                             List<Decision> decisions, Instant createdAt, Instant updatedAt, List<ApprovalAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.requesterId = requesterId;
        this.policyId = policyId;
        this.stages = List.copyOf(stages);
        this.currentStage = currentStage;
        this.status = status != null ? status : ApprovalStatus.PENDING;
        this.decisions = decisions != null ? new ArrayList<>(decisions) : new ArrayList<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    /** A request with a single stage (the original quorum). */
    public static ApprovalRequest createNew(UUID id, UUID organisationId, String subjectType, UUID subjectId, UUID requesterId,
                                             UUID policyId, int requiredApprovals, List<UUID> eligibleApproverIds, String executor) {
        if (eligibleApproverIds == null || eligibleApproverIds.isEmpty()) {
            throw new IllegalArgumentException("At least one eligible approver is required.");
        }
        return createNew(id, organisationId, subjectType, subjectId, requesterId, policyId,
                List.of(new ApprovalStage(requiredApprovals, eligibleApproverIds)), executor);
    }

    public static ApprovalRequest createNew(UUID id, UUID organisationId, String subjectType, UUID subjectId, UUID requesterId,
                                             UUID policyId, List<ApprovalStage> stages, String executor) {
        if (id == null || organisationId == null || subjectId == null || requesterId == null || policyId == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Subject ID, Requester ID and Policy ID are mandatory for ApprovalRequest creation.");
        }
        if (subjectType == null || subjectType.isBlank()) {
            throw new IllegalArgumentException("Subject Type is mandatory for ApprovalRequest creation.");
        }
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("At least one stage is required.");
        }
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable ApprovalRequest creation.");
        }
        return new ApprovalRequest(id, organisationId, subjectType, subjectId, requesterId, policyId, stages, executor);
    }

    /**
     * Rebuilds a stored request. Data stored before chains existed has no stages: it becomes the one-stage chain it always was
     * (from {@code requiredApprovals} and {@code eligibleApproverIds}, which then still describe that one stage).
     */
    public static ApprovalRequest reconstitute(UUID id, UUID organisationId, String subjectType, UUID subjectId, UUID requesterId,
                                                UUID policyId, int requiredApprovals, List<UUID> eligibleApproverIds,
                                                List<ApprovalStage> stages, int currentStage, ApprovalStatus status,
                                                List<Decision> decisions, Instant createdAt, Instant updatedAt, List<ApprovalAuditEntry> auditTrail) {
        if (id == null || organisationId == null || subjectType == null || subjectId == null || requesterId == null || policyId == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Subject Type, Subject ID, Requester ID and Policy ID are mandatory to reconstitute an ApprovalRequest.");
        }
        List<ApprovalStage> chain = stages != null && !stages.isEmpty() ? stages : List.of(new ApprovalStage(requiredApprovals, eligibleApproverIds));
        return new ApprovalRequest(id, organisationId, subjectType, subjectId, requesterId, policyId, chain, currentStage, status,
                decisions, createdAt, updatedAt, auditTrail);
    }

    /**
     * Behavior Qualifier: {@code decision/capture}. Records one approver's vote on the stage the request is waiting on and resolves
     * as soon as the outcome is determined (a reject, or the last stage reaching quorum) - the caller reads the post-decision
     * {@link #getStatus()} to react synchronously, no polling or event needed for the common case. A decision that completes a
     * stage that is not the last leaves the request PENDING, now waiting on the next stage.
     */
    public ApprovalAuditEntry captureDecision(UUID approverId, DecisionOutcome outcome, String comment, String executor) {
        requireExecutor(executor);
        Objects.requireNonNull(approverId, "approverId is mandatory to capture a decision.");
        Objects.requireNonNull(outcome, "outcome is mandatory to capture a decision.");
        if (outcome == DecisionOutcome.RETURN && (comment == null || comment.isBlank())) {
            throw new IllegalArgumentException("A comment saying what to fix is mandatory to return an ApprovalRequest.");
        }
        if (status != ApprovalStatus.PENDING) {
            throw new InvalidApprovalRequestStatusException(String.format(
                    "Illegal transition: ApprovalRequest is [%s], expected [PENDING].", status));
        }
        if (!getEligibleApproverIds().contains(approverId)) {
            throw new InvalidApprovalRequestStatusException(String.format(
                    "Compliance Violation: approver [%s] is not eligible to decide on this ApprovalRequest.", approverId));
        }
        if (decisions.stream().anyMatch(d -> d.approverId().equals(approverId))) {
            throw new InvalidApprovalRequestStatusException(String.format(
                    "Compliance Violation: approver [%s] has already decided on this ApprovalRequest.", approverId));
        }

        Instant now = Instant.now();
        decisions.add(new Decision(approverId, outcome, comment, now, currentStage));
        ApprovalStatus previous = this.status;
        String detail = String.format("Approver [%s] decided [%s].", approverId, outcome);
        if (outcome == DecisionOutcome.REJECT) {
            this.status = ApprovalStatus.REJECTED;
        } else if (outcome == DecisionOutcome.RETURN) {
            this.status = ApprovalStatus.RETURNED;
        } else if (decisionsOnCurrentStage() >= stages.get(currentStage).requiredApprovals()) {
            if (currentStage == stages.size() - 1) {
                this.status = ApprovalStatus.APPROVED;
            } else {
                currentStage++;
                detail += String.format(" Stage %d of %d complete; now waiting on stage %d.", currentStage, stages.size(), currentStage + 1);
            }
        }
        this.updatedAt = now;
        ApprovalAuditEntry entry = new ApprovalAuditEntry(now, "DECISION_CAPTURED", executor, previous, this.status, detail);
        auditTrail.add(entry);
        return entry;
    }

    /** Only APPROVE decisions can be on a stage that is still open (a REJECT ends the request), so every decision counts. */
    private long decisionsOnCurrentStage() {
        return decisions.stream().filter(d -> d.stage() == currentStage).count();
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
    public List<ApprovalStage> getStages() { return Collections.unmodifiableList(stages); }
    /** Zero-based index of the stage the request is waiting on (the last one once it is resolved by that stage). */
    public int getCurrentStage() { return currentStage; }
    /** The quorum of the stage the request is waiting on. */
    public int getRequiredApprovals() { return stages.get(currentStage).requiredApprovals(); }
    /** Who may decide now: the approvers of the stage the request is waiting on. */
    public List<UUID> getEligibleApproverIds() { return stages.get(currentStage).eligibleApproverIds(); }
    public ApprovalStatus getStatus() { return status; }
    public List<Decision> getDecisions() { return Collections.unmodifiableList(decisions); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ApprovalAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    public enum ApprovalStatus { PENDING, APPROVED, REJECTED, RETURNED, CANCELLED }

    public enum DecisionOutcome { APPROVE, REJECT, RETURN }

    /** One vote; {@code stage} is the zero-based stage it was cast on. */
    public record Decision(UUID approverId, DecisionOutcome outcome, String comment, Instant decidedAt, int stage) {
        public Decision {
            Objects.requireNonNull(approverId, "approverId cannot be null.");
            Objects.requireNonNull(outcome, "outcome cannot be null.");
            decidedAt = decidedAt != null ? decidedAt : Instant.now();
        }

        /** A vote on the first stage (every vote of a one-stage request, and every vote stored before chains existed). */
        public Decision(UUID approverId, DecisionOutcome outcome, String comment, Instant decidedAt) {
            this(approverId, outcome, comment, decidedAt, 0);
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
