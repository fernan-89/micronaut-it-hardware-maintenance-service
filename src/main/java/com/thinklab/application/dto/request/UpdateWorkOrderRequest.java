package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** DTO for WorkOrder Basic Info Update (BIAN Behavior Qualifier: {@code update}). */
@Serdeable
public record UpdateWorkOrderRequest(
        @NotBlank(message = "Title is required")
        String title,

        @NotBlank(message = "Symptom is required")
        String symptom,

        String location,
        UUID loanerAssetId
) {}
