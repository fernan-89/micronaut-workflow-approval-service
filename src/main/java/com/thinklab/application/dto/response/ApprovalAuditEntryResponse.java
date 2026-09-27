package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/** DTO projecting one immutable entry of the ApprovalRequest forensic audit ledger. */
@Serdeable
public record ApprovalAuditEntryResponse(
        Instant occurredAt,
        String action,
        String executor,
        String fromStatus,
        String toStatus,
        String detail
) {}
