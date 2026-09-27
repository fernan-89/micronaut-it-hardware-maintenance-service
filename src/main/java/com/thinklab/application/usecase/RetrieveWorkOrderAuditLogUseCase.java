package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.WorkOrderAuditEntryResponse;
import com.thinklab.application.mapper.WorkOrderMapper;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Projects the immutable forensic ledger of a WorkOrder (BIAN Behavior Qualifier: {@code audit-log/retrieve}).
 *
 * <p>Returns {@code Mono<List<...>>}, not {@code Flux<...>}: a controller method returning a bare
 * {@code Flux} is streamed by Micronaut rather than collected, which both bypasses the RFC 7807
 * exception handlers (an established platform gotcha) and, found live building this endpoint,
 * can reorder the emitted elements relative to their list order under JSON streaming
 * serialization - the ledger came back with entries out of sequence even though the underlying
 * MongoDB array was correctly ordered. Collecting into a list first sidesteps both problems.
 */
@Singleton
public class RetrieveWorkOrderAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveWorkOrderAuditLogUseCase.class);
    private static final String REQUESTER_ROLE = "REQUESTER";

    private final WorkOrderRepository workOrderRepository;

    public RetrieveWorkOrderAuditLogUseCase(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    public Mono<List<WorkOrderAuditEntryResponse>> execute(UUID id, String executor, String role) {
        log.info("[USE CASE] Retrieving audit ledger for WorkOrder ID: {}", id);

        return workOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new WorkOrderNotFoundException(id)))
                .flatMap(workOrder -> authorize(workOrder, id, executor, role))
                .map(workOrder -> workOrder.getAuditTrail().stream().map(WorkOrderMapper::toResponse).collect(Collectors.toList()));
    }

    private static Mono<WorkOrder> authorize(WorkOrder workOrder, UUID id, String executor, String role) {
        if (REQUESTER_ROLE.equals(role) && !workOrder.getRequesterId().equals(UUID.fromString(executor))) {
            return Mono.error(new WorkOrderNotFoundException(id));
        }
        return Mono.just(workOrder);
    }
}
