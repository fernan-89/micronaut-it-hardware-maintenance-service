package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.WorkOrderResponse;
import com.thinklab.application.mapper.WorkOrderMapper;
import com.thinklab.domain.model.WorkOrder.WorkOrderStatus;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Use Case for the tenant-scoped WorkOrder collection (BIAN Behavior Qualifier: {@code retrieve}).
 *
 * <p>A {@code REQUESTER}-role caller is always additionally filtered to their own tickets - the
 * repository port already has a {@code requesterId} filter for exactly this, so the scoping is a
 * one-line decision here rather than a post-hoc filter over every organisation's tickets.
 */
@Singleton
public class RetrieveWorkOrdersUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveWorkOrdersUseCase.class);
    private static final String REQUESTER_ROLE = "REQUESTER";

    private final WorkOrderRepository workOrderRepository;

    public RetrieveWorkOrdersUseCase(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    public Flux<WorkOrderResponse> execute(UUID organisationId, WorkOrderStatus status, String executor, String role) {
        log.info("[USE CASE] Retrieving WorkOrders for organisation: {} status: {} role: {}", organisationId, status, role);

        UUID requesterFilter = REQUESTER_ROLE.equals(role) ? UUID.fromString(executor) : null;
        boolean includeInternal = !REQUESTER_ROLE.equals(role);

        return workOrderRepository.findAllByOrganisationId(organisationId, status, requesterFilter)
                .map(workOrder -> WorkOrderMapper.toResponse(workOrder, includeInternal));
    }
}
