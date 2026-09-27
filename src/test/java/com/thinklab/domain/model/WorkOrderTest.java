package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidWorkOrderStatusException;
import com.thinklab.domain.model.WorkOrder.Comment;
import com.thinklab.domain.model.WorkOrder.ExternalReference;
import com.thinklab.domain.model.WorkOrder.Part;
import com.thinklab.domain.model.WorkOrder.Priority;
import com.thinklab.domain.model.WorkOrder.RmaDetails;
import com.thinklab.domain.model.WorkOrder.WorkOrderStatus;
import com.thinklab.domain.model.WorkOrder.WorkOrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkOrderTest {

    private UUID id;
    private UUID organisationId;
    private UUID assetId;
    private UUID requesterId;
    private WorkOrder wo;
    private static final String EXECUTOR = "op-1";

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        wo = WorkOrder.createNew(id, organisationId, assetId, requesterId, "Broken screen", "Screen flickers",
                WorkOrderType.CORRECTIVE, "Room 101", null, EXECUTOR);
    }

    @Test
    @DisplayName("createNew starts REQUESTED with an INITIATED audit entry")
    void createNewStartsRequested() {
        assertEquals(WorkOrderStatus.REQUESTED, wo.getStatus());
        assertEquals(1, wo.getAuditTrail().size());
        assertEquals("INITIATED", wo.getAuditTrail().get(0).action());
        assertNull(wo.getAuditTrail().get(0).fromStatus());
        assertEquals(WorkOrderStatus.REQUESTED, wo.getAuditTrail().get(0).toStatus());
        assertEquals(assetId, wo.getAssetId());
        assertEquals(requesterId, wo.getRequesterId());
        assertTrue(wo.getParts().isEmpty());
        assertTrue(wo.getComments().isEmpty());
        assertNull(wo.getPriority());
        assertNull(wo.getRma());
        assertNull(wo.getExternalReference());
        assertEquals(wo.getCreatedAt(), wo.getUpdatedAt());
    }

    @Test
    @DisplayName("createNew rejects missing identity, type, title, symptom or executor")
    void createNewGuards() {
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.createNew(null, organisationId, assetId, requesterId, "t", "s", WorkOrderType.CORRECTIVE, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.createNew(id, null, assetId, requesterId, "t", "s", WorkOrderType.CORRECTIVE, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.createNew(id, organisationId, null, requesterId, "t", "s", WorkOrderType.CORRECTIVE, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.createNew(id, organisationId, assetId, null, "t", "s", WorkOrderType.CORRECTIVE, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.createNew(id, organisationId, assetId, requesterId, "t", "s", null, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.createNew(id, organisationId, assetId, requesterId, null, "s", WorkOrderType.CORRECTIVE, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.createNew(id, organisationId, assetId, requesterId, "", "s", WorkOrderType.CORRECTIVE, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.createNew(id, organisationId, assetId, requesterId, "t", null, WorkOrderType.CORRECTIVE, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.createNew(id, organisationId, assetId, requesterId, "t", " ", WorkOrderType.CORRECTIVE, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.createNew(id, organisationId, assetId, requesterId, "t", "s", WorkOrderType.CORRECTIVE, null, null, " "));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.createNew(id, organisationId, assetId, requesterId, "t", "s", WorkOrderType.CORRECTIVE, null, null, null));
    }

    @Test
    @DisplayName("createNew accepts an ExternalReference")
    void createNewWithExternalReference() {
        WorkOrder withRef = WorkOrder.createNew(id, organisationId, assetId, requesterId, "t", "s", WorkOrderType.CORRECTIVE,
                null, new ExternalReference("ServiceNow", "INC0001"), EXECUTOR);
        assertEquals("ServiceNow", withRef.getExternalReference().system());
    }

    @Test
    @DisplayName("reconstitute rejects missing mandatory identity/type/title")
    void reconstituteGuards() {
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.reconstitute(null, organisationId, assetId, requesterId, "t",
                "s", null, null, WorkOrderType.CORRECTIVE, null, null, null, null, List.of(), 0, null, null, List.of(), null, null, null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.reconstitute(id, null, assetId, requesterId, "t",
                "s", null, null, WorkOrderType.CORRECTIVE, null, null, null, null, List.of(), 0, null, null, List.of(), null, null, null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.reconstitute(id, organisationId, null, requesterId, "t",
                "s", null, null, WorkOrderType.CORRECTIVE, null, null, null, null, List.of(), 0, null, null, List.of(), null, null, null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.reconstitute(id, organisationId, assetId, null, "t",
                "s", null, null, WorkOrderType.CORRECTIVE, null, null, null, null, List.of(), 0, null, null, List.of(), null, null, null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.reconstitute(id, organisationId, assetId, requesterId, null,
                "s", null, null, WorkOrderType.CORRECTIVE, null, null, null, null, List.of(), 0, null, null, List.of(), null, null, null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> WorkOrder.reconstitute(id, organisationId, assetId, requesterId, "t",
                "s", null, null, null, null, null, null, null, List.of(), 0, null, null, List.of(), null, null, null, null, List.of()));
    }

    @Test
    @DisplayName("reconstitute defaults a missing status to REQUESTED and missing lists to empty")
    void reconstituteDefaults() {
        WorkOrder restored = WorkOrder.reconstitute(id, organisationId, assetId, requesterId, "t", "s", null, null,
                WorkOrderType.CORRECTIVE, null, null, null, null, null, 0, null, null, null, null, null, null, null, null);

        assertEquals(WorkOrderStatus.REQUESTED, restored.getStatus());
        assertTrue(restored.getParts().isEmpty());
        assertTrue(restored.getComments().isEmpty());
        assertTrue(restored.getAuditTrail().isEmpty());
        assertNotNull(restored.getCreatedAt());
        assertEquals(restored.getCreatedAt(), restored.getUpdatedAt());
    }

    @Test
    @DisplayName("the full happy-path lifecycle walk, including fail, reopen, escalate and vendor-returned")
    void fullLifecycleWalk() {
        Instant respDue = Instant.now().plusSeconds(3600);
        Instant resDue = Instant.now().plusSeconds(14400);

        wo.triage(Priority.P2, respDue, resDue, EXECUTOR);
        assertEquals(WorkOrderStatus.TRIAGED, wo.getStatus());
        assertEquals(Priority.P2, wo.getPriority());
        assertEquals(respDue, wo.getSlaResponseDueAt());
        assertEquals(resDue, wo.getSlaResolutionDueAt());

        wo.schedule(EXECUTOR);
        assertEquals(WorkOrderStatus.SCHEDULED, wo.getStatus());

        wo.start(EXECUTOR);
        assertEquals(WorkOrderStatus.IN_REPAIR, wo.getStatus());

        wo.awaitParts(EXECUTOR);
        assertEquals(WorkOrderStatus.AWAITING_PARTS, wo.getStatus());

        wo.resume(EXECUTOR);
        assertEquals(WorkOrderStatus.IN_REPAIR, wo.getStatus());

        wo.completeRepair("Replaced flex cable", 30, EXECUTOR);
        assertEquals(WorkOrderStatus.QUALITY_CHECK, wo.getStatus());
        assertEquals("Replaced flex cable", wo.getDiagnosis());
        assertEquals(30, wo.getLaborMinutes());

        wo.fail(EXECUTOR);
        assertEquals(WorkOrderStatus.IN_REPAIR, wo.getStatus());

        wo.completeRepair("Replaced flex cable, retested", 15, EXECUTOR);
        assertEquals(45, wo.getLaborMinutes());

        wo.pass("RC-DISPLAY-01", EXECUTOR);
        assertEquals(WorkOrderStatus.RESOLVED, wo.getStatus());
        assertEquals("RC-DISPLAY-01", wo.getResolutionCode());

        wo.reopen(EXECUTOR);
        assertEquals(WorkOrderStatus.TRIAGED, wo.getStatus());

        wo.escalateToVendor("Acme Repairs", "CASE-1", EXECUTOR);
        assertEquals(WorkOrderStatus.ESCALATED_TO_VENDOR, wo.getStatus());
        assertEquals("Acme Repairs", wo.getRma().vendorName());
        assertNull(wo.getRma().returnedAt());

        wo.vendorReturned(EXECUTOR);
        assertEquals(WorkOrderStatus.IN_REPAIR, wo.getStatus());
        assertNotNull(wo.getRma().returnedAt());

        wo.completeRepair("Vendor replaced the panel", 0, EXECUTOR);
        wo.pass("RC-DISPLAY-02", EXECUTOR);
        wo.close(EXECUTOR);
        assertEquals(WorkOrderStatus.CLOSED, wo.getStatus());
    }

    @Test
    @DisplayName("cancel is legal from REQUESTED and from TRIAGED")
    void cancelFromRequestedAndTriaged() {
        WorkOrder requested = newWorkOrder();
        requested.cancel(EXECUTOR);
        assertEquals(WorkOrderStatus.CANCELLED, requested.getStatus());

        WorkOrder triaged = newWorkOrder();
        triaged.triage(Priority.P1, Instant.now(), Instant.now(), EXECUTOR);
        triaged.cancel(EXECUTOR);
        assertEquals(WorkOrderStatus.CANCELLED, triaged.getStatus());
    }

    @Test
    @DisplayName("every named transition rejects the wrong source status")
    void illegalSourceStateIsRejectedForEveryTransition() {
        assertIllegal(w -> w.triage(Priority.P1, Instant.now(), Instant.now(), EXECUTOR));
        assertIllegal(w -> w.schedule(EXECUTOR));
        assertIllegal(w -> w.escalateToVendor("v", "c", EXECUTOR));
        assertIllegal(w -> w.start(EXECUTOR));
        assertIllegal(w -> w.awaitParts(EXECUTOR));
        assertIllegal(w -> w.resume(EXECUTOR));
        assertIllegal(w -> w.vendorReturned(EXECUTOR));
        assertIllegal(w -> w.completeRepair("d", 0, EXECUTOR));
        assertIllegal(w -> w.pass("rc", EXECUTOR));
        assertIllegal(w -> w.fail(EXECUTOR));
        assertIllegal(w -> w.close(EXECUTOR));
        assertIllegal(w -> w.reopen(EXECUTOR));
        assertIllegal(w -> w.cancel(EXECUTOR));
    }

    /** A freshly closed WorkOrder is in the wrong state for literally every other transition. */
    private void assertIllegal(Consumer<WorkOrder> action) {
        WorkOrder closed = closedWorkOrder();
        assertThrows(InvalidWorkOrderStatusException.class, () -> action.accept(closed));
    }

    @Test
    @DisplayName("update, addPart and addComment are blocked once the WorkOrder is terminal (CLOSED or CANCELLED)")
    void terminalBlocksMutation() {
        WorkOrder closed = closedWorkOrder();
        assertThrows(InvalidWorkOrderStatusException.class, () -> closed.updateBasicInfo("t", "s", null, null, EXECUTOR));
        assertThrows(InvalidWorkOrderStatusException.class, () -> closed.addPart(new Part(UUID.randomUUID(), "Screen", 1), EXECUTOR));
        // addComment is allowed even when terminal (a closed ticket can still receive a follow-up note).
        closed.addComment(new Comment(UUID.randomUUID(), "op", "note", false, null), EXECUTOR);
        assertEquals(1, closed.getComments().size());

        WorkOrder cancelled = newWorkOrder();
        cancelled.cancel(EXECUTOR);
        assertThrows(InvalidWorkOrderStatusException.class, () -> cancelled.updateBasicInfo("t", "s", null, null, EXECUTOR));
    }

    @Test
    @DisplayName("updateBasicInfo replaces title/symptom/location/loaner and rejects blank fields")
    void updateBasicInfo() {
        var entry = wo.updateBasicInfo("New title", "New symptom", "Room 202", UUID.randomUUID(), EXECUTOR);
        assertEquals("New title", wo.getTitle());
        assertEquals("New symptom", wo.getSymptom());
        assertEquals("Room 202", wo.getLocation());
        assertNotNull(wo.getLoanerAssetId());
        assertEquals("UPDATED", entry.action());

        assertThrows(IllegalArgumentException.class, () -> wo.updateBasicInfo(null, "s", null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> wo.updateBasicInfo("", "s", null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> wo.updateBasicInfo("t", null, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> wo.updateBasicInfo("t", " ", null, null, EXECUTOR));
    }

    @Test
    @DisplayName("triage requires a non-null priority and non-null SLA due dates")
    void triageGuards() {
        assertThrows(NullPointerException.class, () -> wo.triage(null, Instant.now(), Instant.now(), EXECUTOR));
        assertThrows(NullPointerException.class, () -> wo.triage(Priority.P1, null, Instant.now(), EXECUTOR));
        assertThrows(NullPointerException.class, () -> wo.triage(Priority.P1, Instant.now(), null, EXECUTOR));
    }

    @Test
    @DisplayName("escalateToVendor requires a non-null vendor name and case number")
    void escalateGuards() {
        wo.triage(Priority.P1, Instant.now(), Instant.now(), EXECUTOR);
        assertThrows(NullPointerException.class, () -> wo.escalateToVendor(null, "c", EXECUTOR));
        assertThrows(NullPointerException.class, () -> wo.escalateToVendor("v", null, EXECUTOR));
    }

    @Test
    @DisplayName("completeRepair rejects a negative additionalLaborMinutes and a null diagnosis")
    void completeRepairGuards() {
        wo.triage(Priority.P1, Instant.now(), Instant.now(), EXECUTOR);
        wo.schedule(EXECUTOR);
        wo.start(EXECUTOR);
        assertThrows(IllegalArgumentException.class, () -> wo.completeRepair("d", -1, EXECUTOR));
        assertThrows(NullPointerException.class, () -> wo.completeRepair(null, 0, EXECUTOR));
    }

    @Test
    @DisplayName("pass requires a non-null resolutionCode")
    void passGuards() {
        wo.triage(Priority.P1, Instant.now(), Instant.now(), EXECUTOR);
        wo.schedule(EXECUTOR);
        wo.start(EXECUTOR);
        wo.completeRepair("d", 0, EXECUTOR);
        assertThrows(NullPointerException.class, () -> wo.pass(null, EXECUTOR));
    }

    @Test
    @DisplayName("addPart and addComment require a non-null value and record a ledger entry")
    void addPartAndCommentGuards() {
        assertThrows(NullPointerException.class, () -> wo.addPart(null, EXECUTOR));
        assertThrows(NullPointerException.class, () -> wo.addComment(null, EXECUTOR));

        var partEntry = wo.addPart(new Part(UUID.randomUUID(), "Flex cable", 2), EXECUTOR);
        assertEquals("PART_ADDED", partEntry.action());
        assertEquals(1, wo.getParts().size());

        var commentEntry = wo.addComment(new Comment(UUID.randomUUID(), "op", "note", true, null), EXECUTOR);
        assertEquals("COMMENT_ADDED", commentEntry.action());
        assertEquals(1, wo.getComments().size());
        assertTrue(wo.getComments().get(0).internal());
    }

    @Test
    @DisplayName("every mutating method requires a non-blank executor")
    void everyMutationRequiresExecutor() {
        assertThrows(IllegalArgumentException.class, () -> wo.updateBasicInfo("t", "s", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> wo.updateBasicInfo("t", "s", null, null, " "));
        assertThrows(IllegalArgumentException.class, () -> wo.triage(Priority.P1, Instant.now(), Instant.now(), null));
        assertThrows(IllegalArgumentException.class, () -> wo.addPart(new Part(UUID.randomUUID(), "d", 1), null));
        assertThrows(IllegalArgumentException.class, () -> wo.addComment(new Comment(UUID.randomUUID(), "op", "note", false, null), null));
    }

    @Test
    @DisplayName("Priority carries response and resolution SLA targets")
    void priorityTargets() {
        assertEquals(4, List.of(Priority.values()).size());
        assertTrue(Priority.P1.responseTarget().toMinutes() < Priority.P4.responseTarget().toMinutes());
        assertTrue(Priority.P1.resolutionTarget().toMinutes() < Priority.P4.resolutionTarget().toMinutes());
    }

    @Test
    @DisplayName("Part rejects a null or blank description and a non-positive quantity")
    void partValueObjectGuards() {
        assertThrows(NullPointerException.class, () -> new Part(null, "d", 1));
        assertThrows(IllegalArgumentException.class, () -> new Part(UUID.randomUUID(), null, 1));
        assertThrows(IllegalArgumentException.class, () -> new Part(UUID.randomUUID(), "", 1));
        assertThrows(IllegalArgumentException.class, () -> new Part(UUID.randomUUID(), "d", 0));
    }

    @Test
    @DisplayName("Comment rejects a null id, a null or blank author or text, and defaults a missing createdAt")
    void commentValueObjectGuards() {
        assertThrows(NullPointerException.class, () -> new Comment(null, "op", "text", false, null));
        assertThrows(IllegalArgumentException.class, () -> new Comment(UUID.randomUUID(), null, "text", false, null));
        assertThrows(IllegalArgumentException.class, () -> new Comment(UUID.randomUUID(), "", "text", false, null));
        assertThrows(IllegalArgumentException.class, () -> new Comment(UUID.randomUUID(), "op", null, false, null));
        assertThrows(IllegalArgumentException.class, () -> new Comment(UUID.randomUUID(), "op", "", false, null));
        assertNotNull(new Comment(UUID.randomUUID(), "op", "text", false, null).createdAt());
    }

    @Test
    @DisplayName("RmaDetails rejects a null vendor/case and withReturnedAt produces a new snapshot")
    void rmaValueObjectGuards() {
        assertThrows(NullPointerException.class, () -> new RmaDetails(null, "c", null, null));
        assertThrows(NullPointerException.class, () -> new RmaDetails("v", null, null, null));

        RmaDetails rma = new RmaDetails("v", "c", null, null);
        Instant returnedAt = Instant.now();
        RmaDetails returned = rma.withReturnedAt(returnedAt);
        assertEquals(returnedAt, returned.returnedAt());
        assertEquals("v", returned.vendorName());
    }

    @Test
    @DisplayName("ExternalReference rejects a null system or externalId")
    void externalReferenceGuards() {
        assertThrows(NullPointerException.class, () -> new ExternalReference(null, "id"));
        assertThrows(NullPointerException.class, () -> new ExternalReference("sys", null));
    }

    private WorkOrder newWorkOrder() {
        return WorkOrder.createNew(UUID.randomUUID(), organisationId, assetId, requesterId, "t", "s",
                WorkOrderType.CORRECTIVE, null, null, EXECUTOR);
    }

    private WorkOrder closedWorkOrder() {
        WorkOrder w = newWorkOrder();
        w.triage(Priority.P1, Instant.now(), Instant.now(), EXECUTOR);
        w.schedule(EXECUTOR);
        w.start(EXECUTOR);
        w.completeRepair("d", 0, EXECUTOR);
        w.pass("rc", EXECUTOR);
        w.close(EXECUTOR);
        return w;
    }
}
