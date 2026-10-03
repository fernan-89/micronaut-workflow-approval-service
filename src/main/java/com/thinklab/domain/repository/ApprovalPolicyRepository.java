package com.thinklab.domain.repository;

import com.thinklab.domain.model.ApprovalPolicy;
import com.thinklab.domain.model.ApprovalStage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Outbound Port for ApprovalPolicy persistence operations (Workflow Approval Service Domain).
 *
 * <p>ARCHITECTURAL RULE: Partial State Mutations (ADR-002, same rule every other repository port in
 * the platform follows). {@link #create(ApprovalPolicy)} is the only whole-document write.
 */
public interface ApprovalPolicyRepository {

    Mono<ApprovalPolicy> create(ApprovalPolicy policy);

    Mono<ApprovalPolicy> findById(UUID id);

    /** Tenant-scoped listing, optionally filtered by the policy's own {@code name}. */
    Flux<ApprovalPolicy> findAllByOrganisationId(UUID organisationId, String name);

    /** Replaces the name and the whole chain. The first stage is also kept in the original two fields, so older readers still see a quorum. */
    Mono<Void> updateBasicInfo(UUID id, String name, List<ApprovalStage> stages);
}
