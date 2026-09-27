package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** DTO for adding a Part to a WorkOrder (BIAN Behavior Qualifier: {@code part/initiate}). */
@Serdeable
public record InitiatePartRequest(
        @NotBlank(message = "Description is required")
        String description,

        @Positive(message = "Quantity must be positive")
        int quantity
) {}
