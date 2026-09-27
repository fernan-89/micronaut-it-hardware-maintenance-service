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
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Infrastructure-specific representation of the WorkOrder Aggregate for MongoDB. Keeps the pure
 * Domain Model free of persistence annotations, same pattern as {@code AssetDocument}/{@code SiteDocument}.
 */
@Introspected
public class WorkOrderDocument {

    @BsonId
    private UUID id;

    private UUID organisationId;
    private UUID assetId;
    private UUID requesterId;
    private String title;
    private String symptom;
    private String diagnosis;
    private String resolutionCode;
    private String type;
    private String priority;
    private String status;
    private String location;
    private UUID loanerAssetId;
    private List<PartDocument> parts = new ArrayList<>();
    private int laborMinutes;
    private RmaDocument rma;
    private ExternalReferenceDocument externalReference;
    private List<CommentDocument> comments = new ArrayList<>();
    private Instant slaResponseDueAt;
    private Instant slaResolutionDueAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }
    public UUID getRequesterId() { return requesterId; }
    public void setRequesterId(UUID requesterId) { this.requesterId = requesterId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSymptom() { return symptom; }
    public void setSymptom(String symptom) { this.symptom = symptom; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getResolutionCode() { return resolutionCode; }
    public void setResolutionCode(String resolutionCode) { this.resolutionCode = resolutionCode; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public UUID getLoanerAssetId() { return loanerAssetId; }
    public void setLoanerAssetId(UUID loanerAssetId) { this.loanerAssetId = loanerAssetId; }
    public List<PartDocument> getParts() { return parts; }
    public void setParts(List<PartDocument> parts) { this.parts = parts; }
    public int getLaborMinutes() { return laborMinutes; }
    public void setLaborMinutes(int laborMinutes) { this.laborMinutes = laborMinutes; }
    public RmaDocument getRma() { return rma; }
    public void setRma(RmaDocument rma) { this.rma = rma; }
    public ExternalReferenceDocument getExternalReference() { return externalReference; }
    public void setExternalReference(ExternalReferenceDocument externalReference) { this.externalReference = externalReference; }
    public List<CommentDocument> getComments() { return comments; }
    public void setComments(List<CommentDocument> comments) { this.comments = comments; }
    public Instant getSlaResponseDueAt() { return slaResponseDueAt; }
    public void setSlaResponseDueAt(Instant slaResponseDueAt) { this.slaResponseDueAt = slaResponseDueAt; }
    public Instant getSlaResolutionDueAt() { return slaResolutionDueAt; }
    public void setSlaResolutionDueAt(Instant slaResolutionDueAt) { this.slaResolutionDueAt = slaResolutionDueAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    @Introspected
    public record PartDocument(UUID partId, String description, int quantity) {
        public static PartDocument fromDomain(Part part) { return new PartDocument(part.partId(), part.description(), part.quantity()); }
        Part toDomain() { return new Part(partId, description, quantity); }
    }

    @Introspected
    public record CommentDocument(UUID commentId, String author, String text, boolean internal, Instant createdAt) {
        public static CommentDocument fromDomain(Comment comment) {
            return new CommentDocument(comment.commentId(), comment.author(), comment.text(), comment.internal(), comment.createdAt());
        }
        Comment toDomain() { return new Comment(commentId, author, text, internal, createdAt); }
    }

    @Introspected
    public record RmaDocument(String vendorName, String caseNumber, Instant shippedAt, Instant returnedAt) {
        public static RmaDocument fromDomain(RmaDetails rma) {
            return rma == null ? null : new RmaDocument(rma.vendorName(), rma.caseNumber(), rma.shippedAt(), rma.returnedAt());
        }
        RmaDetails toDomain() { return new RmaDetails(vendorName, caseNumber, shippedAt, returnedAt); }
    }

    @Introspected
    public record ExternalReferenceDocument(String system, String externalId) {
        static ExternalReferenceDocument fromDomain(ExternalReference ref) {
            return ref == null ? null : new ExternalReferenceDocument(ref.system(), ref.externalId());
        }
        ExternalReference toDomain() { return new ExternalReference(system, externalId); }
    }

    @Introspected
    public record AuditEntryDocument(Instant occurredAt, String action, String executor,
                                      String fromStatus, String toStatus, String detail) {

        public static AuditEntryDocument fromDomain(WorkOrderAuditEntry entry) {
            return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(),
                    entry.fromStatus() != null ? entry.fromStatus().name() : null, entry.toStatus().name(), entry.detail());
        }

        // toStatus has no null branch here: fromDomain above always writes entry.toStatus().name()
        // unconditionally, so a document this application wrote never has a null toStatus.
        WorkOrderAuditEntry toDomain() {
            return new WorkOrderAuditEntry(occurredAt, action, executor,
                    fromStatus != null ? WorkOrderStatus.valueOf(fromStatus) : null,
                    WorkOrderStatus.valueOf(toStatus), detail);
        }
    }

    public static final class WorkOrderPersistenceMapper {

        private WorkOrderPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static WorkOrderDocument toDocument(WorkOrder workOrder) {
            WorkOrderDocument doc = new WorkOrderDocument();
            doc.setId(workOrder.getId());
            doc.setOrganisationId(workOrder.getOrganisationId());
            doc.setAssetId(workOrder.getAssetId());
            doc.setRequesterId(workOrder.getRequesterId());
            doc.setTitle(workOrder.getTitle());
            doc.setSymptom(workOrder.getSymptom());
            doc.setDiagnosis(workOrder.getDiagnosis());
            doc.setResolutionCode(workOrder.getResolutionCode());
            doc.setType(workOrder.getType().name());
            doc.setPriority(workOrder.getPriority() != null ? workOrder.getPriority().name() : null);
            doc.setStatus(workOrder.getStatus().name());
            doc.setLocation(workOrder.getLocation());
            doc.setLoanerAssetId(workOrder.getLoanerAssetId());
            doc.setParts(workOrder.getParts().stream().map(PartDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            doc.setLaborMinutes(workOrder.getLaborMinutes());
            doc.setRma(RmaDocument.fromDomain(workOrder.getRma()));
            doc.setExternalReference(ExternalReferenceDocument.fromDomain(workOrder.getExternalReference()));
            doc.setComments(workOrder.getComments().stream().map(CommentDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            doc.setSlaResponseDueAt(workOrder.getSlaResponseDueAt());
            doc.setSlaResolutionDueAt(workOrder.getSlaResolutionDueAt());
            doc.setCreatedAt(workOrder.getCreatedAt());
            doc.setUpdatedAt(workOrder.getUpdatedAt());
            doc.setAuditTrail(workOrder.getAuditTrail().stream().map(AuditEntryDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static WorkOrder toDomain(WorkOrderDocument doc) {
            WorkOrderStatus status = doc.getStatus() != null ? WorkOrderStatus.valueOf(doc.getStatus()) : WorkOrderStatus.REQUESTED;
            Priority priority = doc.getPriority() != null ? Priority.valueOf(doc.getPriority()) : null;
            List<Part> parts = doc.getParts() != null
                    ? doc.getParts().stream().map(PartDocument::toDomain).collect(Collectors.toList()) : new ArrayList<>();
            List<Comment> comments = doc.getComments() != null
                    ? doc.getComments().stream().map(CommentDocument::toDomain).collect(Collectors.toList()) : new ArrayList<>();
            List<WorkOrderAuditEntry> trail = doc.getAuditTrail() != null
                    ? doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList()) : new ArrayList<>();

            return WorkOrder.reconstitute(
                    doc.getId(), doc.getOrganisationId(), doc.getAssetId(), doc.getRequesterId(),
                    doc.getTitle(), doc.getSymptom(), doc.getDiagnosis(), doc.getResolutionCode(),
                    WorkOrderType.valueOf(doc.getType()), priority, status, doc.getLocation(), doc.getLoanerAssetId(),
                    parts, doc.getLaborMinutes(), doc.getRma() != null ? doc.getRma().toDomain() : null,
                    doc.getExternalReference() != null ? doc.getExternalReference().toDomain() : null,
                    comments, doc.getSlaResponseDueAt(), doc.getSlaResolutionDueAt(),
                    doc.getCreatedAt(), doc.getUpdatedAt(), trail
            );
        }
    }
}
