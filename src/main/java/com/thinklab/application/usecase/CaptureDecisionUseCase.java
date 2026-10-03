package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CaptureDecisionRequest;
import com.thinklab.application.dto.response.ApprovalRequestResponse;
import com.thinklab.application.mapper.ApprovalRequestMapper;
import com.thinklab.domain.exception.ApprovalRequestNotFoundException;
import com.thinklab.domain.model.ApprovalRequest.Decision;
import com.thinklab.domain.repository.ApprovalRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for capturing one approver's decision (BIAN Behavior Qualifier: {@code decision/capture}).
 * Returns the ApprovalRequest's post-decision state so a synchronous caller (GMUD's own
 * {@code approval/capture}) can react to the resolved outcome in the same request/response cycle.
 */
@Singleton
public class CaptureDecisionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CaptureDecisionUseCase.class);

    private final ApprovalRequestRepository requestRepository;

    public CaptureDecisionUseCase(ApprovalRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    public Mono<ApprovalRequestResponse> execute(UUID id, UUID approverId, CaptureDecisionRequest request, String executor) {
        log.info("[USE CASE] Capturing decision [{}] from approver [{}] for ApprovalRequest ID: {}", request.outcome(), approverId, id);

        return requestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApprovalRequestNotFoundException(id)))
                .flatMap(approvalRequest -> {
                    var entry = approvalRequest.captureDecision(approverId, request.outcome(), request.comment(), executor);
                    Decision decision = approvalRequest.getDecisions().get(approvalRequest.getDecisions().size() - 1);
                    return requestRepository.addDecision(approvalRequest, decision, entry)
                            .thenReturn(ApprovalRequestMapper.toResponse(approvalRequest));
                });
    }
}
