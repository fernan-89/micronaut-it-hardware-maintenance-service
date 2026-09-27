package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateWorkOrderRequest;
import com.thinklab.application.dto.request.UpdateWorkOrderRequest;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.model.WorkOrder.WorkOrderStatus;
import com.thinklab.domain.model.WorkOrder.WorkOrderType;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderCreationAndRetrievalUseCaseTest {

    @Mock private HashServicePort hashServicePort;
    @Mock private WorkOrderRepository workOrderRepository;

    private UUID organisationId;
    private UUID assetId;
    private UUID requesterId;
    private static final String EXECUTOR = "op-1";

    @BeforeEach
    void setUp() {
        organisationId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
    }

    // --- InitiateWorkOrderUseCase ---

    @Test
    @DisplayName("initiate: an OPERATOR must supply requesterId explicitly")
    void initiateAsOperator() {
        InitiateWorkOrderRequest request = new InitiateWorkOrderRequest(assetId, requesterId, "t", "s", WorkOrderType.CORRECTIVE, null, null, null);
        UUID sovereignId = UUID.randomUUID();
        lenient().when(hashServicePort.generateSovereignId("workorder-creation")).thenReturn(Mono.just(sovereignId));
        lenient().when(workOrderRepository.create(any(WorkOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        InitiateWorkOrderUseCase useCase = new InitiateWorkOrderUseCase(hashServicePort, workOrderRepository);

        StepVerifier.create(useCase.execute(organisationId, request, EXECUTOR, "OPERATOR"))
                .assertNext(response -> assertEquals(requesterId, response.requesterId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("initiate: an OPERATOR omitting requesterId is rejected")
    void initiateAsOperatorWithoutRequesterId() {
        InitiateWorkOrderRequest request = new InitiateWorkOrderRequest(assetId, null, "t", "s", WorkOrderType.CORRECTIVE, null, null, null);
        InitiateWorkOrderUseCase useCase = new InitiateWorkOrderUseCase(hashServicePort, workOrderRepository);

        StepVerifier.create(useCase.execute(organisationId, request, EXECUTOR, "OPERATOR"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("initiate: a REQUESTER always files on their own behalf, ignoring any requesterId in the body")
    void initiateAsRequesterForcesOwnId() {
        InitiateWorkOrderRequest request = new InitiateWorkOrderRequest(assetId, UUID.randomUUID(), "t", "s", WorkOrderType.CORRECTIVE, null, null, null);
        UUID sovereignId = UUID.randomUUID();
        lenient().when(hashServicePort.generateSovereignId("workorder-creation")).thenReturn(Mono.just(sovereignId));
        lenient().when(workOrderRepository.create(any(WorkOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        InitiateWorkOrderUseCase useCase = new InitiateWorkOrderUseCase(hashServicePort, workOrderRepository);
        String requesterExecutor = requesterId.toString();

        StepVerifier.create(useCase.execute(organisationId, request, requesterExecutor, "REQUESTER"))
                .assertNext(response -> assertEquals(requesterId, response.requesterId()))
                .verifyComplete();
    }

    // --- RetrieveWorkOrderUseCase ---

    @Test
    @DisplayName("retrieveById: a not-found id surfaces WorkOrderNotFoundException")
    void retrieveByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(workOrderRepository.findById(id)).thenReturn(Mono.empty());
        RetrieveWorkOrderUseCase useCase = new RetrieveWorkOrderUseCase(workOrderRepository);

        StepVerifier.create(useCase.execute(id, EXECUTOR, "OPERATOR")).expectError(WorkOrderNotFoundException.class).verify();
    }

    @Test
    @DisplayName("retrieveById: an OPERATOR sees any ticket with internal comments included")
    void retrieveByIdAsOperator() {
        WorkOrder workOrder = newWorkOrder();
        when(workOrderRepository.findById(workOrder.getId())).thenReturn(Mono.just(workOrder));
        RetrieveWorkOrderUseCase useCase = new RetrieveWorkOrderUseCase(workOrderRepository);

        StepVerifier.create(useCase.execute(workOrder.getId(), EXECUTOR, "OPERATOR")).expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("retrieveById: a REQUESTER viewing someone else's ticket gets the same 404 as an unknown id")
    void retrieveByIdAsRequesterForeignTicket() {
        WorkOrder workOrder = newWorkOrder();
        when(workOrderRepository.findById(workOrder.getId())).thenReturn(Mono.just(workOrder));
        RetrieveWorkOrderUseCase useCase = new RetrieveWorkOrderUseCase(workOrderRepository);

        StepVerifier.create(useCase.execute(workOrder.getId(), UUID.randomUUID().toString(), "REQUESTER"))
                .expectError(WorkOrderNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("retrieveById: the owning REQUESTER can see their own ticket")
    void retrieveByIdAsOwningRequester() {
        WorkOrder workOrder = newWorkOrder();
        when(workOrderRepository.findById(workOrder.getId())).thenReturn(Mono.just(workOrder));
        RetrieveWorkOrderUseCase useCase = new RetrieveWorkOrderUseCase(workOrderRepository);

        StepVerifier.create(useCase.execute(workOrder.getId(), requesterId.toString(), "REQUESTER")).expectNextCount(1).verifyComplete();
    }

    // --- RetrieveWorkOrdersUseCase ---

    @Test
    @DisplayName("retrieveAll: a REQUESTER is always filtered to their own tickets; an OPERATOR is not")
    void retrieveAllScoping() {
        when(workOrderRepository.findAllByOrganisationId(eq(organisationId), any(), any())).thenReturn(Flux.just(newWorkOrder()));
        RetrieveWorkOrdersUseCase useCase = new RetrieveWorkOrdersUseCase(workOrderRepository);

        StepVerifier.create(useCase.execute(organisationId, WorkOrderStatus.REQUESTED, requesterId.toString(), "REQUESTER")).expectNextCount(1).verifyComplete();
        verify(workOrderRepository).findAllByOrganisationId(organisationId, WorkOrderStatus.REQUESTED, requesterId);

        StepVerifier.create(useCase.execute(organisationId, null, EXECUTOR, "OPERATOR")).expectNextCount(1).verifyComplete();
        verify(workOrderRepository).findAllByOrganisationId(organisationId, null, null);
    }

    // --- UpdateWorkOrderUseCase ---

    @Test
    @DisplayName("update: not found surfaces WorkOrderNotFoundException")
    void updateNotFound() {
        UUID id = UUID.randomUUID();
        when(workOrderRepository.findById(id)).thenReturn(Mono.empty());
        UpdateWorkOrderUseCase useCase = new UpdateWorkOrderUseCase(workOrderRepository);

        StepVerifier.create(useCase.execute(id, new UpdateWorkOrderRequest("t", "s", null, null), EXECUTOR))
                .expectError(WorkOrderNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("update: applies the domain mutation and persists the granular update")
    void updateSuccess() {
        WorkOrder workOrder = newWorkOrder();
        when(workOrderRepository.findById(workOrder.getId())).thenReturn(Mono.just(workOrder));
        when(workOrderRepository.updateBasicInfo(eq(workOrder.getId()), eq("New title"), eq("New symptom"), any(), any(), any()))
                .thenReturn(Mono.empty());
        UpdateWorkOrderUseCase useCase = new UpdateWorkOrderUseCase(workOrderRepository);

        StepVerifier.create(useCase.execute(workOrder.getId(), new UpdateWorkOrderRequest("New title", "New symptom", null, null), EXECUTOR))
                .verifyComplete();
    }

    private WorkOrder newWorkOrder() {
        return WorkOrder.createNew(UUID.randomUUID(), organisationId, assetId, requesterId, "t", "s",
                WorkOrderType.CORRECTIVE, null, null, EXECUTOR);
    }
}
