package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.CaptureDecisionRequest;
import com.thinklab.application.dto.request.InitiateApprovalRequestRequest;
import com.thinklab.application.dto.request.InitiatePolicyRequest;
import com.thinklab.application.dto.request.UpdatePolicyRequest;
import com.thinklab.application.dto.response.ApprovalAuditEntryResponse;
import com.thinklab.application.dto.response.ApprovalPolicyResponse;
import com.thinklab.application.dto.response.ApprovalRequestResponse;
import com.thinklab.application.usecase.CancelApprovalRequestUseCase;
import com.thinklab.application.usecase.CaptureDecisionUseCase;
import com.thinklab.application.usecase.InitiateApprovalRequestUseCase;
import com.thinklab.application.usecase.InitiatePolicyUseCase;
import com.thinklab.application.usecase.RetrieveApprovalAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveApprovalRequestUseCase;
import com.thinklab.application.usecase.RetrieveApprovalRequestsUseCase;
import com.thinklab.application.usecase.RetrievePoliciesUseCase;
import com.thinklab.application.usecase.RetrievePolicyUseCase;
import com.thinklab.application.usecase.UpdatePolicyUseCase;
import com.thinklab.domain.model.ApprovalRequest.ApprovalStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code workflow-approval} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.ApprovalRequest}
 * is the Control Record; {@link com.thinklab.domain.model.ApprovalPolicy} is tenant configuration,
 * addressed under its own {@code policy/...} prefix rather than nested under a request (a policy
 * outlives any single request that references it). There is no {@code DELETE}: {@code control/cancel}
 * is a terminal, soft status transition.
 */
@Controller("/workflow-approval/v1")
public class WorkflowApprovalController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowApprovalController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";

    private final InitiatePolicyUseCase initiatePolicyUseCase;
    private final RetrievePolicyUseCase retrievePolicyUseCase;
    private final RetrievePoliciesUseCase retrievePoliciesUseCase;
    private final UpdatePolicyUseCase updatePolicyUseCase;
    private final InitiateApprovalRequestUseCase initiateApprovalRequestUseCase;
    private final RetrieveApprovalRequestUseCase retrieveApprovalRequestUseCase;
    private final RetrieveApprovalRequestsUseCase retrieveApprovalRequestsUseCase;
    private final CaptureDecisionUseCase captureDecisionUseCase;
    private final CancelApprovalRequestUseCase cancelApprovalRequestUseCase;
    private final RetrieveApprovalAuditLogUseCase retrieveApprovalAuditLogUseCase;

    public WorkflowApprovalController(
            InitiatePolicyUseCase initiatePolicyUseCase,
            RetrievePolicyUseCase retrievePolicyUseCase,
            RetrievePoliciesUseCase retrievePoliciesUseCase,
            UpdatePolicyUseCase updatePolicyUseCase,
            InitiateApprovalRequestUseCase initiateApprovalRequestUseCase,
            RetrieveApprovalRequestUseCase retrieveApprovalRequestUseCase,
            RetrieveApprovalRequestsUseCase retrieveApprovalRequestsUseCase,
            CaptureDecisionUseCase captureDecisionUseCase,
            CancelApprovalRequestUseCase cancelApprovalRequestUseCase,
            RetrieveApprovalAuditLogUseCase retrieveApprovalAuditLogUseCase
    ) {
        this.initiatePolicyUseCase = initiatePolicyUseCase;
        this.retrievePolicyUseCase = retrievePolicyUseCase;
        this.retrievePoliciesUseCase = retrievePoliciesUseCase;
        this.updatePolicyUseCase = updatePolicyUseCase;
        this.initiateApprovalRequestUseCase = initiateApprovalRequestUseCase;
        this.retrieveApprovalRequestUseCase = retrieveApprovalRequestUseCase;
        this.retrieveApprovalRequestsUseCase = retrieveApprovalRequestsUseCase;
        this.captureDecisionUseCase = captureDecisionUseCase;
        this.cancelApprovalRequestUseCase = cancelApprovalRequestUseCase;
        this.retrieveApprovalAuditLogUseCase = retrieveApprovalAuditLogUseCase;
    }

    /** Behavior Qualifier: {@code policy/initiate}. */
    @Post("/policy/initiate")
    public Mono<HttpResponse<ApprovalPolicyResponse>> initiatePolicy(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Body @Valid InitiatePolicyRequest request
    ) {
        log.info("[ACTION: INITIATE_POLICY] Received request to create policy '{}' for organisation: {}", request.name(), tenantId);

        return initiatePolicyUseCase.execute(UUID.fromString(tenantId), request).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code policy/retrieve}. */
    @Get("/policy/{id}/retrieve")
    public Mono<HttpResponse<ApprovalPolicyResponse>> retrievePolicyById(@PathVariable UUID id) {
        return retrievePolicyUseCase.execute(id).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code policy/retrieve} (collection). Filterable by the policy's own {@code name} (e.g. "CAB"). */
    @Get("/policy/retrieve")
    public Mono<List<ApprovalPolicyResponse>> retrievePolicies(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @QueryValue @Nullable String name
    ) {
        return Mono.defer(() -> retrievePoliciesUseCase.execute(UUID.fromString(tenantId), name).collectList());
    }

    /** Behavior Qualifier: {@code policy/update}. */
    @Put("/policy/{id}/update")
    public Mono<HttpResponse<Void>> updatePolicy(@PathVariable UUID id, @Body @Valid UpdatePolicyRequest request) {
        return updatePolicyUseCase.execute(id, request).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code initiate}. Files a new ApprovalRequest against a policy. */
    @Post("/initiate")
    public Mono<HttpResponse<ApprovalRequestResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid InitiateApprovalRequestRequest request
    ) {
        log.info("[ACTION: INITIATE_APPROVAL_REQUEST] [EXECUTOR: {}] Received request for organisation: {} subject: {}/{}",
                executor, tenantId, request.subjectType(), request.subjectId());

        return initiateApprovalRequestUseCase.execute(UUID.fromString(tenantId), request, executor).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<ApprovalRequestResponse>> retrieveById(@PathVariable UUID id) {
        return retrieveApprovalRequestUseCase.execute(id).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection). Filterable by {@code subjectType}/{@code subjectId}/{@code status}. */
    @Get("/retrieve")
    public Mono<List<ApprovalRequestResponse>> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @QueryValue @Nullable String subjectType,
            @QueryValue @Nullable UUID subjectId,
            @QueryValue @Nullable ApprovalStatus status
    ) {
        return Mono.defer(() -> retrieveApprovalRequestsUseCase.execute(UUID.fromString(tenantId), subjectType, subjectId, status).collectList());
    }

    /** Behavior Qualifier: {@code decision/capture}. One approver's vote; resolves the request synchronously when quorum/veto is reached. */
    @Put("/{id}/decision/capture")
    public Mono<HttpResponse<ApprovalRequestResponse>> captureDecision(
            @PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Body @Valid CaptureDecisionRequest request
    ) {
        log.info("[ACTION: CAPTURE_DECISION] [EXECUTOR: {}] decision for ApprovalRequest ID: {}", executor, id);

        return captureDecisionUseCase.execute(id, UUID.fromString(executor), request, executor).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code control/cancel}. Terminal, replaces DELETE. */
    @Put("/{id}/control/cancel")
    public Mono<HttpResponse<Void>> controlCancel(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return cancelApprovalRequestUseCase.execute(id, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code audit-log/retrieve}. Immutable forensic ledger of the ApprovalRequest. */
    @Get("/{id}/audit-log/retrieve")
    public Mono<List<ApprovalAuditEntryResponse>> retrieveAuditLog(@PathVariable UUID id) {
        return retrieveApprovalAuditLogUseCase.execute(id);
    }
}
