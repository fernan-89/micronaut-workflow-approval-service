package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ApprovalRequestResponse;
import com.thinklab.application.mapper.ApprovalRequestMapper;
import com.thinklab.domain.exception.ApprovalRequestNotFoundException;
import com.thinklab.domain.repository.ApprovalRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for retrieving a single ApprovalRequest (BIAN Behavior Qualifier: {@code retrieve}). */
@Singleton
public class RetrieveApprovalRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveApprovalRequestUseCase.class);

    private final ApprovalRequestRepository requestRepository;

    public RetrieveApprovalRequestUseCase(ApprovalRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    public Mono<ApprovalRequestResponse> execute(UUID id) {
        log.info("[USE CASE] Retrieving ApprovalRequest by ID: {}", id);

        return requestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApprovalRequestNotFoundException(id)))
                .map(ApprovalRequestMapper::toResponse);
    }
}
