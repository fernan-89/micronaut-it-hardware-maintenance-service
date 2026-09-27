package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** DTO completing repair work (BIAN Behavior Qualifier: {@code control/complete-repair}). */
@Serdeable
public record CompleteRepairRequest(
        @NotBlank(message = "Diagnosis is required")
        String diagnosis,

        @Min(value = 0, message = "additionalLaborMinutes cannot be negative")
        int additionalLaborMinutes
) {}
