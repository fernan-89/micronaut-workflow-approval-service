package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.ApprovalRequest;
import com.thinklab.domain.model.ApprovalRequest.ApprovalAuditEntry;
import com.thinklab.domain.model.ApprovalRequest.ApprovalStatus;
import com.thinklab.domain.model.ApprovalRequest.Decision;
import com.thinklab.domain.model.ApprovalRequest.DecisionOutcome;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Infrastructure-specific representation of the ApprovalRequest Aggregate for MongoDB. */
@Introspected
public class ApprovalRequestDocument {

    @BsonId
    private UUID id;

    private UUID organisationId;
    private String subjectType;
    private UUID subjectId;
    private UUID requesterId;
    private UUID policyId;
    private int requiredApprovals;
    private List<UUID> eligibleApproverIds = new ArrayList<>();
    private String status;
    private List<DecisionDocument> decisions = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    public UUID getRequesterId() { return requesterId; }
    public void setRequesterId(UUID requesterId) { this.requesterId = requesterId; }
    public UUID getPolicyId() { return policyId; }
    public void setPolicyId(UUID policyId) { this.policyId = policyId; }
    public int getRequiredApprovals() { return requiredApprovals; }
    public void setRequiredApprovals(int requiredApprovals) { this.requiredApprovals = requiredApprovals; }
    public List<UUID> getEligibleApproverIds() { return eligibleApproverIds; }
    public void setEligibleApproverIds(List<UUID> eligibleApproverIds) { this.eligibleApproverIds = eligibleApproverIds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<DecisionDocument> getDecisions() { return decisions; }
    public void setDecisions(List<DecisionDocument> decisions) { this.decisions = decisions; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    @Introspected
    public record DecisionDocument(UUID approverId, String outcome, String comment, Instant decidedAt) {
        public static DecisionDocument fromDomain(Decision decision) {
            return new DecisionDocument(decision.approverId(), decision.outcome().name(), decision.comment(), decision.decidedAt());
        }

        Decision toDomain() {
            return new Decision(approverId, DecisionOutcome.valueOf(outcome), comment, decidedAt);
        }
    }

    @Introspected
    public record AuditEntryDocument(Instant occurredAt, String action, String executor,
                                      String fromStatus, String toStatus, String detail) {

        public static AuditEntryDocument fromDomain(ApprovalAuditEntry entry) {
            return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(),
                    entry.fromStatus() != null ? entry.fromStatus().name() : null, entry.toStatus().name(), entry.detail());
        }

        ApprovalAuditEntry toDomain() {
            return new ApprovalAuditEntry(occurredAt, action, executor,
                    fromStatus != null ? ApprovalStatus.valueOf(fromStatus) : null, ApprovalStatus.valueOf(toStatus), detail);
        }
    }

    public static final class ApprovalRequestPersistenceMapper {

        private ApprovalRequestPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static ApprovalRequestDocument toDocument(ApprovalRequest request) {
            ApprovalRequestDocument doc = new ApprovalRequestDocument();
            doc.setId(request.getId());
            doc.setOrganisationId(request.getOrganisationId());
            doc.setSubjectType(request.getSubjectType());
            doc.setSubjectId(request.getSubjectId());
            doc.setRequesterId(request.getRequesterId());
            doc.setPolicyId(request.getPolicyId());
            doc.setRequiredApprovals(request.getRequiredApprovals());
            doc.setEligibleApproverIds(new ArrayList<>(request.getEligibleApproverIds()));
            doc.setStatus(request.getStatus().name());
            doc.setDecisions(request.getDecisions().stream().map(DecisionDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            doc.setCreatedAt(request.getCreatedAt());
            doc.setUpdatedAt(request.getUpdatedAt());
            doc.setAuditTrail(request.getAuditTrail().stream().map(AuditEntryDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static ApprovalRequest toDomain(ApprovalRequestDocument doc) {
            ApprovalStatus status = doc.getStatus() != null ? ApprovalStatus.valueOf(doc.getStatus()) : ApprovalStatus.PENDING;
            List<UUID> approvers = doc.getEligibleApproverIds() != null ? doc.getEligibleApproverIds() : new ArrayList<>();
            List<Decision> decisions = doc.getDecisions() != null
                    ? doc.getDecisions().stream().map(DecisionDocument::toDomain).collect(Collectors.toList()) : new ArrayList<>();
            List<ApprovalAuditEntry> trail = doc.getAuditTrail() != null
                    ? doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList()) : new ArrayList<>();

            return ApprovalRequest.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getSubjectType(), doc.getSubjectId(),
                    doc.getRequesterId(), doc.getPolicyId(), doc.getRequiredApprovals(), approvers, status, decisions,
                    doc.getCreatedAt(), doc.getUpdatedAt(), trail);
        }
    }
}
