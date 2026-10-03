package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

/** One stage of an approval chain. */
@Serdeable
public record StageResponse(int requiredApprovals, List<UUID> eligibleApproverIds) {}
