package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CompleteRepairRequest;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for {@code control/complete-repair} (IN_REPAIR -&gt; QUALITY_CHECK). */
@Singleton
public class CompleteRepairUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompleteRepairUseCase.class);

    private final WorkOrderRepository workOrderRepository;

    public CompleteRepairUseCase(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    public Mono<Void> execute(UUID id, CompleteRepairRequest request, String executor) {
        log.info("[USE CASE] Completing repair for WorkOrder ID: {}", id);

        return workOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new WorkOrderNotFoundException(id)))
                .flatMap(workOrder -> {
                    var entry = workOrder.completeRepair(request.diagnosis(), request.additionalLaborMinutes(), executor);
                    return workOrderRepository.updateCompleteRepair(id, workOrder.getDiagnosis(), workOrder.getLaborMinutes(), workOrder.getStatus(), entry);
                });
    }
}
