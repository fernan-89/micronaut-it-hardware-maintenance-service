package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateWorkOrderRequest;
import com.thinklab.application.dto.response.CommentResponse;
import com.thinklab.application.dto.response.ExternalReferenceResponse;
import com.thinklab.application.dto.response.PartResponse;
import com.thinklab.application.dto.response.RmaResponse;
import com.thinklab.application.dto.response.WorkOrderAuditEntryResponse;
import com.thinklab.application.dto.response.WorkOrderResponse;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.model.WorkOrder.Comment;
import com.thinklab.domain.model.WorkOrder.ExternalReference;
import com.thinklab.domain.model.WorkOrder.WorkOrderAuditEntry;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Static factory mapper for WorkOrder DTOs and the Domain aggregate. Enforces the DTO Isolation Pattern. */
public final class WorkOrderMapper {

    private WorkOrderMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static WorkOrder toDomain(InitiateWorkOrderRequest request, UUID sovereignId, UUID organisationId, UUID requesterId, String executor) {
        ExternalReference externalReference = request.externalReferenceSystem() != null && request.externalReferenceId() != null
                ? new ExternalReference(request.externalReferenceSystem(), request.externalReferenceId())
                : null;
        return WorkOrder.createNew(sovereignId, organisationId, request.assetId(), requesterId, request.title(),
                request.symptom(), request.type(), request.location(), externalReference, executor);
    }

    /**
     * @param includeInternalComments {@code false} strips every {@code internal=true} comment - the
     *                                REQUESTER-role scoping rule enforced by the use case, not this mapper.
     */
    public static WorkOrderResponse toResponse(WorkOrder workOrder, boolean includeInternalComments) {
        List<CommentResponse> comments = workOrder.getComments().stream()
                .filter(comment -> includeInternalComments || !comment.internal())
                .map(WorkOrderMapper::toResponse)
                .collect(Collectors.toList());

        return new WorkOrderResponse(
                workOrder.getId(),
                workOrder.getOrganisationId(),
                workOrder.getAssetId(),
                workOrder.getRequesterId(),
                workOrder.getTitle(),
                workOrder.getSymptom(),
                workOrder.getDiagnosis(),
                workOrder.getResolutionCode(),
                workOrder.getType().name(),
                workOrder.getPriority() != null ? workOrder.getPriority().name() : null,
                workOrder.getStatus().name(),
                workOrder.getLocation(),
                workOrder.getLoanerAssetId(),
                workOrder.getParts().stream().map(part -> new PartResponse(part.partId(), part.description(), part.quantity())).collect(Collectors.toList()),
                workOrder.getLaborMinutes(),
                workOrder.getRma() != null ? new RmaResponse(workOrder.getRma().vendorName(), workOrder.getRma().caseNumber(),
                        workOrder.getRma().shippedAt(), workOrder.getRma().returnedAt()) : null,
                workOrder.getExternalReference() != null
                        ? new ExternalReferenceResponse(workOrder.getExternalReference().system(), workOrder.getExternalReference().externalId()) : null,
                comments,
                workOrder.getSlaResponseDueAt(),
                workOrder.getSlaResolutionDueAt(),
                workOrder.getCreatedAt(),
                workOrder.getUpdatedAt()
        );
    }

    private static CommentResponse toResponse(Comment comment) {
        return new CommentResponse(comment.commentId(), comment.author(), comment.text(), comment.internal(), comment.createdAt());
    }

    public static WorkOrderAuditEntryResponse toResponse(WorkOrderAuditEntry entry) {
        return new WorkOrderAuditEntryResponse(
                entry.occurredAt(),
                entry.action(),
                entry.executor(),
                entry.fromStatus() != null ? entry.fromStatus().name() : null,
                entry.toStatus().name(),
                entry.detail()
        );
    }
}
