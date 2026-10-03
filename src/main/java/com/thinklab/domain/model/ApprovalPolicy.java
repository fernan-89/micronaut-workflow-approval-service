package com.thinklab.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Core Domain Model representing the ApprovalPolicy Aggregate Root (BIAN Service Domain:
 * {@code workflow-approval}).
 *
 * <p><b>Per-tenant approval chain, one place (ADR-030, ADR-033):</b> a named policy (e.g. {@code "CAB"},
 * {@code "ECAB"}) records an ordered list of {@link ApprovalStage}s; each stage says how many distinct approvals it needs and who may
 * cast one. A policy with one stage is the CAB-style single quorum this service started with. Any client Service Domain (starting
 * with {@code it-change-management}) references a policy by id when it files an {@link ApprovalRequest} - this service is the single
 * place the rule lives, never duplicated per client.
 *
 * <p><b>Segregation of duties (ADR-033):</b> the same person cannot be an approver of two stages of one policy, so no single person
 * can satisfy a whole chain.
 *
 * <p>No lifecycle state machine: a policy is configuration, not a workflow instance. It is created and updated like any other
 * reference-data record; no in-flight {@link ApprovalRequest} depends on its policy still existing after creation, since every field
 * it needs is snapshotted at that point.
 *
 * <p>Strictly pure Java. Agnostic of frameworks, databases, or web layers.
 */
public class ApprovalPolicy {

    /** More stages than this is a workflow engine, not an approval chain. */
    public static final int MAX_STAGES = 10;

    private final UUID id;
    private final UUID organisationId;
    private String name;
    private List<ApprovalStage> stages;
    private final Instant createdAt;
    private Instant updatedAt;

    private ApprovalPolicy(UUID id, UUID organisationId, String name, List<ApprovalStage> stages, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organisationId = organisationId;
        this.name = name;
        this.stages = List.copyOf(stages);
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    /** A single-stage policy (the original quorum). */
    public static ApprovalPolicy createNew(UUID id, UUID organisationId, String name, int requiredApprovals, List<UUID> eligibleApproverIds) {
        return createNew(id, organisationId, name, List.of(ApprovalStage.of(requiredApprovals, eligibleApproverIds)));
    }

    public static ApprovalPolicy createNew(UUID id, UUID organisationId, String name, List<ApprovalStage> stages) {
        if (id == null || organisationId == null) {
            throw new IllegalArgumentException("ID and Organisation ID are mandatory for ApprovalPolicy creation.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is mandatory for ApprovalPolicy creation.");
        }
        validateChain(stages);
        Instant now = Instant.now();
        return new ApprovalPolicy(id, organisationId, name, stages, now, now);
    }

    /** Rebuilds a stored policy. Data stored before chains existed has no stages: it becomes the one-stage chain it always was. */
    public static ApprovalPolicy reconstitute(UUID id, UUID organisationId, String name, int requiredApprovals,
                                               List<UUID> eligibleApproverIds, List<ApprovalStage> stages, Instant createdAt, Instant updatedAt) {
        if (id == null || organisationId == null || name == null) {
            throw new IllegalArgumentException("ID, Organisation ID and Name are mandatory to reconstitute an ApprovalPolicy.");
        }
        List<ApprovalStage> chain = stages != null && !stages.isEmpty() ? stages : List.of(new ApprovalStage(requiredApprovals, eligibleApproverIds));
        return new ApprovalPolicy(id, organisationId, name, chain, createdAt, updatedAt);
    }

    /** Behavior Qualifier: {@code update}. Replaces the name and the whole chain; requests already filed keep the chain they were filed with. */
    public void update(String newName, List<ApprovalStage> newStages) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Name cannot be empty.");
        }
        validateChain(newStages);
        this.name = newName;
        this.stages = List.copyOf(newStages);
        this.updatedAt = Instant.now();
    }

    private static void validateChain(List<ApprovalStage> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("At least one stage is required.");
        }
        if (stages.size() > MAX_STAGES) {
            throw new IllegalArgumentException("A policy can have at most " + MAX_STAGES + " stages.");
        }
        Set<UUID> seen = new HashSet<>();
        for (ApprovalStage stage : stages) {
            ApprovalStage.of(stage.requiredApprovals(), stage.eligibleApproverIds());
            for (UUID approver : new HashSet<>(stage.eligibleApproverIds())) {
                if (!seen.add(approver)) {
                    throw new IllegalArgumentException("Segregation of duties: an approver cannot belong to more than one stage.");
                }
            }
        }
    }

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getName() { return name; }
    public List<ApprovalStage> getStages() { return Collections.unmodifiableList(stages); }
    /** The first stage's quorum (the whole quorum of a one-stage policy). */
    public int getRequiredApprovals() { return stages.get(0).requiredApprovals(); }
    /** The first stage's approvers (all of them for a one-stage policy). */
    public List<UUID> getEligibleApproverIds() { return stages.get(0).eligibleApproverIds(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
