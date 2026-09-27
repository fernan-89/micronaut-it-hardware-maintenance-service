package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/** DTO for WorkOrder Vendor Escalation (BIAN Behavior Qualifier: {@code control/escalate}). */
@Serdeable
public record EscalateWorkOrderRequest(
        @NotBlank(message = "Vendor name is required")
        String vendorName,

        @NotBlank(message = "Case number is required")
        String caseNumber
) {}
