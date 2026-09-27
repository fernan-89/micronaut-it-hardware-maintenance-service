package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.model.WorkOrder.Comment;
import com.thinklab.domain.model.WorkOrder.Part;
import com.thinklab.domain.model.WorkOrder.Priority;
import com.thinklab.domain.model.WorkOrder.RmaDetails;
import com.thinklab.domain.model.WorkOrder.WorkOrderAuditEntry;
import com.thinklab.domain.model.WorkOrder.WorkOrderStatus;
import com.thinklab.domain.model.WorkOrder.WorkOrderType;
import com.thinklab.domain.repository.WorkOrderRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WorkOrder aggregate through {@link WorkOrderRepository} against a real MongoDB: every granular
 * update, tenant/requester-scoped filtering, not-found handling, the database taken from
 * {@code mongodb.uri}, and the compound index {@link com.thinklab.infrastructure.adapter.out.persistence.repository.WorkOrderIndexInitializer}
 * creates at startup.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkOrderPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "it_hardware_maintenance_it";
    private static final String EXECUTOR = "op-1";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject
    WorkOrderRepository workOrders;

    @Inject
    MongoClient mongoClient;

    private static WorkOrder newWorkOrder(UUID organisationId, UUID requesterId) {
        return WorkOrder.createNew(UUID.randomUUID(), organisationId, UUID.randomUUID(), requesterId,
                "Broken screen", "Flickers", WorkOrderType.CORRECTIVE, "Room 101", null, EXECUTOR);
    }

    private static WorkOrderAuditEntry audit(String action, WorkOrderStatus from, WorkOrderStatus to) {
        return new WorkOrderAuditEntry(Instant.now(), action, EXECUTOR, from, to, action + " detail");
    }

    @Test
    @DisplayName("a created WorkOrder is read back with its INITIATED audit entry")
    void createAndFind() {
        WorkOrder created = workOrders.create(newWorkOrder(UUID.randomUUID(), UUID.randomUUID())).block();

        WorkOrder found = workOrders.findById(created.getId()).block();

        assertEquals(created.getTitle(), found.getTitle());
        assertEquals(WorkOrderStatus.REQUESTED, found.getStatus());
        assertEquals(1, found.getAuditTrail().size());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    @DisplayName("writes land in the database named by mongodb.uri")
    void usesTheConfiguredDatabase() {
        WorkOrder created = workOrders.create(newWorkOrder(UUID.randomUUID(), UUID.randomUUID())).block();

        Document stored = Mono.from(mongoClient.getDatabase(DATABASE).getCollection("work_orders")
                .find(Filters.eq("_id", created.getId())).first()).block();

        assertNotNull(stored, "work order not found in " + DATABASE);
    }

    @Test
    @DisplayName("every granular update is persisted and appends its own audit entry")
    void everyGranularUpdateAppendsToTheLedger() {
        WorkOrder created = workOrders.create(newWorkOrder(UUID.randomUUID(), UUID.randomUUID())).block();
        UUID id = created.getId();
        int initial = created.getAuditTrail().size();

        workOrders.updateBasicInfo(id, "New title", "New symptom", "Room 202", UUID.randomUUID(),
                audit("UPDATED", WorkOrderStatus.REQUESTED, WorkOrderStatus.REQUESTED)).block();
        Instant responseDue = Instant.now().plusSeconds(3600);
        Instant resolutionDue = Instant.now().plusSeconds(14400);
        workOrders.updateTriage(id, Priority.P1, responseDue, resolutionDue, WorkOrderStatus.TRIAGED,
                audit("TRIAGED", WorkOrderStatus.REQUESTED, WorkOrderStatus.TRIAGED)).block();
        workOrders.updateStatus(id, WorkOrderStatus.SCHEDULED, audit("SCHEDULED", WorkOrderStatus.TRIAGED, WorkOrderStatus.SCHEDULED)).block();
        workOrders.updateStatus(id, WorkOrderStatus.IN_REPAIR, audit("REPAIR_STARTED", WorkOrderStatus.SCHEDULED, WorkOrderStatus.IN_REPAIR)).block();
        RmaDetails rma = new RmaDetails("Acme", "CASE-1", Instant.now(), null);
        workOrders.updateRma(id, rma, WorkOrderStatus.ESCALATED_TO_VENDOR, audit("ESCALATED_TO_VENDOR", WorkOrderStatus.IN_REPAIR, WorkOrderStatus.ESCALATED_TO_VENDOR)).block();
        RmaDetails returned = rma.withReturnedAt(Instant.now());
        workOrders.updateRma(id, returned, WorkOrderStatus.IN_REPAIR, audit("VENDOR_RETURNED", WorkOrderStatus.ESCALATED_TO_VENDOR, WorkOrderStatus.IN_REPAIR)).block();
        workOrders.updateCompleteRepair(id, "Replaced panel", 45, WorkOrderStatus.QUALITY_CHECK,
                audit("REPAIR_COMPLETED", WorkOrderStatus.IN_REPAIR, WorkOrderStatus.QUALITY_CHECK)).block();
        workOrders.updatePass(id, "RC-1", WorkOrderStatus.RESOLVED, audit("QUALITY_CHECK_PASSED", WorkOrderStatus.QUALITY_CHECK, WorkOrderStatus.RESOLVED)).block();
        Part part = new Part(UUID.randomUUID(), "Flex cable", 2);
        workOrders.addPart(id, part, audit("PART_ADDED", WorkOrderStatus.RESOLVED, WorkOrderStatus.RESOLVED)).block();
        Comment comment = new Comment(UUID.randomUUID(), EXECUTOR, "Confirmed fixed", false, Instant.now());
        workOrders.addComment(id, comment, audit("COMMENT_ADDED", WorkOrderStatus.RESOLVED, WorkOrderStatus.RESOLVED)).block();

        WorkOrder found = workOrders.findById(id).block();
        assertEquals("New title", found.getTitle());
        assertEquals(Priority.P1, found.getPriority());
        assertEquals(WorkOrderStatus.RESOLVED, found.getStatus());
        assertEquals("Acme", found.getRma().vendorName());
        assertNotNull(found.getRma().returnedAt());
        assertEquals("Replaced panel", found.getDiagnosis());
        assertEquals(45, found.getLaborMinutes());
        assertEquals("RC-1", found.getResolutionCode());
        assertEquals(1, found.getParts().size());
        assertEquals(1, found.getComments().size());
        // Ten updates, ten ledger entries, in the order they were made.
        assertEquals(List.of("UPDATED", "TRIAGED", "SCHEDULED", "REPAIR_STARTED", "ESCALATED_TO_VENDOR", "VENDOR_RETURNED",
                        "REPAIR_COMPLETED", "QUALITY_CHECK_PASSED", "PART_ADDED", "COMMENT_ADDED"),
                found.getAuditTrail().subList(initial, found.getAuditTrail().size()).stream().map(WorkOrderAuditEntry::action).toList());
    }

    @Test
    @DisplayName("listing is tenant-scoped and honours the optional status and requesterId filters")
    void listingFilters() {
        UUID organisation = UUID.randomUUID();
        UUID requesterA = UUID.randomUUID();
        UUID requesterB = UUID.randomUUID();
        WorkOrder fromA = workOrders.create(newWorkOrder(organisation, requesterA)).block();
        WorkOrder fromB = workOrders.create(newWorkOrder(organisation, requesterB)).block();
        workOrders.create(newWorkOrder(UUID.randomUUID(), requesterA)).block();
        workOrders.updateStatus(fromB.getId(), WorkOrderStatus.CANCELLED,
                audit("CANCELLED", WorkOrderStatus.REQUESTED, WorkOrderStatus.CANCELLED)).block();

        assertEquals(Set.of(fromA.getId(), fromB.getId()), ids(workOrders.findAllByOrganisationId(organisation, null, null).collectList().block()));
        assertEquals(Set.of(fromA.getId()), ids(workOrders.findAllByOrganisationId(organisation, null, requesterA).collectList().block()));
        assertEquals(Set.of(fromB.getId()), ids(workOrders.findAllByOrganisationId(organisation, WorkOrderStatus.CANCELLED, null).collectList().block()));
    }

    @Test
    @DisplayName("an unknown WorkOrder is empty on read and WorkOrderNotFoundException on update")
    void notFound() {
        UUID unknown = UUID.randomUUID();

        assertNull(workOrders.findById(unknown).block());
        assertThrows(WorkOrderNotFoundException.class, () -> workOrders.updateStatus(unknown, WorkOrderStatus.CANCELLED,
                audit("CANCELLED", WorkOrderStatus.REQUESTED, WorkOrderStatus.CANCELLED)).block());
    }

    @Test
    @DisplayName("the compound (organisationId, status, requesterId) index exists")
    void indexExists() {
        workOrders.create(newWorkOrder(UUID.randomUUID(), UUID.randomUUID())).block();

        List<Document> indexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("work_orders").listIndexes()).collectList().block();

        assertTrue(indexes.stream().anyMatch(index -> new Document("organisationId", 1).append("status", 1).append("requesterId", 1)
                .equals(index.get("key", Document.class))), () -> "work_orders: " + indexes);
    }

    private static Set<UUID> ids(List<WorkOrder> list) {
        return list.stream().map(WorkOrder::getId).collect(Collectors.toSet());
    }
}
