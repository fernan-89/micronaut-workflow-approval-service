package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdatePolicyRequest;
import com.thinklab.application.mapper.ApprovalPolicyMapper;
import com.thinklab.domain.exception.ApprovalPolicyNotFoundException;
import com.thinklab.domain.repository.ApprovalPolicyRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for updating an ApprovalPolicy (BIAN Behavior Qualifier: {@code policy/update}). */
@Singleton
public class UpdatePolicyUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdatePolicyUseCase.class);

    private final ApprovalPolicyRepository policyRepository;

    public UpdatePolicyUseCase(ApprovalPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    public Mono<Void> execute(UUID id, UpdatePolicyRequest request) {
        log.info("[USE CASE] Updating ApprovalPolicy ID: {}", id);

        return policyRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApprovalPolicyNotFoundException(id)))
                .flatMap(policy -> {
                    policy.update(request.name(), ApprovalPolicyMapper.toStages(request.requiredApprovals(), request.eligibleApproverIds(), request.stages()));
                    return policyRepository.updateBasicInfo(id, policy.getName(), policy.getStages());
                });
    }
}
