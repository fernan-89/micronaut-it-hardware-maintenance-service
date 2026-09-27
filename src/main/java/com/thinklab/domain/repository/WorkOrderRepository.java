package com.thinklab.domain.repository;

import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.model.WorkOrder.Comment;
import com.thinklab.domain.model.WorkOrder.Part;
import com.thinklab.domain.model.WorkOrder.Priority;
import com.thinklab.domain.model.WorkOrder.RmaDetails;
import com.thinklab.domain.model.WorkOrder.WorkOrderAuditEntry;
import com.thinklab.domain.model.WorkOrder.WorkOrderStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound Port for WorkOrder persistence operations (IT Hardware Maintenance Service Domain).
 *
 * <p>ARCHITECTURAL RULE: Partial State Mutations (ADR-002, same rule {@code AssetRepository} and
 * {@code SiteRepository} already follow). {@link #create(WorkOrder)} is the only whole-document
 * write; every transition is a granular update that atomically appends its forensic
 * {@link WorkOrderAuditEntry} to the ledger. There is no {@code deleteById} — {@code control/cancel}
 * and {@code control/close} reach a terminal status via {@link #updateStatus}, never a physical
 * deletion.
 */
public interface WorkOrderRepository {

    Mono<WorkOrder> create(WorkOrder workOrder);

    Mono<WorkOrder> findById(UUID id);

    /**
     * Tenant-scoped listing. A {@code requesterId} filter is how the application layer enforces
     * self-service scoping for the {@code REQUESTER} role (only their own tickets) without leaking
     * that concern into this port's contract - it is just another optional filter here.
     */
    Flux<WorkOrder> findAllByOrganisationId(UUID organisationId, WorkOrderStatus status, UUID requesterId);

    Mono<Void> updateBasicInfo(UUID id, String title, String symptom, String location, UUID loanerAssetId, WorkOrderAuditEntry auditEntry);

    /** Covers every status-only transition: schedule, start, await-parts, resume, fail, close, reopen, cancel. */
    Mono<Void> updateStatus(UUID id, WorkOrderStatus status, WorkOrderAuditEntry auditEntry);

    Mono<Void> updateTriage(UUID id, Priority priority, Instant slaResponseDueAt, Instant slaResolutionDueAt,
                             WorkOrderStatus status, WorkOrderAuditEntry auditEntry);

    /** Covers both {@code control/escalate} and {@code control/vendor-returned} - both just set the RMA snapshot. */
    Mono<Void> updateRma(UUID id, RmaDetails rma, WorkOrderStatus status, WorkOrderAuditEntry auditEntry);

    Mono<Void> updateCompleteRepair(UUID id, String diagnosis, int laborMinutes, WorkOrderStatus status, WorkOrderAuditEntry auditEntry);

    Mono<Void> updatePass(UUID id, String resolutionCode, WorkOrderStatus status, WorkOrderAuditEntry auditEntry);

    Mono<Void> addPart(UUID id, Part part, WorkOrderAuditEntry auditEntry);

    Mono<Void> addComment(UUID id, Comment comment, WorkOrderAuditEntry auditEntry);
}
