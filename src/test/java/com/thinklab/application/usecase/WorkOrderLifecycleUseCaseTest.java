package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CompleteRepairRequest;
import com.thinklab.application.dto.request.EscalateWorkOrderRequest;
import com.thinklab.application.dto.request.PassQualityCheckRequest;
import com.thinklab.application.dto.request.TriageWorkOrderRequest;
import com.thinklab.domain.exception.InvalidWorkOrderStatusException;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.model.WorkOrder.Priority;
import com.thinklab.domain.model.WorkOrder.WorkOrderType;
import com.thinklab.domain.port.CalendarPort;
import com.thinklab.domain.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderLifecycleUseCaseTest {

    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private CalendarPort calendarPort;
    @Mock private WorkOrderEventPublisher eventPublisher;

    private UUID organisationId;
    private UUID assetId;
    private UUID requesterId;
    private WorkOrder workOrder;
    private static final String EXECUTOR = "op-1";

    @BeforeEach
    void setUp() {
        organisationId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        workOrder = WorkOrder.createNew(UUID.randomUUID(), organisationId, assetId, requesterId, "t", "s",
                WorkOrderType.CORRECTIVE, null, null, EXECUTOR);
        lenient().when(workOrderRepository.findById(workOrder.getId())).thenReturn(Mono.just(workOrder));
    }

    // --- TriageWorkOrderUseCase ---

    @Test
    @DisplayName("triage: computes SLA due dates from the calendar port and persists them")
    void triageSuccess() {
        Instant responseDue = Instant.now().plusSeconds(3600);
        Instant resolutionDue = Instant.now().plusSeconds(14400);
        when(calendarPort.addBusinessDuration(any(Instant.class), eqDuration(Priority.P1.responseTarget()))).thenReturn(responseDue);
        when(calendarPort.addBusinessDuration(any(Instant.class), eqDuration(Priority.P1.resolutionTarget()))).thenReturn(resolutionDue);
        when(workOrderRepository.updateTriage(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        TriageWorkOrderUseCase useCase = new TriageWorkOrderUseCase(workOrderRepository, calendarPort);

        StepVerifier.create(useCase.execute(workOrder.getId(), new TriageWorkOrderRequest(Priority.P1), EXECUTOR)).verifyComplete();
        verify(workOrderRepository).updateTriage(workOrder.getId(), Priority.P1, responseDue, resolutionDue, workOrder.getStatus(), workOrder.getAuditTrail().get(1));
    }

    @Test
    @DisplayName("triage: not found surfaces WorkOrderNotFoundException")
    void triageNotFound() {
        UUID unknown = UUID.randomUUID();
        when(workOrderRepository.findById(unknown)).thenReturn(Mono.empty());
        TriageWorkOrderUseCase useCase = new TriageWorkOrderUseCase(workOrderRepository, calendarPort);

        StepVerifier.create(useCase.execute(unknown, new TriageWorkOrderRequest(Priority.P1), EXECUTOR)).expectError(WorkOrderNotFoundException.class).verify();
    }

    // --- ControlWorkOrderUseCase ---

    @Test
    @DisplayName("control: SCHEDULE requires TRIAGED first; an illegal source status surfaces the domain's 409")
    void controlIllegalTransition() {
        ControlWorkOrderUseCase useCase = new ControlWorkOrderUseCase(workOrderRepository);

        StepVerifier.create(useCase.execute(workOrder.getId(), ControlWorkOrderUseCase.Action.SCHEDULE, EXECUTOR))
                .expectError(InvalidWorkOrderStatusException.class)
                .verify();
    }

    @Test
    @DisplayName("control: every simple Action reaches its target status")
    void controlEverySimpleAction() {
        when(workOrderRepository.updateStatus(any(), any(), any())).thenReturn(Mono.empty());
        ControlWorkOrderUseCase useCase = new ControlWorkOrderUseCase(workOrderRepository);

        workOrder.triage(Priority.P1, Instant.now(), Instant.now(), EXECUTOR);
        StepVerifier.create(useCase.execute(workOrder.getId(), ControlWorkOrderUseCase.Action.SCHEDULE, EXECUTOR)).verifyComplete();

        workOrder.start(EXECUTOR);
        StepVerifier.create(useCase.execute(workOrder.getId(), ControlWorkOrderUseCase.Action.AWAIT_PARTS, EXECUTOR)).verifyComplete();
        StepVerifier.create(useCase.execute(workOrder.getId(), ControlWorkOrderUseCase.Action.RESUME, EXECUTOR)).verifyComplete();

        workOrder.completeRepair("d", 0, EXECUTOR);
        StepVerifier.create(useCase.execute(workOrder.getId(), ControlWorkOrderUseCase.Action.FAIL, EXECUTOR)).verifyComplete();

        workOrder.completeRepair("d2", 0, EXECUTOR);
        workOrder.pass("rc", EXECUTOR);
        StepVerifier.create(useCase.execute(workOrder.getId(), ControlWorkOrderUseCase.Action.REOPEN, EXECUTOR)).verifyComplete();

        workOrder.escalateToVendor("v", "c", EXECUTOR);
        workOrder.vendorReturned(EXECUTOR);
        workOrder.completeRepair("d3", 0, EXECUTOR);
        workOrder.pass("rc2", EXECUTOR);
        StepVerifier.create(useCase.execute(workOrder.getId(), ControlWorkOrderUseCase.Action.CLOSE, EXECUTOR)).verifyComplete();
    }

    @Test
    @DisplayName("control: CANCEL from REQUESTED")
    void controlCancel() {
        when(workOrderRepository.updateStatus(any(), any(), any())).thenReturn(Mono.empty());
        ControlWorkOrderUseCase useCase = new ControlWorkOrderUseCase(workOrderRepository);

        StepVerifier.create(useCase.execute(workOrder.getId(), ControlWorkOrderUseCase.Action.CANCEL, EXECUTOR)).verifyComplete();
    }

    // --- StartRepairUseCase ---

    @Test
    @DisplayName("start: persists IN_REPAIR and publishes repair-started")
    void startPublishesEvent() {
        workOrder.triage(Priority.P1, Instant.now(), Instant.now(), EXECUTOR);
        workOrder.schedule(EXECUTOR);
        when(workOrderRepository.updateStatus(any(), any(), any())).thenReturn(Mono.empty());
        when(eventPublisher.publishRepairStarted(workOrder)).thenReturn(Mono.empty());
        StartRepairUseCase useCase = new StartRepairUseCase(workOrderRepository, eventPublisher);

        StepVerifier.create(useCase.execute(workOrder.getId(), EXECUTOR)).verifyComplete();
        verify(eventPublisher).publishRepairStarted(workOrder);
    }

    @Test
    @DisplayName("start: not found surfaces WorkOrderNotFoundException")
    void startNotFound() {
        UUID unknown = UUID.randomUUID();
        when(workOrderRepository.findById(unknown)).thenReturn(Mono.empty());
        StartRepairUseCase useCase = new StartRepairUseCase(workOrderRepository, eventPublisher);

        StepVerifier.create(useCase.execute(unknown, EXECUTOR)).expectError(WorkOrderNotFoundException.class).verify();
    }

    // --- EscalateWorkOrderUseCase / VendorReturnedUseCase ---

    @Test
    @DisplayName("escalate then vendor-returned")
    void escalateAndVendorReturned() {
        workOrder.triage(Priority.P1, Instant.now(), Instant.now(), EXECUTOR);
        when(workOrderRepository.updateRma(any(), any(), any(), any())).thenReturn(Mono.empty());
        EscalateWorkOrderUseCase escalate = new EscalateWorkOrderUseCase(workOrderRepository);
        VendorReturnedUseCase vendorReturned = new VendorReturnedUseCase(workOrderRepository);

        StepVerifier.create(escalate.execute(workOrder.getId(), new EscalateWorkOrderRequest("Acme", "CASE-1"), EXECUTOR)).verifyComplete();
        StepVerifier.create(vendorReturned.execute(workOrder.getId(), EXECUTOR)).verifyComplete();
    }

    // --- CompleteRepairUseCase / PassQualityCheckUseCase ---

    @Test
    @DisplayName("completeRepair then pass publishes repair-completed")
    void completeRepairThenPass() {
        workOrder.triage(Priority.P1, Instant.now(), Instant.now(), EXECUTOR);
        workOrder.schedule(EXECUTOR);
        workOrder.start(EXECUTOR);
        when(workOrderRepository.updateCompleteRepair(any(), any(), anyInt(), any(), any())).thenReturn(Mono.empty());
        when(workOrderRepository.updatePass(any(), any(), any(), any())).thenReturn(Mono.empty());
        when(eventPublisher.publishRepairCompleted(workOrder)).thenReturn(Mono.empty());
        CompleteRepairUseCase completeRepair = new CompleteRepairUseCase(workOrderRepository);
        PassQualityCheckUseCase pass = new PassQualityCheckUseCase(workOrderRepository, eventPublisher);

        StepVerifier.create(completeRepair.execute(workOrder.getId(), new CompleteRepairRequest("diag", 30), EXECUTOR)).verifyComplete();
        StepVerifier.create(pass.execute(workOrder.getId(), new PassQualityCheckRequest("RC-1"), EXECUTOR)).verifyComplete();
        verify(eventPublisher).publishRepairCompleted(workOrder);
    }

    private static Duration eqDuration(Duration duration) {
        return org.mockito.ArgumentMatchers.eq(duration);
    }
}
