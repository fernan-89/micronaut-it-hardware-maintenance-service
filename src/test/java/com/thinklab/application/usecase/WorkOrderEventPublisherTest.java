package com.thinklab.application.usecase;

import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.model.WorkOrder.WorkOrderType;
import com.thinklab.kit.events.OutboxEvent;
import com.thinklab.kit.events.OutboxStore;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderEventPublisherTest {

    @Mock private OutboxStore outboxStore;
    @Mock private ObjectMapper objectMapper;

    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        workOrder = WorkOrder.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "t", "s", WorkOrderType.CORRECTIVE, null, null, "op-1");
    }

    @Test
    @DisplayName("publishRepairStarted appends an OutboxEvent on the repair-started subject")
    void publishRepairStarted() throws IOException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxStore.append(argThat(event -> event.subject().equals("thinklab.it-hardware-maintenance.workorder.repair-started"))))
                .thenReturn(Mono.just(OutboxEvent.newEvent("s", "{}")));
        WorkOrderEventPublisher publisher = new WorkOrderEventPublisher(outboxStore, objectMapper);

        StepVerifier.create(publisher.publishRepairStarted(workOrder)).verifyComplete();
    }

    @Test
    @DisplayName("publishRepairCompleted appends an OutboxEvent on the repair-completed subject")
    void publishRepairCompleted() throws IOException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxStore.append(argThat(event -> event.subject().equals("thinklab.it-hardware-maintenance.workorder.repair-completed"))))
                .thenReturn(Mono.just(OutboxEvent.newEvent("s", "{}")));
        WorkOrderEventPublisher publisher = new WorkOrderEventPublisher(outboxStore, objectMapper);

        StepVerifier.create(publisher.publishRepairCompleted(workOrder)).verifyComplete();
    }

    @Test
    @DisplayName("a failed outbox append is logged and swallowed, never failing the caller")
    void appendFailureIsSwallowed() throws IOException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxStore.append(any())).thenReturn(Mono.error(new RuntimeException("mongo down")));
        WorkOrderEventPublisher publisher = new WorkOrderEventPublisher(outboxStore, objectMapper);

        StepVerifier.create(publisher.publishRepairStarted(workOrder)).verifyComplete();
    }

    @Test
    @DisplayName("a JSON serialization failure is wrapped as UncheckedIOException")
    void serializationFailure() throws IOException {
        lenient().when(objectMapper.writeValueAsString(any())).thenThrow(new IOException("bad payload"));
        WorkOrderEventPublisher publisher = new WorkOrderEventPublisher(outboxStore, objectMapper);

        assertThrows(java.io.UncheckedIOException.class, () -> publisher.publishRepairStarted(workOrder));
    }
}
