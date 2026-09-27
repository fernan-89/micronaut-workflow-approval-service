package com.thinklab.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Core Domain Model representing the ApprovalPolicy Aggregate Root (BIAN Service Domain:
 * {@code workflow-approval}).
 *
 * <p><b>Per-tenant CAB-style quorum, one place (ADR-030):</b> a named policy (e.g. {@code "CAB"},
 * {@code "ECAB"}) records how many distinct approvals are required and who may cast one. Any client
 * Service Domain (starting with {@code it-change-management}) references a policy by id when it
 * files an {@link ApprovalRequest} — this service is the single place the quorum rule lives, never
 * duplicated per client.
 *
 * <p>No lifecycle state machine: a policy is configuration, not a workflow instance. It is created
 * and updated like any other reference-data record; retiring one is a {@code name} an organisation
 * simply stops referencing, not a status transition (no in-flight {@link ApprovalRequest} depends on
 * its policy still existing after creation, since every field it needs is snapshotted at that point).
 *
 * <p>Strictly pure Java. Agnostic of frameworks, databases, or web layers.
 */
public class ApprovalPolicy {

    private final UUID id;
    private final UUID organisationId;
    private String name;
    private int requiredApprovals;
    private List<UUID> eligibleApproverIds;
    private final Instant createdAt;
    private Instant updatedAt;

    private ApprovalPolicy(UUID id, UUID organisationId, String name, int requiredApprovals, List<UUID> eligibleApproverIds,
                            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organisationId = organisationId;
        this.name = name;
        this.requiredApprovals = requiredApprovals;
        this.eligibleApproverIds = copy(eligibleApproverIds);
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public static ApprovalPolicy createNew(UUID id, UUID organisationId, String name, int requiredApprovals, List<UUID> eligibleApproverIds) {
        if (id == null || organisationId == null) {
            throw new IllegalArgumentException("ID and Organisation ID are mandatory for ApprovalPolicy creation.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is mandatory for ApprovalPolicy creation.");
        }
        validateQuorum(requiredApprovals, eligibleApproverIds);
        Instant now = Instant.now();
        return new ApprovalPolicy(id, organisationId, name, requiredApprovals, eligibleApproverIds, now, now);
    }

    public static ApprovalPolicy reconstitute(UUID id, UUID organisationId, String name, int requiredApprovals,
                                               List<UUID> eligibleApproverIds, Instant createdAt, Instant updatedAt) {
        if (id == null || organisationId == null || name == null) {
            throw new IllegalArgumentException("ID, Organisation ID and Name are mandatory to reconstitute an ApprovalPolicy.");
        }
        return new ApprovalPolicy(id, organisationId, name, requiredApprovals, eligibleApproverIds, createdAt, updatedAt);
    }

    /** Behavior Qualifier: {@code update}. */
    public void update(String newName, int newRequiredApprovals, List<UUID> newEligibleApproverIds) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Name cannot be empty.");
        }
        validateQuorum(newRequiredApprovals, newEligibleApproverIds);
        this.name = newName;
        this.requiredApprovals = newRequiredApprovals;
        this.eligibleApproverIds = copy(newEligibleApproverIds);
        this.updatedAt = Instant.now();
    }

    private static void validateQuorum(int requiredApprovals, List<UUID> eligibleApproverIds) {
        if (requiredApprovals <= 0) {
            throw new IllegalArgumentException("requiredApprovals must be positive.");
        }
        if (eligibleApproverIds == null || eligibleApproverIds.isEmpty()) {
            throw new IllegalArgumentException("At least one eligible approver is required.");
        }
        if (requiredApprovals > eligibleApproverIds.size()) {
            throw new IllegalArgumentException("requiredApprovals cannot exceed the number of eligible approvers.");
        }
    }

    private static List<UUID> copy(List<UUID> list) {
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getName() { return name; }
    public int getRequiredApprovals() { return requiredApprovals; }
    public List<UUID> getEligibleApproverIds() { return Collections.unmodifiableList(eligibleApproverIds); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
