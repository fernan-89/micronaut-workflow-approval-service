package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/** {@code stage} is one-based: the stage of the chain the vote was cast on. */
@Serdeable
public record DecisionResponse(UUID approverId, String outcome, String comment, Instant decidedAt, int stage) {}
