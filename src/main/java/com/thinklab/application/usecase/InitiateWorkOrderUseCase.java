package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateWorkOrderRequest;
import com.thinklab.application.dto.response.WorkOrderResponse;
import com.thinklab.application.mapper.WorkOrderMapper;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for filing a new WorkOrder (BIAN Behavior Qualifier: {@code initiate}).
 *
 * <p>Self-service scoping: a caller authenticated as {@code REQUESTER} can only file on their own
 * behalf, so {@code requesterId} is forced to the token-derived {@code X-Executor} regardless of what
 * the request body says. Any other role must supply {@code requesterId} explicitly (staff filing on a
 * user's behalf).
 */
@Singleton
public class InitiateWorkOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateWorkOrderUseCase.class);
    private static final String REQUESTER_ROLE = "REQUESTER";

    private final HashServicePort hashServicePort;
    private final WorkOrderRepository workOrderRepository;

    public InitiateWorkOrderUseCase(HashServicePort hashServicePort, WorkOrderRepository workOrderRepository) {
        this.hashServicePort = hashServicePort;
        this.workOrderRepository = workOrderRepository;
    }

    public Mono<WorkOrderResponse> execute(UUID organisationId, InitiateWorkOrderRequest request, String executor, String role) {
        log.info("[USE CASE] Initiating WorkOrder for organisation: {} asset: {}", organisationId, request.assetId());

        return Mono.fromCallable(() -> resolveRequesterId(request, executor, role))
                .flatMap(requesterId -> hashServicePort.generateSovereignId("workorder-creation")
                        .map(sovereignId -> WorkOrderMapper.toDomain(request, sovereignId, organisationId, requesterId, executor)))
                .flatMap(workOrderRepository::create)
                .map(workOrder -> WorkOrderMapper.toResponse(workOrder, true));
    }

    private static UUID resolveRequesterId(InitiateWorkOrderRequest request, String executor, String role) {
        if (REQUESTER_ROLE.equals(role)) {
            return UUID.fromString(executor);
        }
        if (request.requesterId() == null) {
            throw new IllegalArgumentException("requesterId is required when an operator files a WorkOrder on someone else's behalf.");
        }
        return request.requesterId();
    }
}
