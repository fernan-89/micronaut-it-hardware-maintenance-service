package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateWorkOrderRequest;
import com.thinklab.domain.model.WorkOrder;
import com.thinklab.domain.model.WorkOrder.Comment;
import com.thinklab.domain.model.WorkOrder.WorkOrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkOrderMapperTest {

    @Test
    @DisplayName("toDomain maps every InitiateWorkOrderRequest field, including an external reference when both parts are present")
    void toDomainWithExternalReference() {
        InitiateWorkOrderRequest request = new InitiateWorkOrderRequest(UUID.randomUUID(), null, "t", "s",
                WorkOrderType.UPGRADE, "loc", "ServiceNow", "INC0001");

        WorkOrder workOrder = WorkOrderMapper.toDomain(request, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "op-1");

        assertEquals("t", workOrder.getTitle());
        assertEquals(WorkOrderType.UPGRADE, workOrder.getType());
        assertEquals("ServiceNow", workOrder.getExternalReference().system());
    }

    @Test
    @DisplayName("toDomain leaves externalReference null when only one of system/externalId is present")
    void toDomainPartialExternalReference() {
        InitiateWorkOrderRequest request = new InitiateWorkOrderRequest(UUID.randomUUID(), null, "t", "s",
                WorkOrderType.CORRECTIVE, null, "ServiceNow", null);

        WorkOrder workOrder = WorkOrderMapper.toDomain(request, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "op-1");

        assertNull(workOrder.getExternalReference());
    }

    @Test
    @DisplayName("toResponse strips internal comments when includeInternalComments is false, keeps them otherwise")
    void toResponseCommentScoping() {
        WorkOrder workOrder = WorkOrder.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "t", "s", WorkOrderType.CORRECTIVE, null, null, "op-1");
        workOrder.addComment(new Comment(UUID.randomUUID(), "op-1", "internal note", true, Instant.now()), "op-1");
        workOrder.addComment(new Comment(UUID.randomUUID(), "requester", "visible note", false, Instant.now()), "requester");

        assertEquals(1, WorkOrderMapper.toResponse(workOrder, false).comments().size());
        assertEquals(2, WorkOrderMapper.toResponse(workOrder, true).comments().size());
        assertTrue(WorkOrderMapper.toResponse(workOrder, false).comments().get(0).text().equals("visible note"));
    }

    @Test
    @DisplayName("toResponse maps rma and externalReference to null when absent, and populates every scalar field")
    void toResponseScalarFields() {
        WorkOrder workOrder = WorkOrder.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "t", "s", WorkOrderType.PREVENTIVE, "loc", null, "op-1");

        var response = WorkOrderMapper.toResponse(workOrder, true);

        assertEquals(workOrder.getId(), response.id());
        assertEquals("PREVENTIVE", response.type());
        assertEquals("REQUESTED", response.status());
        assertNull(response.rma());
        assertNull(response.externalReference());
        assertNull(response.priority());
    }

    @Test
    @DisplayName("toResponse populates parts, rma and externalReference when present")
    void toResponseWithPartsRmaAndExternalReference() {
        WorkOrder workOrder = WorkOrder.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "t", "s", WorkOrderType.CORRECTIVE, null, new com.thinklab.domain.model.WorkOrder.ExternalReference("ServiceNow", "INC0001"), "op-1");
        workOrder.addPart(new com.thinklab.domain.model.WorkOrder.Part(UUID.randomUUID(), "Flex cable", 2), "op-1");
        workOrder.triage(com.thinklab.domain.model.WorkOrder.Priority.P1, Instant.now(), Instant.now(), "op-1");
        workOrder.escalateToVendor("Acme", "CASE-1", "op-1");

        var response = WorkOrderMapper.toResponse(workOrder, true);

        assertEquals(1, response.parts().size());
        assertEquals("Flex cable", response.parts().get(0).description());
        assertEquals(2, response.parts().get(0).quantity());
        assertEquals("Acme", response.rma().vendorName());
        assertEquals("CASE-1", response.rma().caseNumber());
        assertEquals("ServiceNow", response.externalReference().system());
        assertEquals("INC0001", response.externalReference().externalId());
    }

    @Test
    @DisplayName("toResponse(WorkOrderAuditEntry) maps a null fromStatus and a non-null toStatus")
    void auditEntryMapping() {
        WorkOrder workOrder = WorkOrder.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "t", "s", WorkOrderType.CORRECTIVE, null, null, "op-1");

        var response = WorkOrderMapper.toResponse(workOrder.getAuditTrail().get(0));

        assertNull(response.fromStatus());
        assertEquals("REQUESTED", response.toStatus());
        assertEquals("INITIATED", response.action());
    }

    @Test
    @DisplayName("toResponse(WorkOrderAuditEntry) also maps a non-null fromStatus, for a real transition entry")
    void auditEntryMappingWithFromStatus() {
        WorkOrder workOrder = WorkOrder.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "t", "s", WorkOrderType.CORRECTIVE, null, null, "op-1");
        workOrder.triage(com.thinklab.domain.model.WorkOrder.Priority.P1, Instant.now(), Instant.now(), "op-1");

        var response = WorkOrderMapper.toResponse(workOrder.getAuditTrail().get(1));

        assertEquals("REQUESTED", response.fromStatus());
        assertEquals("TRIAGED", response.toStatus());
    }

    @Test
    @DisplayName("the mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        var constructor = WorkOrderMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
        assertTrue(ex.getCause() instanceof UnsupportedOperationException);
    }
}
