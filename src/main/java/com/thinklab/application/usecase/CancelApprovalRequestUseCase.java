package com.thinklab.application.usecase;

import com.thinklab.domain.exception.ApprovalRequestNotFoundException;
import com.thinklab.domain.repository.ApprovalRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for {@code control/cancel} (terminal, replaces DELETE). Only legal while PENDING. */
@Singleton
public class CancelApprovalRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelApprovalRequestUseCase.class);

    private final ApprovalRequestRepository requestRepository;

    public CancelApprovalRequestUseCase(ApprovalRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    public Mono<Void> execute(UUID id, String executor) {
        log.info("[USE CASE] Cancelling ApprovalRequest ID: {}", id);

        return requestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApprovalRequestNotFoundException(id)))
                .flatMap(approvalRequest -> {
                    var entry = approvalRequest.cancel(executor);
                    return requestRepository.updateStatus(id, approvalRequest.getStatus(), entry);
                });
    }
}
