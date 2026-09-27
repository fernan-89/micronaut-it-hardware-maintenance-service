package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiatePartRequest;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.model.WorkOrder.Part;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for adding a Part to a WorkOrder (BIAN Behavior Qualifier: {@code part/initiate}). Parts
 * are their own sub-resource (ADR-031), not a free-form string, so a future migration to
 * {@code consumable-inventory} is a storage swap rather than a contract rewrite.
 */
@Singleton
public class InitiatePartUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiatePartUseCase.class);

    private final HashServicePort hashServicePort;
    private final WorkOrderRepository workOrderRepository;

    public InitiatePartUseCase(HashServicePort hashServicePort, WorkOrderRepository workOrderRepository) {
        this.hashServicePort = hashServicePort;
        this.workOrderRepository = workOrderRepository;
    }

    public Mono<Void> execute(UUID id, InitiatePartRequest request, String executor) {
        log.info("[USE CASE] Adding part to WorkOrder ID: {}", id);

        return workOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new WorkOrderNotFoundException(id)))
                .flatMap(workOrder -> hashServicePort.generateSovereignId("workorder-part-creation")
                        .flatMap(partId -> {
                            Part part = new Part(partId, request.description(), request.quantity());
                            var entry = workOrder.addPart(part, executor);
                            return workOrderRepository.addPart(id, part, entry);
                        }));
    }
}
