package com.thinklab.application.usecase;

import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for {@code control/start} (SCHEDULED -&gt; IN_REPAIR). Publishes
 * {@code repair-started} so the Asset Registry's own event handler moves the affected Asset into
 * MAINTENANCE - see {@link WorkOrderEventPublisher}.
 */
@Singleton
public class StartRepairUseCase {

    private static final Logger log = LoggerFactory.getLogger(StartRepairUseCase.class);

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderEventPublisher eventPublisher;

    public StartRepairUseCase(WorkOrderRepository workOrderRepository, WorkOrderEventPublisher eventPublisher) {
        this.workOrderRepository = workOrderRepository;
        this.eventPublisher = eventPublisher;
    }

    public Mono<Void> execute(UUID id, String executor) {
        log.info("[USE CASE] Starting repair for WorkOrder ID: {}", id);

        return workOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new WorkOrderNotFoundException(id)))
                .flatMap(workOrder -> {
                    var entry = workOrder.start(executor);
                    return workOrderRepository.updateStatus(id, workOrder.getStatus(), entry)
                            .then(Mono.defer(() -> eventPublisher.publishRepairStarted(workOrder)));
                });
    }
}
