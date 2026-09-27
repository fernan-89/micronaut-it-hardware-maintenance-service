package com.thinklab.application.dto.request;

import com.thinklab.domain.model.WorkOrder.WorkOrderType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO for WorkOrder Creation Request (BIAN Behavior Qualifier: {@code initiate}).
 *
 * <p>{@code requesterId} is optional in the payload: when the caller is authenticated with role
 * {@code REQUESTER}, the use case ignores it and forces the token-derived {@code X-Executor} instead
 * (a requester can never file a ticket on someone else's behalf) - only an OPERATOR/ADMIN filing on a
 * requester's behalf must supply it.
 */
@Serdeable
public record InitiateWorkOrderRequest(
        @NotNull(message = "Asset ID is required")
        UUID assetId,

        UUID requesterId,

        @NotBlank(message = "Title is required")
        String title,

        @NotBlank(message = "Symptom is required")
        String symptom,

        @NotNull(message = "Type is required")
        WorkOrderType type,

        String location,
        String externalReferenceSystem,
        String externalReferenceId
) {}
