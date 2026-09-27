package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record RmaResponse(String vendorName, String caseNumber, Instant shippedAt, Instant returnedAt) {}
