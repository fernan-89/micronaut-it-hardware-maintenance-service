package com.thinklab.application.dto.request;

import com.thinklab.domain.model.WorkOrder.Priority;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

/** DTO for WorkOrder Triage (BIAN Behavior Qualifier: {@code triage}). */
@Serdeable
public record TriageWorkOrderRequest(
        @NotNull(message = "Priority is required")
        Priority priority
) {}
