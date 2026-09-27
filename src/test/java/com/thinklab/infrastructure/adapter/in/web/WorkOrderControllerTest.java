package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.CompleteRepairRequest;
import com.thinklab.application.dto.request.EscalateWorkOrderRequest;
import com.thinklab.application.dto.request.InitiateCommentRequest;
import com.thinklab.application.dto.request.InitiatePartRequest;
import com.thinklab.application.dto.request.InitiateWorkOrderRequest;
import com.thinklab.application.dto.request.PassQualityCheckRequest;
import com.thinklab.application.dto.request.TriageWorkOrderRequest;
import com.thinklab.application.dto.request.UpdateWorkOrderRequest;
import com.thinklab.application.dto.response.WorkOrderAuditEntryResponse;
import com.thinklab.application.dto.response.WorkOrderResponse;
import com.thinklab.application.usecase.CompleteRepairUseCase;
import com.thinklab.application.usecase.ControlWorkOrderUseCase;
import com.thinklab.application.usecase.EscalateWorkOrderUseCase;
import com.thinklab.application.usecase.InitiateCommentUseCase;
import com.thinklab.application.usecase.InitiatePartUseCase;
import com.thinklab.application.usecase.InitiateWorkOrderUseCase;
import com.thinklab.application.usecase.PassQualityCheckUseCase;
import com.thinklab.application.usecase.RetrieveWorkOrderAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveWorkOrderUseCase;
import com.thinklab.application.usecase.RetrieveWorkOrdersUseCase;
import com.thinklab.application.usecase.StartRepairUseCase;
import com.thinklab.application.usecase.TriageWorkOrderUseCase;
import com.thinklab.application.usecase.UpdateWorkOrderUseCase;
import com.thinklab.application.usecase.VendorReturnedUseCase;
import com.thinklab.domain.model.WorkOrder.Priority;
import com.thinklab.domain.model.WorkOrder.WorkOrderType;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderControllerTest {

    @Mock private InitiateWorkOrderUseCase initiateWorkOrderUseCase;
    @Mock private RetrieveWorkOrderUseCase retrieveWorkOrderUseCase;
    @Mock private RetrieveWorkOrdersUseCase retrieveWorkOrdersUseCase;
    @Mock private UpdateWorkOrderUseCase updateWorkOrderUseCase;
    @Mock private TriageWorkOrderUseCase triageWorkOrderUseCase;
    @Mock private ControlWorkOrderUseCase controlWorkOrderUseCase;
    @Mock private StartRepairUseCase startRepairUseCase;
    @Mock private EscalateWorkOrderUseCase escalateWorkOrderUseCase;
    @Mock private VendorReturnedUseCase vendorReturnedUseCase;
    @Mock private CompleteRepairUseCase completeRepairUseCase;
    @Mock private PassQualityCheckUseCase passQualityCheckUseCase;
    @Mock private InitiatePartUseCase initiatePartUseCase;
    @Mock private InitiateCommentUseCase initiateCommentUseCase;
    @Mock private RetrieveWorkOrderAuditLogUseCase retrieveWorkOrderAuditLogUseCase;

    private WorkOrderController controller;
    private UUID id;
    private static final String TENANT = UUID.randomUUID().toString();
    private static final String EXECUTOR = "op-1";

    @BeforeEach
    void setUp() {
        controller = new WorkOrderController(initiateWorkOrderUseCase, retrieveWorkOrderUseCase, retrieveWorkOrdersUseCase,
                updateWorkOrderUseCase, triageWorkOrderUseCase, controlWorkOrderUseCase, startRepairUseCase,
                escalateWorkOrderUseCase, vendorReturnedUseCase, completeRepairUseCase, passQualityCheckUseCase,
                initiatePartUseCase, initiateCommentUseCase, retrieveWorkOrderAuditLogUseCase);
        id = UUID.randomUUID();
    }

    private WorkOrderResponse sampleResponse() {
        return new WorkOrderResponse(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "t", "s", null, null,
                "CORRECTIVE", null, "REQUESTED", null, null, java.util.List.of(), 0, null, null, java.util.List.of(),
                null, null, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("initiate returns 201 Created")
    void initiate() {
        InitiateWorkOrderRequest request = new InitiateWorkOrderRequest(UUID.randomUUID(), null, "t", "s", WorkOrderType.CORRECTIVE, null, null, null);
        when(initiateWorkOrderUseCase.execute(any(), eq(request), eq(EXECUTOR), eq("OPERATOR"))).thenReturn(Mono.just(sampleResponse()));

        var response = controller.initiate(TENANT, EXECUTOR, "OPERATOR", request).block();
        assertEquals(HttpStatus.CREATED, response.getStatus());
    }

    @Test
    @DisplayName("retrieveById returns 200 OK")
    void retrieveById() {
        when(retrieveWorkOrderUseCase.execute(id, EXECUTOR, "OPERATOR")).thenReturn(Mono.just(sampleResponse()));

        var response = controller.retrieveById(id, EXECUTOR, "OPERATOR").block();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    @DisplayName("retrieveAll delegates with the optional status filter")
    void retrieveAll() {
        when(retrieveWorkOrdersUseCase.execute(any(), any(), eq(EXECUTOR), eq("OPERATOR"))).thenReturn(Flux.just(sampleResponse()));

        var result = controller.retrieveAll(TENANT, EXECUTOR, "OPERATOR", null).block();
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("update returns 204 No Content")
    void update() {
        UpdateWorkOrderRequest request = new UpdateWorkOrderRequest("t", "s", null, null);
        when(updateWorkOrderUseCase.execute(id, request, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.update(id, EXECUTOR, request).block().getStatus());
    }

    @Test
    @DisplayName("triage returns 204 No Content")
    void triage() {
        TriageWorkOrderRequest request = new TriageWorkOrderRequest(Priority.P1);
        when(triageWorkOrderUseCase.execute(id, request, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.triage(id, EXECUTOR, request).block().getStatus());
    }

    @Test
    @DisplayName("schedule dispatches ControlWorkOrderUseCase.Action.SCHEDULE")
    void schedule() {
        when(controlWorkOrderUseCase.execute(id, ControlWorkOrderUseCase.Action.SCHEDULE, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.schedule(id, EXECUTOR).block().getStatus());
    }

    @Test
    @DisplayName("control/start dispatches StartRepairUseCase")
    void controlStart() {
        when(startRepairUseCase.execute(id, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.controlStart(id, EXECUTOR).block().getStatus());
    }

    @Test
    @DisplayName("control/await-parts, resume, fail, close, reopen and cancel each dispatch their Action")
    void controlSimpleActions() {
        lenient().when(controlWorkOrderUseCase.execute(any(), any(), eq(EXECUTOR))).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.controlAwaitParts(id, EXECUTOR).block().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.controlResume(id, EXECUTOR).block().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.controlFail(id, EXECUTOR).block().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.controlClose(id, EXECUTOR).block().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.controlReopen(id, EXECUTOR).block().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.controlCancel(id, EXECUTOR).block().getStatus());

        verify(controlWorkOrderUseCase).execute(id, ControlWorkOrderUseCase.Action.AWAIT_PARTS, EXECUTOR);
        verify(controlWorkOrderUseCase).execute(id, ControlWorkOrderUseCase.Action.RESUME, EXECUTOR);
        verify(controlWorkOrderUseCase).execute(id, ControlWorkOrderUseCase.Action.FAIL, EXECUTOR);
        verify(controlWorkOrderUseCase).execute(id, ControlWorkOrderUseCase.Action.CLOSE, EXECUTOR);
        verify(controlWorkOrderUseCase).execute(id, ControlWorkOrderUseCase.Action.REOPEN, EXECUTOR);
        verify(controlWorkOrderUseCase).execute(id, ControlWorkOrderUseCase.Action.CANCEL, EXECUTOR);
    }

    @Test
    @DisplayName("control/escalate returns 204 No Content")
    void controlEscalate() {
        EscalateWorkOrderRequest request = new EscalateWorkOrderRequest("Acme", "CASE-1");
        when(escalateWorkOrderUseCase.execute(id, request, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.controlEscalate(id, EXECUTOR, request).block().getStatus());
    }

    @Test
    @DisplayName("control/vendor-returned returns 204 No Content")
    void controlVendorReturned() {
        when(vendorReturnedUseCase.execute(id, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.controlVendorReturned(id, EXECUTOR).block().getStatus());
    }

    @Test
    @DisplayName("control/complete-repair returns 204 No Content")
    void controlCompleteRepair() {
        CompleteRepairRequest request = new CompleteRepairRequest("diag", 10);
        when(completeRepairUseCase.execute(id, request, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.controlCompleteRepair(id, EXECUTOR, request).block().getStatus());
    }

    @Test
    @DisplayName("control/pass returns 204 No Content")
    void controlPass() {
        PassQualityCheckRequest request = new PassQualityCheckRequest("RC-1");
        when(passQualityCheckUseCase.execute(id, request, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.controlPass(id, EXECUTOR, request).block().getStatus());
    }

    @Test
    @DisplayName("part/initiate returns 201 Created")
    void initiatePart() {
        InitiatePartRequest request = new InitiatePartRequest("Flex cable", 1);
        when(initiatePartUseCase.execute(id, request, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.CREATED, controller.initiatePart(id, EXECUTOR, request).block().getStatus());
    }

    @Test
    @DisplayName("comment/initiate returns 201 Created")
    void initiateComment() {
        InitiateCommentRequest request = new InitiateCommentRequest("note", false);
        when(initiateCommentUseCase.execute(id, request, EXECUTOR, "OPERATOR")).thenReturn(Mono.empty());

        assertEquals(HttpStatus.CREATED, controller.initiateComment(id, EXECUTOR, "OPERATOR", request).block().getStatus());
    }

    @Test
    @DisplayName("audit-log/retrieve delegates to RetrieveWorkOrderAuditLogUseCase")
    void retrieveAuditLog() {
        WorkOrderAuditEntryResponse entry = new WorkOrderAuditEntryResponse(Instant.now(), "INITIATED", EXECUTOR, null, "REQUESTED", "d");
        when(retrieveWorkOrderAuditLogUseCase.execute(id, EXECUTOR, "OPERATOR")).thenReturn(Mono.just(java.util.List.of(entry)));

        var result = controller.retrieveAuditLog(id, EXECUTOR, "OPERATOR").block();
        assertEquals(1, result.size());
    }
}
