package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.ApprovalPolicy;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Infrastructure-specific representation of the ApprovalPolicy Aggregate for MongoDB. */
@Introspected
public class ApprovalPolicyDocument {

    @BsonId
    private UUID id;

    private UUID organisationId;
    private String name;
    private int requiredApprovals;
    private List<UUID> eligibleApproverIds = new ArrayList<>();
    private List<StageDocument> stages;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getRequiredApprovals() { return requiredApprovals; }
    public void setRequiredApprovals(int requiredApprovals) { this.requiredApprovals = requiredApprovals; }
    public List<UUID> getEligibleApproverIds() { return eligibleApproverIds; }
    public void setEligibleApproverIds(List<UUID> eligibleApproverIds) { this.eligibleApproverIds = eligibleApproverIds; }
    public List<StageDocument> getStages() { return stages; }
    public void setStages(List<StageDocument> stages) { this.stages = stages; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static final class ApprovalPolicyPersistenceMapper {

        private ApprovalPolicyPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static ApprovalPolicyDocument toDocument(ApprovalPolicy policy) {
            ApprovalPolicyDocument doc = new ApprovalPolicyDocument();
            doc.setId(policy.getId());
            doc.setOrganisationId(policy.getOrganisationId());
            doc.setName(policy.getName());
            doc.setRequiredApprovals(policy.getRequiredApprovals());
            doc.setEligibleApproverIds(new ArrayList<>(policy.getEligibleApproverIds()));
            doc.setStages(StageDocument.fromDomain(policy.getStages()));
            doc.setCreatedAt(policy.getCreatedAt());
            doc.setUpdatedAt(policy.getUpdatedAt());
            return doc;
        }

        public static ApprovalPolicy toDomain(ApprovalPolicyDocument doc) {
            List<UUID> approvers = doc.getEligibleApproverIds() != null ? doc.getEligibleApproverIds() : new ArrayList<>();
            return ApprovalPolicy.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getName(),
                    doc.getRequiredApprovals(), approvers, StageDocument.toDomain(doc.getStages()), doc.getCreatedAt(), doc.getUpdatedAt());
        }
    }
}
