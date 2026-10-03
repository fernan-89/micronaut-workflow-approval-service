package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.ApprovalStage;
import io.micronaut.core.annotation.Introspected;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MongoDB representation of one {@link ApprovalStage} (ADR-033). A public top-level class with accessors, like the aggregates'
 * documents, because the driver's POJO codec cannot reach a nested or non-public class.
 */
@Introspected
public class StageDocument {

    private int requiredApprovals;
    private List<UUID> eligibleApproverIds = new ArrayList<>();

    public int getRequiredApprovals() { return requiredApprovals; }
    public void setRequiredApprovals(int requiredApprovals) { this.requiredApprovals = requiredApprovals; }
    public List<UUID> getEligibleApproverIds() { return eligibleApproverIds; }
    public void setEligibleApproverIds(List<UUID> eligibleApproverIds) { this.eligibleApproverIds = eligibleApproverIds; }

    public static StageDocument fromDomain(ApprovalStage stage) {
        StageDocument doc = new StageDocument();
        doc.setRequiredApprovals(stage.requiredApprovals());
        doc.setEligibleApproverIds(new ArrayList<>(stage.eligibleApproverIds()));
        return doc;
    }

    public ApprovalStage toDomain() {
        return new ApprovalStage(requiredApprovals, eligibleApproverIds);
    }

    public static List<StageDocument> fromDomain(List<ApprovalStage> stages) {
        return stages.stream().map(StageDocument::fromDomain).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /** The chain as stored, or {@code null} when there is none (documents written before chains existed). */
    public static List<ApprovalStage> toDomain(List<StageDocument> documents) {
        return documents == null ? null : documents.stream().map(StageDocument::toDomain).toList();
    }
}
