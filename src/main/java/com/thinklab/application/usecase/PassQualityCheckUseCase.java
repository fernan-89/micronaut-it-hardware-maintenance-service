package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.PassQualityCheckRequest;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for {@code control/pass} (QUALITY_CHECK -&gt; RESOLVED). Publishes {@code repair-completed}
 * as soon as the repair is verified - the event that moves the affected Asset back to service - rather
 * than waiting for the separate, purely administrative {@code control/close} step (ADR-034).
 */
@Singleton
public class PassQualityCheckUseCase {

    private static final Logger log = LoggerFactory.getLogger(PassQualityCheckUseCase.class);

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderEventPublisher eventPublisher;

    public PassQualityCheckUseCase(WorkOrderRepository workOrderRepository, WorkOrderEventPublisher eventPublisher) {
        this.workOrderRepository = workOrderRepository;
        this.eventPublisher = eventPublisher;
    }

    public Mono<Void> execute(UUID id, PassQualityCheckRequest request, String executor) {
        log.info("[USE CASE] Recording a passed quality check for WorkOrder ID: {}", id);

        return workOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new WorkOrderNotFoundException(id)))
                .flatMap(workOrder -> {
                    var entry = workOrder.pass(request.resolutionCode(), executor);
                    return workOrderRepository.updatePass(id, workOrder.getResolutionCode(), workOrder.getStatus(), entry)
                            .then(Mono.defer(() -> eventPublisher.publishRepairCompleted(workOrder)));
                });
    }
}
