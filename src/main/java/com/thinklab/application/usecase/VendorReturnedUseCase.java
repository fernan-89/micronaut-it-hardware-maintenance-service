package com.thinklab.application.usecase;

import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for {@code control/vendor-returned} (ESCALATED_TO_VENDOR -&gt; IN_REPAIR). */
@Singleton
public class VendorReturnedUseCase {

    private static final Logger log = LoggerFactory.getLogger(VendorReturnedUseCase.class);

    private final WorkOrderRepository workOrderRepository;

    public VendorReturnedUseCase(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    public Mono<Void> execute(UUID id, String executor) {
        log.info("[USE CASE] Recording vendor return for WorkOrder ID: {}", id);

        return workOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new WorkOrderNotFoundException(id)))
                .flatMap(workOrder -> {
                    var entry = workOrder.vendorReturned(executor);
                    return workOrderRepository.updateRma(id, workOrder.getRma(), workOrder.getStatus(), entry);
                });
    }
}
