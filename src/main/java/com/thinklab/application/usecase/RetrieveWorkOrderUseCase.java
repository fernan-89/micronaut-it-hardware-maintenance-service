package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.WorkOrderResponse;
import com.thinklab.application.mapper.WorkOrderMapper;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for retrieving a single WorkOrder (BIAN Behavior Qualifier: {@code retrieve}).
 *
 * <p>A {@code REQUESTER}-role caller viewing someone else's ticket gets the same 404 as a truly
 * missing id, not 403 - the platform's "a denied route is indistinguishable from an unknown one"
 * posture (ADR-022) applied here to a single resource instead of a whole route.
 */
@Singleton
public class RetrieveWorkOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveWorkOrderUseCase.class);
    private static final String REQUESTER_ROLE = "REQUESTER";

    private final WorkOrderRepository workOrderRepository;

    public RetrieveWorkOrderUseCase(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    public Mono<WorkOrderResponse> execute(UUID id, String executor, String role) {
        log.info("[USE CASE] Retrieving WorkOrder by ID: {}", id);

        return workOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new WorkOrderNotFoundException(id)))
                .flatMap(workOrder -> authorize(workOrder, id, executor, role))
                .map(workOrder -> WorkOrderMapper.toResponse(workOrder, !REQUESTER_ROLE.equals(role)));
    }

    private static Mono<WorkOrder> authorize(WorkOrder workOrder, UUID id, String executor, String role) {
        if (REQUESTER_ROLE.equals(role) && !workOrder.getRequesterId().equals(UUID.fromString(executor))) {
            return Mono.error(new WorkOrderNotFoundException(id));
        }
        return Mono.just(workOrder);
    }
}
