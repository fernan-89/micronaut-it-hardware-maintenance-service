package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/** DTO projecting one immutable entry of the WorkOrder forensic audit ledger. */
@Serdeable
public record WorkOrderAuditEntryResponse(
        Instant occurredAt,
        String action,
        String executor,
        String fromStatus,
        String toStatus,
        String detail
) {}
