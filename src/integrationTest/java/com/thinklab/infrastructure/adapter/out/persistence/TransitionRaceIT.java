package com.thinklab.infrastructure.adapter.out.persistence;

import com.thinklab.application.usecase.ControlWorkOrderUseCase;
import com.thinklab.domain.exception.InvalidWorkOrderStatusException;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.model.WorkOrder.Priority;
import com.thinklab.domain.model.WorkOrder.WorkOrderStatus;
import com.thinklab.domain.model.WorkOrder.WorkOrderType;
import com.thinklab.domain.repository.WorkOrderRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The work-order transition races against a real MongoDB (platform race audit, ACTION-PLAN 0.5): a work order read by two requests at once must have
 * exactly one of them win, and the stored status must agree with the trail.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransitionRaceIT implements TestPropertyProvider {

    private static final String DATABASE = "it_hardware_maintenance_race_it";
    private static final String EXECUTOR = "op-1";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject WorkOrderRepository repository;
    @Inject ControlWorkOrderUseCase control;

    private WorkOrder requested() {
        WorkOrder order = WorkOrder.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Broken screen", "Flickers", WorkOrderType.CORRECTIVE, "Room 101", null, EXECUTOR);
        repository.create(order).block();
        return order;
    }

    private WorkOrder triaged() {
        WorkOrder order = requested();
        var entry = order.triage(Priority.P3, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(86400), EXECUTOR);
        repository.updateTriage(order.getId(), Priority.P3, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(86400), order.getStatus(), entry).block();
        return order;
    }

    private List<String> run(UUID id, UUID organisationId, ControlWorkOrderUseCase.Action... actions) {
        return Flux.fromArray(actions)
                .flatMap(action -> control.execute(id, organisationId, action, EXECUTOR).thenReturn("OK:" + action.name())
                        .onErrorResume(e -> Mono.just(e.getClass().getSimpleName())), actions.length)
                .collectList().block(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("ten cancellations of one work order at once: exactly one wins, the others are refused with a conflict")
    void tenCancellationsAtOnce() {
        WorkOrder order = requested();
        ControlWorkOrderUseCase.Action[] tenCancels = new ControlWorkOrderUseCase.Action[10];
        java.util.Arrays.fill(tenCancels, ControlWorkOrderUseCase.Action.CANCEL);

        List<String> results = run(order.getId(), order.getOrganisationId(), tenCancels);

        assertEquals(1, results.stream().filter(r -> r.startsWith("OK")).count(), results.toString());
        assertEquals(9, results.stream().filter(InvalidWorkOrderStatusException.class.getSimpleName()::equals).count(), results.toString());
        WorkOrder stored = repository.findById(order.getId(), order.getOrganisationId()).block();
        assertEquals(WorkOrderStatus.CANCELLED, stored.getStatus());
        assertEquals(2, stored.getAuditTrail().size(), "one entry for creating and one for the single cancel that won");
    }

    @Test
    @DisplayName("a schedule and a cancel of a triaged work order at once: one wins, and the stored status is the winner's")
    void scheduleAgainstCancel() {
        WorkOrder order = triaged();

        List<String> results = run(order.getId(), order.getOrganisationId(), ControlWorkOrderUseCase.Action.SCHEDULE, ControlWorkOrderUseCase.Action.CANCEL);

        List<String> winners = results.stream().filter(r -> r.startsWith("OK")).toList();
        assertEquals(1, winners.size(), results.toString());
        WorkOrder stored = repository.findById(order.getId(), order.getOrganisationId()).block();
        assertEquals(winners.get(0).equals("OK:SCHEDULE") ? WorkOrderStatus.SCHEDULED : WorkOrderStatus.CANCELLED, stored.getStatus());
        assertEquals(3, stored.getAuditTrail().size());
    }
}
