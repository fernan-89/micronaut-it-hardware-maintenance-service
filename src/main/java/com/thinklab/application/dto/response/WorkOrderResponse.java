package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for WorkOrder output payload (IT Hardware Maintenance Control Record). Enforces the DTO
 * Isolation Pattern by preventing the pure Domain Model from bleeding out to the HTTP boundary.
 *
 * <p>{@code comments} is already scoped by the use case before mapping: a REQUESTER-role caller never
 * sees a comment authored with {@code internal=true}.
 */
@Serdeable
public record WorkOrderResponse(
        UUID id,
        UUID organisationId,
        UUID assetId,
        UUID requesterId,
        String title,
        String symptom,
        String diagnosis,
        String resolutionCode,
        String type,
        String priority,
        String status,
        String location,
        UUID loanerAssetId,
        List<PartResponse> parts,
        int laborMinutes,
        RmaResponse rma,
        ExternalReferenceResponse externalReference,
        List<CommentResponse> comments,
        Instant slaResponseDueAt,
        Instant slaResolutionDueAt,
        Instant createdAt,
        Instant updatedAt
) {}
