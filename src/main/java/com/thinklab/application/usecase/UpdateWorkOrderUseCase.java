package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateWorkOrderRequest;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for updating WorkOrder basic info (BIAN Behavior Qualifier: {@code update}). */
@Singleton
public class UpdateWorkOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateWorkOrderUseCase.class);

    private final WorkOrderRepository workOrderRepository;

    public UpdateWorkOrderUseCase(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    public Mono<Void> execute(UUID id, UpdateWorkOrderRequest request, String executor) {
        log.info("[USE CASE] Updating basic info for WorkOrder ID: {}", id);

        return workOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new WorkOrderNotFoundException(id)))
                .flatMap(workOrder -> {
                    var entry = workOrder.updateBasicInfo(request.title(), request.symptom(), request.location(), request.loanerAssetId(), executor);
                    return workOrderRepository.updateBasicInfo(id, request.title(), request.symptom(), request.location(), request.loanerAssetId(), entry);
                });
    }
}
