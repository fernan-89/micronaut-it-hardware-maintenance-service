package com.thinklab.application.usecase;

import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.model.WorkOrder.WorkOrderAuditEntry;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case governing every WorkOrder transition that changes status alone - no extra data, no side
 * effect (start/pass are their own use cases: they also publish an outbox event). Mirrors the Asset
 * Registry's {@code ControlAssetUseCase} dispatch-by-enum shape.
 */
@Singleton
public class ControlWorkOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlWorkOrderUseCase.class);

    private final WorkOrderRepository workOrderRepository;

    public ControlWorkOrderUseCase(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    public Mono<Void> execute(UUID id, Action action, String executor) {
        log.info("[USE CASE] Controlling WorkOrder lifecycle: {} for ID: {}", action, id);

        return workOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new WorkOrderNotFoundException(id)))
                .flatMap(workOrder -> {
                    WorkOrderAuditEntry entry = action.apply(workOrder, executor);
                    return workOrderRepository.updateStatus(id, workOrder.getStatus(), entry);
                });
    }

    public enum Action {
        SCHEDULE {
            @Override WorkOrderAuditEntry apply(WorkOrder workOrder, String executor) { return workOrder.schedule(executor); }
        },
        AWAIT_PARTS {
            @Override WorkOrderAuditEntry apply(WorkOrder workOrder, String executor) { return workOrder.awaitParts(executor); }
        },
        RESUME {
            @Override WorkOrderAuditEntry apply(WorkOrder workOrder, String executor) { return workOrder.resume(executor); }
        },
        FAIL {
            @Override WorkOrderAuditEntry apply(WorkOrder workOrder, String executor) { return workOrder.fail(executor); }
        },
        CLOSE {
            @Override WorkOrderAuditEntry apply(WorkOrder workOrder, String executor) { return workOrder.close(executor); }
        },
        REOPEN {
            @Override WorkOrderAuditEntry apply(WorkOrder workOrder, String executor) { return workOrder.reopen(executor); }
        },
        CANCEL {
            @Override WorkOrderAuditEntry apply(WorkOrder workOrder, String executor) { return workOrder.cancel(executor); }
        };

        abstract WorkOrderAuditEntry apply(WorkOrder workOrder, String executor);
    }
}
