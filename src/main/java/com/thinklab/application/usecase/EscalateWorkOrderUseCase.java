package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.EscalateWorkOrderRequest;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for {@code control/escalate} (TRIAGED -&gt; ESCALATED_TO_VENDOR). */
@Singleton
public class EscalateWorkOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(EscalateWorkOrderUseCase.class);

    private final WorkOrderRepository workOrderRepository;

    public EscalateWorkOrderUseCase(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    public Mono<Void> execute(UUID id, EscalateWorkOrderRequest request, String executor) {
        log.info("[USE CASE] Escalating WorkOrder ID: {} to vendor: {}", id, request.vendorName());

        return workOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new WorkOrderNotFoundException(id)))
                .flatMap(workOrder -> {
                    var entry = workOrder.escalateToVendor(request.vendorName(), request.caseNumber(), executor);
                    return workOrderRepository.updateRma(id, workOrder.getRma(), workOrder.getStatus(), entry);
                });
    }
}
