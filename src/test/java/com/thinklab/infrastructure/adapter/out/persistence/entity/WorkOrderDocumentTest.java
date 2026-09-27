package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.model.WorkOrder.Comment;
import com.thinklab.domain.model.WorkOrder.ExternalReference;
import com.thinklab.domain.model.WorkOrder.Part;
import com.thinklab.domain.model.WorkOrder.Priority;
import com.thinklab.domain.model.WorkOrder.RmaDetails;
import com.thinklab.domain.model.WorkOrder.WorkOrderAuditEntry;
import com.thinklab.domain.model.WorkOrder.WorkOrderStatus;
import com.thinklab.domain.model.WorkOrder.WorkOrderType;
import com.thinklab.infrastructure.adapter.out.persistence.entity.WorkOrderDocument.WorkOrderPersistenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkOrderDocumentTest {

    @Test
    @DisplayName("toDocument / toDomain round-trips the whole aggregate, including parts, comments, RMA and external reference")
    void roundTrip() {
        UUID id = UUID.randomUUID();
        UUID organisationId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        Part part = new Part(UUID.randomUUID(), "Flex cable", 2);
        Comment comment = new Comment(UUID.randomUUID(), "op-1", "note", true, Instant.now());
        RmaDetails rma = new RmaDetails("Acme", "CASE-1", Instant.now(), null);
        ExternalReference externalReference = new ExternalReference("ServiceNow", "INC0001");
        WorkOrderAuditEntry initiated = new WorkOrderAuditEntry(Instant.now(), "INITIATED", "op-1", null, WorkOrderStatus.REQUESTED, "filed");
        WorkOrderAuditEntry started = new WorkOrderAuditEntry(Instant.now(), "REPAIR_STARTED", "op-1", WorkOrderStatus.SCHEDULED, WorkOrderStatus.IN_REPAIR, "started");

        WorkOrder workOrder = WorkOrder.reconstitute(id, organisationId, assetId, requesterId, "Broken screen", "Flickers",
                "Diagnosed panel fault", "RC-1", WorkOrderType.CORRECTIVE, Priority.P2, WorkOrderStatus.IN_REPAIR,
                "Room 101", UUID.randomUUID(), List.of(part), 45, rma, externalReference, List.of(comment),
                Instant.now(), Instant.now(), Instant.now(), Instant.now(), List.of(initiated, started));

        WorkOrderDocument doc = WorkOrderPersistenceMapper.toDocument(workOrder);
        WorkOrder restored = WorkOrderPersistenceMapper.toDomain(doc);

        assertEquals(id, doc.getId());
        assertEquals("IN_REPAIR", doc.getStatus());
        assertEquals(2, restored.getAuditTrail().size());
        assertNull(restored.getAuditTrail().get(0).fromStatus());
        assertEquals(WorkOrderStatus.REQUESTED, restored.getAuditTrail().get(0).toStatus());
        assertEquals(WorkOrderStatus.SCHEDULED, restored.getAuditTrail().get(1).fromStatus());
        assertEquals(id, restored.getId());
        assertEquals(organisationId, restored.getOrganisationId());
        assertEquals(assetId, restored.getAssetId());
        assertEquals(requesterId, restored.getRequesterId());
        assertEquals(WorkOrderStatus.IN_REPAIR, restored.getStatus());
        assertEquals(Priority.P2, restored.getPriority());
        assertEquals(1, restored.getParts().size());
        assertEquals("Flex cable", restored.getParts().get(0).description());
        assertEquals(1, restored.getComments().size());
        assertTrue(restored.getComments().get(0).internal());
        assertEquals("Acme", restored.getRma().vendorName());
        assertEquals("ServiceNow", restored.getExternalReference().system());
        assertEquals(45, restored.getLaborMinutes());
    }

    @Test
    @DisplayName("toDocument leaves rma and externalReference null when absent")
    void nullRmaAndExternalReference() {
        WorkOrder workOrder = WorkOrder.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "t", "s", WorkOrderType.CORRECTIVE, null, null, "op-1");

        WorkOrderDocument doc = WorkOrderPersistenceMapper.toDocument(workOrder);

        assertNull(doc.getRma());
        assertNull(doc.getExternalReference());
    }

    @Test
    @DisplayName("toDomain defaults a missing status to REQUESTED and missing lists to empty")
    void defaultsWhenFieldsMissing() {
        WorkOrderDocument doc = new WorkOrderDocument();
        doc.setId(UUID.randomUUID());
        doc.setOrganisationId(UUID.randomUUID());
        doc.setAssetId(UUID.randomUUID());
        doc.setRequesterId(UUID.randomUUID());
        doc.setTitle("t");
        doc.setType("CORRECTIVE");
        doc.setParts(null);
        doc.setComments(null);
        doc.setAuditTrail(null);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());

        WorkOrder restored = WorkOrderPersistenceMapper.toDomain(doc);

        assertEquals(WorkOrderStatus.REQUESTED, restored.getStatus());
        assertTrue(restored.getParts().isEmpty());
        assertTrue(restored.getComments().isEmpty());
        assertTrue(restored.getAuditTrail().isEmpty());
    }

    @Test
    @DisplayName("the persistence mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        var constructor = WorkOrderPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
        assertTrue(ex.getCause() instanceof UnsupportedOperationException);
    }

    @Test
    @DisplayName("plain accessors expose what was set (POJO codec contract)")
    void accessors() {
        WorkOrderDocument doc = new WorkOrderDocument();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        doc.setId(id);
        doc.setDiagnosis("d");
        doc.setResolutionCode("rc");
        doc.setPriority("P1");
        doc.setLocation("loc");
        doc.setLoanerAssetId(id);
        doc.setLaborMinutes(10);
        doc.setSlaResponseDueAt(now);
        doc.setSlaResolutionDueAt(now);
        doc.setSymptom("sym");

        assertEquals(id, doc.getId());
        assertEquals("d", doc.getDiagnosis());
        assertEquals("rc", doc.getResolutionCode());
        assertEquals("P1", doc.getPriority());
        assertEquals("loc", doc.getLocation());
        assertEquals(id, doc.getLoanerAssetId());
        assertEquals(10, doc.getLaborMinutes());
        assertEquals(now, doc.getSlaResponseDueAt());
        assertEquals(now, doc.getSlaResolutionDueAt());
        assertEquals("sym", doc.getSymptom());
    }
}
