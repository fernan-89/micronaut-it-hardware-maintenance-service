package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.WorkOrderAuditEntryResponse;
import com.thinklab.application.mapper.WorkOrderMapper;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Projects the immutable forensic ledger of a WorkOrder (BIAN Behavior Qualifier: {@code audit-log/retrieve}). */
@Singleton
public class RetrieveWorkOrderAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveWorkOrderAuditLogUseCase.class);
    private static final String REQUESTER_ROLE = "REQUESTER";

    private final WorkOrderRepository workOrderRepository;

    public RetrieveWorkOrderAuditLogUseCase(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    public Flux<WorkOrderAuditEntryResponse> execute(UUID id, String executor, String role) {
        log.info("[USE CASE] Retrieving audit ledger for WorkOrder ID: {}", id);

        return workOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new WorkOrderNotFoundException(id)))
                .flatMap(workOrder -> authorize(workOrder, id, executor, role))
                .flatMapMany(workOrder -> Flux.fromIterable(workOrder.getAuditTrail()))
                .map(WorkOrderMapper::toResponse);
    }

    private static Mono<WorkOrder> authorize(WorkOrder workOrder, UUID id, String executor, String role) {
        if (REQUESTER_ROLE.equals(role) && !workOrder.getRequesterId().equals(UUID.fromString(executor))) {
            return Mono.error(new WorkOrderNotFoundException(id));
        }
        return Mono.just(workOrder);
    }
}
