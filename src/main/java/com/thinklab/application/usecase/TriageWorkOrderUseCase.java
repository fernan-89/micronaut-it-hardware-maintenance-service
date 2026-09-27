package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.TriageWorkOrderRequest;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.port.CalendarPort;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Use Case for WorkOrder triage (BIAN Behavior Qualifier: {@code triage}). Computes the SLA due
 * dates from {@link CalendarPort} + the chosen {@link com.thinklab.domain.model.WorkOrder.Priority}
 * (ADR-033) before handing already-resolved {@link Instant}s to the pure domain method.
 */
@Singleton
public class TriageWorkOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(TriageWorkOrderUseCase.class);

    private final WorkOrderRepository workOrderRepository;
    private final CalendarPort calendarPort;

    public TriageWorkOrderUseCase(WorkOrderRepository workOrderRepository, CalendarPort calendarPort) {
        this.workOrderRepository = workOrderRepository;
        this.calendarPort = calendarPort;
    }

    public Mono<Void> execute(UUID id, TriageWorkOrderRequest request, String executor) {
        log.info("[USE CASE] Triaging WorkOrder ID: {} with priority: {}", id, request.priority());

        return workOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new WorkOrderNotFoundException(id)))
                .flatMap(workOrder -> {
                    Instant now = Instant.now();
                    Instant responseDueAt = calendarPort.addBusinessDuration(now, request.priority().responseTarget());
                    Instant resolutionDueAt = calendarPort.addBusinessDuration(now, request.priority().resolutionTarget());
                    var entry = workOrder.triage(request.priority(), responseDueAt, resolutionDueAt, executor);
                    return workOrderRepository.updateTriage(id, request.priority(), responseDueAt, resolutionDueAt, workOrder.getStatus(), entry);
                });
    }
}
