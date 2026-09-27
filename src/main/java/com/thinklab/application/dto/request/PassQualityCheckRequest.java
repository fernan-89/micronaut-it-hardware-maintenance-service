package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/** DTO resolving a WorkOrder after a passed quality check (BIAN Behavior Qualifier: {@code control/pass}). */
@Serdeable
public record PassQualityCheckRequest(
        @NotBlank(message = "Resolution code is required")
        String resolutionCode
) {}
