package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ApprovalAuditEntryResponse;
import com.thinklab.application.mapper.ApprovalRequestMapper;
import com.thinklab.domain.exception.ApprovalRequestNotFoundException;
import com.thinklab.domain.repository.ApprovalRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Projects the immutable forensic ledger of an ApprovalRequest (BIAN Behavior Qualifier: {@code audit-log/retrieve}).
 *
 * <p>Returns {@code Mono<List<...>>}, not {@code Flux<...>}: a controller method returning a bare
 * {@code Flux} is streamed rather than collected by Micronaut, which both bypasses the RFC 7807
 * exception handlers and can reorder the emitted elements under JSON streaming serialization (found
 * live on {@code it-hardware-maintenance}'s own audit-log endpoint, Journey 6). Collecting into a
 * list first sidesteps both problems.
 */
@Singleton
public class RetrieveApprovalAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveApprovalAuditLogUseCase.class);

    private final ApprovalRequestRepository requestRepository;

    public RetrieveApprovalAuditLogUseCase(ApprovalRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    public Mono<List<ApprovalAuditEntryResponse>> execute(UUID id) {
        log.info("[USE CASE] Retrieving audit ledger for ApprovalRequest ID: {}", id);

        return requestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApprovalRequestNotFoundException(id)))
                .map(approvalRequest -> approvalRequest.getAuditTrail().stream().map(ApprovalRequestMapper::toResponse).collect(Collectors.toList()));
    }
}
