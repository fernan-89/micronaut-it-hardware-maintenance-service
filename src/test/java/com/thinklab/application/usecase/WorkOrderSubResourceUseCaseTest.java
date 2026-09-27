package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateCommentRequest;
import com.thinklab.application.dto.request.InitiatePartRequest;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.model.WorkOrder.Comment;
import com.thinklab.domain.model.WorkOrder.WorkOrderType;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderSubResourceUseCaseTest {

    @Mock private HashServicePort hashServicePort;
    @Mock private WorkOrderRepository workOrderRepository;

    private WorkOrder workOrder;
    private UUID requesterId;
    private static final String EXECUTOR = "op-1";

    @BeforeEach
    void setUp() {
        requesterId = UUID.randomUUID();
        workOrder = WorkOrder.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), requesterId,
                "t", "s", WorkOrderType.CORRECTIVE, null, null, EXECUTOR);
        lenient().when(workOrderRepository.findById(workOrder.getId())).thenReturn(Mono.just(workOrder));
    }

    // --- InitiatePartUseCase ---

    @Test
    @DisplayName("initiatePart: obtains a sovereign id and persists the part")
    void initiatePartSuccess() {
        UUID partId = UUID.randomUUID();
        when(hashServicePort.generateSovereignId("workorder-part-creation")).thenReturn(Mono.just(partId));
        when(workOrderRepository.addPart(any(), any(), any())).thenReturn(Mono.empty());
        InitiatePartUseCase useCase = new InitiatePartUseCase(hashServicePort, workOrderRepository);

        StepVerifier.create(useCase.execute(workOrder.getId(), new InitiatePartRequest("Flex cable", 1), EXECUTOR)).verifyComplete();
    }

    @Test
    @DisplayName("initiatePart: not found surfaces WorkOrderNotFoundException")
    void initiatePartNotFound() {
        UUID unknown = UUID.randomUUID();
        when(workOrderRepository.findById(unknown)).thenReturn(Mono.empty());
        InitiatePartUseCase useCase = new InitiatePartUseCase(hashServicePort, workOrderRepository);

        StepVerifier.create(useCase.execute(unknown, new InitiatePartRequest("d", 1), EXECUTOR)).expectError(WorkOrderNotFoundException.class).verify();
    }

    // --- InitiateCommentUseCase ---

    @Test
    @DisplayName("initiateComment: an OPERATOR's internal=true request is honoured")
    void initiateCommentAsOperatorInternal() {
        UUID commentId = UUID.randomUUID();
        when(hashServicePort.generateSovereignId("workorder-comment-creation")).thenReturn(Mono.just(commentId));
        when(workOrderRepository.addComment(any(), any(), any())).thenReturn(Mono.empty());
        InitiateCommentUseCase useCase = new InitiateCommentUseCase(hashServicePort, workOrderRepository);

        StepVerifier.create(useCase.execute(workOrder.getId(), new InitiateCommentRequest("note", true), EXECUTOR, "OPERATOR")).verifyComplete();
        Comment added = workOrder.getComments().get(0);
        org.junit.jupiter.api.Assertions.assertTrue(added.internal());
    }

    @Test
    @DisplayName("initiateComment: an OPERATOR's internal=false request stays false")
    void initiateCommentAsOperatorExternal() {
        UUID commentId = UUID.randomUUID();
        when(hashServicePort.generateSovereignId("workorder-comment-creation")).thenReturn(Mono.just(commentId));
        when(workOrderRepository.addComment(any(), any(), any())).thenReturn(Mono.empty());
        InitiateCommentUseCase useCase = new InitiateCommentUseCase(hashServicePort, workOrderRepository);

        StepVerifier.create(useCase.execute(workOrder.getId(), new InitiateCommentRequest("note", false), EXECUTOR, "OPERATOR")).verifyComplete();
        Comment added = workOrder.getComments().get(0);
        org.junit.jupiter.api.Assertions.assertFalse(added.internal());
    }

    @Test
    @DisplayName("initiateComment: a REQUESTER's internal=true request is forced to false")
    void initiateCommentAsRequesterForcesExternal() {
        UUID commentId = UUID.randomUUID();
        when(hashServicePort.generateSovereignId("workorder-comment-creation")).thenReturn(Mono.just(commentId));
        when(workOrderRepository.addComment(any(), any(), any())).thenReturn(Mono.empty());
        InitiateCommentUseCase useCase = new InitiateCommentUseCase(hashServicePort, workOrderRepository);

        StepVerifier.create(useCase.execute(workOrder.getId(), new InitiateCommentRequest("note", true), requesterId.toString(), "REQUESTER")).verifyComplete();
        Comment added = workOrder.getComments().get(0);
        org.junit.jupiter.api.Assertions.assertFalse(added.internal());
    }

    // --- RetrieveWorkOrderAuditLogUseCase ---

    @Test
    @DisplayName("auditLog: an OPERATOR sees the ledger; the owning REQUESTER sees it too; a REQUESTER on a foreign ticket gets 404")
    void auditLogScoping() {
        RetrieveWorkOrderAuditLogUseCase useCase = new RetrieveWorkOrderAuditLogUseCase(workOrderRepository);

        StepVerifier.create(useCase.execute(workOrder.getId(), EXECUTOR, "OPERATOR")).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(workOrder.getId(), requesterId.toString(), "REQUESTER")).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(workOrder.getId(), UUID.randomUUID().toString(), "REQUESTER"))
                .expectError(WorkOrderNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("auditLog: not found surfaces WorkOrderNotFoundException")
    void auditLogNotFound() {
        UUID unknown = UUID.randomUUID();
        when(workOrderRepository.findById(unknown)).thenReturn(Mono.empty());
        RetrieveWorkOrderAuditLogUseCase useCase = new RetrieveWorkOrderAuditLogUseCase(workOrderRepository);

        StepVerifier.create(useCase.execute(unknown, EXECUTOR, "OPERATOR")).expectError(WorkOrderNotFoundException.class).verify();
    }
}
