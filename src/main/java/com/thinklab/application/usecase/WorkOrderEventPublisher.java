package com.thinklab.application.usecase;

import com.thinklab.application.dto.event.WorkOrderRepairEvent;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.kit.events.OutboxEvent;
import com.thinklab.kit.events.OutboxStore;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

/**
 * Publishes the {@code repair-started}/{@code repair-completed} outbox events (kit ADR-003).
 *
 * <p><b>Best-effort, not transactional (ADR-034):</b> unlike party-authentication's
 * {@code UserCreationWriter} (Micronaut Data {@code @Transactional} joining the same MongoDB
 * session), this service persists WorkOrder through the raw reactive-streams driver, matching
 * Asset/Site - a true multi-document transaction there would need manual {@code ClientSession}
 * orchestration. The status write always happens first; a failed outbox append is logged and
 * swallowed rather than failing the whole request, so a client never sees "control/start succeeded"
 * roll back because of an unrelated event-plumbing problem.
 */
@Singleton
public class WorkOrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderEventPublisher.class);

    private final OutboxStore outboxStore;
    private final ObjectMapper objectMapper;

    public WorkOrderEventPublisher(OutboxStore outboxStore, ObjectMapper objectMapper) {
        this.outboxStore = outboxStore;
        this.objectMapper = objectMapper;
    }

    public Mono<Void> publishRepairStarted(WorkOrder workOrder) {
        return publish(WorkOrderRepairEvent.REPAIR_STARTED_SUBJECT, workOrder);
    }

    public Mono<Void> publishRepairCompleted(WorkOrder workOrder) {
        return publish(WorkOrderRepairEvent.REPAIR_COMPLETED_SUBJECT, workOrder);
    }

    private Mono<Void> publish(String subject, WorkOrder workOrder) {
        WorkOrderRepairEvent payload = new WorkOrderRepairEvent(workOrder.getId(), workOrder.getOrganisationId(), workOrder.getAssetId(), Instant.now());
        OutboxEvent event;
        try {
            event = OutboxEvent.newEvent(subject, objectMapper.writeValueAsString(payload));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return outboxStore.append(event).then()
                .onErrorResume(e -> {
                    log.warn("[EVENTS] Failed to append outbox event [{}] for WorkOrder [{}]: {}", subject, workOrder.getId(), e.getMessage());
                    return Mono.empty();
                });
    }
}
