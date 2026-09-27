package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidWorkOrderStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Core Domain Model representing the WorkOrder Aggregate Root (BIAN Service Domain:
 * {@code it-hardware-maintenance}).
 *
 * <p><b>BIAN Alignment (ADR-013):</b> the Control Record of the hardware-maintenance Service Domain
 * — a repair/maintenance ticket against an Asset from the IT Asset Registry, scoped to an
 * Organisation and filed either by an operator or, self-service, by the {@code REQUESTER} role
 * (ADR-032). Every route is a named Behavior Qualifier rather than a generic
 * {@code control/{status}} endpoint, because several transitions carry extra data of their own
 * (triage sets the priority and computes the SLA targets, escalation records the vendor/RMA case).
 *
 * <p><b>Forensic Audit Ledger:</b> every mutation appends an immutable {@link WorkOrderAuditEntry},
 * mirroring the Asset Registry's {@code AssetAuditEntry} pattern.
 *
 * <p><b>SLA behind a calendar port (ADR-033):</b> this aggregate never computes a due date itself —
 * {@link #triage} takes the already-computed {@code slaResponseDueAt}/{@code slaResolutionDueAt}
 * from its caller, which derives them from {@link Priority}'s target durations through the
 * application layer's {@code CalendarPort}. Keeping the port out of the domain layer means the
 * aggregate stays pure Java, framework- and calendar-implementation-agnostic.
 *
 * <p>Strictly pure Java. Agnostic of frameworks, databases, or web layers.
 */
public class WorkOrder {

    private final UUID id;
    private final UUID organisationId;
    private final UUID assetId;
    private final UUID requesterId;
    private String title;
    private String symptom;
    private String diagnosis;
    private String resolutionCode;
    private final WorkOrderType type;
    private Priority priority;
    private WorkOrderStatus status;
    private String location;
    private UUID loanerAssetId;
    private final List<Part> parts;
    private int laborMinutes;
    private RmaDetails rma;
    private final ExternalReference externalReference;
    private final List<Comment> comments;
    private Instant slaResponseDueAt;
    private Instant slaResolutionDueAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<WorkOrderAuditEntry> auditTrail;

    private WorkOrder(UUID id, UUID organisationId, UUID assetId, UUID requesterId, String title, String symptom,
                       WorkOrderType type, Priority priority, String location, ExternalReference externalReference,
                       String executor) {
        this.id = id;
        this.organisationId = organisationId;
        this.assetId = assetId;
        this.requesterId = requesterId;
        this.title = title;
        this.symptom = symptom;
        this.type = type;
        this.priority = priority;
        this.location = location;
        this.externalReference = externalReference;
        this.parts = new ArrayList<>();
        this.comments = new ArrayList<>();
        this.status = WorkOrderStatus.REQUESTED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new WorkOrderAuditEntry(this.createdAt, "INITIATED", executor, null, WorkOrderStatus.REQUESTED,
                "Work order filed."));
    }

    private WorkOrder(UUID id, UUID organisationId, UUID assetId, UUID requesterId, String title, String symptom,
                       String diagnosis, String resolutionCode, WorkOrderType type, Priority priority,
                       WorkOrderStatus status, String location, UUID loanerAssetId, List<Part> parts, int laborMinutes,
                       RmaDetails rma, ExternalReference externalReference, List<Comment> comments,
                       Instant slaResponseDueAt, Instant slaResolutionDueAt, Instant createdAt, Instant updatedAt,
                       List<WorkOrderAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.assetId = assetId;
        this.requesterId = requesterId;
        this.title = title;
        this.symptom = symptom;
        this.diagnosis = diagnosis;
        this.resolutionCode = resolutionCode;
        this.type = type;
        this.priority = priority;
        this.status = status != null ? status : WorkOrderStatus.REQUESTED;
        this.location = location;
        this.loanerAssetId = loanerAssetId;
        this.parts = parts != null ? new ArrayList<>(parts) : new ArrayList<>();
        this.laborMinutes = laborMinutes;
        this.rma = rma;
        this.externalReference = externalReference;
        this.comments = comments != null ? new ArrayList<>(comments) : new ArrayList<>();
        this.slaResponseDueAt = slaResponseDueAt;
        this.slaResolutionDueAt = slaResolutionDueAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    public static WorkOrder createNew(UUID id, UUID organisationId, UUID assetId, UUID requesterId, String title,
                                       String symptom, WorkOrderType type, String location,
                                       ExternalReference externalReference, String executor) {
        if (id == null || organisationId == null || assetId == null || requesterId == null || type == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Asset ID, Requester ID and Type are mandatory for WorkOrder creation.");
        }
        if (title == null || title.isBlank() || symptom == null || symptom.isBlank()) {
            throw new IllegalArgumentException("Title and Symptom are mandatory for WorkOrder creation.");
        }
        requireExecutor(executor);
        return new WorkOrder(id, organisationId, assetId, requesterId, title, symptom, type, null, location, externalReference, executor);
    }

    public static WorkOrder reconstitute(UUID id, UUID organisationId, UUID assetId, UUID requesterId, String title,
                                          String symptom, String diagnosis, String resolutionCode, WorkOrderType type,
                                          Priority priority, WorkOrderStatus status, String location, UUID loanerAssetId,
                                          List<Part> parts, int laborMinutes, RmaDetails rma, ExternalReference externalReference,
                                          List<Comment> comments, Instant slaResponseDueAt, Instant slaResolutionDueAt,
                                          Instant createdAt, Instant updatedAt, List<WorkOrderAuditEntry> auditTrail) {
        if (id == null || organisationId == null || assetId == null || requesterId == null || title == null || type == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Asset ID, Requester ID, Title and Type are mandatory to reconstitute a WorkOrder.");
        }
        return new WorkOrder(id, organisationId, assetId, requesterId, title, symptom, diagnosis, resolutionCode, type,
                priority, status, location, loanerAssetId, parts, laborMinutes, rma, externalReference, comments,
                slaResponseDueAt, slaResolutionDueAt, createdAt, updatedAt, auditTrail);
    }

    // --- Domain Behaviors ---

    /** Behavior Qualifier: {@code update}. Not available once the ticket is terminal. */
    public WorkOrderAuditEntry updateBasicInfo(String newTitle, String newSymptom, String newLocation, UUID newLoanerAssetId, String executor) {
        requireNotTerminal("update");
        if (newTitle == null || newTitle.isBlank() || newSymptom == null || newSymptom.isBlank()) {
            throw new IllegalArgumentException("Title and Symptom cannot be empty.");
        }
        requireExecutor(executor);
        this.title = newTitle;
        this.symptom = newSymptom;
        this.location = newLocation;
        this.loanerAssetId = newLoanerAssetId;
        return record("UPDATED", executor, "Basic information updated.");
    }

    /** Behavior Qualifier: {@code triage}. REQUESTED -&gt; TRIAGED. */
    public WorkOrderAuditEntry triage(Priority newPriority, Instant slaResponseDueAt, Instant slaResolutionDueAt, String executor) {
        requireStatus(WorkOrderStatus.REQUESTED);
        Objects.requireNonNull(newPriority, "Priority is mandatory to triage a WorkOrder.");
        this.priority = newPriority;
        this.slaResponseDueAt = Objects.requireNonNull(slaResponseDueAt, "slaResponseDueAt is mandatory to triage a WorkOrder.");
        this.slaResolutionDueAt = Objects.requireNonNull(slaResolutionDueAt, "slaResolutionDueAt is mandatory to triage a WorkOrder.");
        return transition(WorkOrderStatus.TRIAGED, "TRIAGED", executor, "Priority set to " + newPriority + ".");
    }

    /** Behavior Qualifier: {@code schedule}. TRIAGED -&gt; SCHEDULED. */
    public WorkOrderAuditEntry schedule(String executor) {
        requireStatus(WorkOrderStatus.TRIAGED);
        return transition(WorkOrderStatus.SCHEDULED, "SCHEDULED", executor, "Repair scheduled.");
    }

    /** Behavior Qualifier: {@code control/escalate}. TRIAGED -&gt; ESCALATED_TO_VENDOR. */
    public WorkOrderAuditEntry escalateToVendor(String vendorName, String caseNumber, String executor) {
        requireStatus(WorkOrderStatus.TRIAGED);
        this.rma = new RmaDetails(
                Objects.requireNonNull(vendorName, "vendorName is mandatory to escalate to a vendor."),
                Objects.requireNonNull(caseNumber, "caseNumber is mandatory to escalate to a vendor."),
                Instant.now(), null);
        return transition(WorkOrderStatus.ESCALATED_TO_VENDOR, "ESCALATED_TO_VENDOR", executor,
                String.format("Escalated to vendor [%s], case [%s].", vendorName, caseNumber));
    }

    /** Behavior Qualifier: {@code control/start}. SCHEDULED -&gt; IN_REPAIR. */
    public WorkOrderAuditEntry start(String executor) {
        requireStatus(WorkOrderStatus.SCHEDULED);
        return transition(WorkOrderStatus.IN_REPAIR, "REPAIR_STARTED", executor, "Repair started.");
    }

    /** Behavior Qualifier: {@code control/await-parts}. IN_REPAIR -&gt; AWAITING_PARTS. */
    public WorkOrderAuditEntry awaitParts(String executor) {
        requireStatus(WorkOrderStatus.IN_REPAIR);
        return transition(WorkOrderStatus.AWAITING_PARTS, "AWAITING_PARTS", executor, "Repair paused: awaiting parts.");
    }

    /** Behavior Qualifier: {@code control/resume}. AWAITING_PARTS -&gt; IN_REPAIR. */
    public WorkOrderAuditEntry resume(String executor) {
        requireStatus(WorkOrderStatus.AWAITING_PARTS);
        return transition(WorkOrderStatus.IN_REPAIR, "REPAIR_RESUMED", executor, "Repair resumed: parts available.");
    }

    /** Behavior Qualifier: {@code control/vendor-returned}. ESCALATED_TO_VENDOR -&gt; IN_REPAIR. */
    public WorkOrderAuditEntry vendorReturned(String executor) {
        requireStatus(WorkOrderStatus.ESCALATED_TO_VENDOR);
        this.rma = this.rma.withReturnedAt(Instant.now());
        return transition(WorkOrderStatus.IN_REPAIR, "VENDOR_RETURNED", executor, "Asset returned by the vendor.");
    }

    /** Behavior Qualifier: {@code control/complete-repair}. IN_REPAIR -&gt; QUALITY_CHECK. */
    public WorkOrderAuditEntry completeRepair(String newDiagnosis, int additionalLaborMinutes, String executor) {
        requireStatus(WorkOrderStatus.IN_REPAIR);
        if (additionalLaborMinutes < 0) {
            throw new IllegalArgumentException("additionalLaborMinutes cannot be negative.");
        }
        this.diagnosis = Objects.requireNonNull(newDiagnosis, "diagnosis is mandatory to complete a repair.");
        this.laborMinutes += additionalLaborMinutes;
        return transition(WorkOrderStatus.QUALITY_CHECK, "REPAIR_COMPLETED", executor, "Repair completed, awaiting quality check.");
    }

    /** Behavior Qualifier: {@code control/pass}. QUALITY_CHECK -&gt; RESOLVED. */
    public WorkOrderAuditEntry pass(String newResolutionCode, String executor) {
        requireStatus(WorkOrderStatus.QUALITY_CHECK);
        this.resolutionCode = Objects.requireNonNull(newResolutionCode, "resolutionCode is mandatory to resolve a WorkOrder.");
        return transition(WorkOrderStatus.RESOLVED, "QUALITY_CHECK_PASSED", executor, "Quality check passed.");
    }

    /** Behavior Qualifier: {@code control/fail}. QUALITY_CHECK -&gt; IN_REPAIR. */
    public WorkOrderAuditEntry fail(String executor) {
        requireStatus(WorkOrderStatus.QUALITY_CHECK);
        return transition(WorkOrderStatus.IN_REPAIR, "QUALITY_CHECK_FAILED", executor, "Quality check failed, back to repair.");
    }

    /** Behavior Qualifier: {@code control/close}. RESOLVED -&gt; CLOSED (terminal). */
    public WorkOrderAuditEntry close(String executor) {
        requireStatus(WorkOrderStatus.RESOLVED);
        return transition(WorkOrderStatus.CLOSED, "CLOSED", executor, "Closed and confirmed by the requester.");
    }

    /** Behavior Qualifier: {@code control/reopen}. RESOLVED -&gt; TRIAGED. */
    public WorkOrderAuditEntry reopen(String executor) {
        requireStatus(WorkOrderStatus.RESOLVED);
        return transition(WorkOrderStatus.TRIAGED, "REOPENED", executor, "Reopened: requester disputed the resolution.");
    }

    /**
     * Behavior Qualifier: {@code control/cancel} (terminal, replaces DELETE). Only legal before any
     * repair work has actually started.
     */
    public WorkOrderAuditEntry cancel(String executor) {
        requireStatus(WorkOrderStatus.REQUESTED, WorkOrderStatus.TRIAGED);
        return transition(WorkOrderStatus.CANCELLED, "CANCELLED", executor, "Cancelled before repair started.");
    }

    /** Behavior Qualifier: {@code part/initiate}. */
    public WorkOrderAuditEntry addPart(Part part, String executor) {
        requireNotTerminal("add a part to");
        Objects.requireNonNull(part, "part is mandatory.");
        this.parts.add(part);
        return record("PART_ADDED", executor, String.format("Part [%s] x%d added.", part.description(), part.quantity()));
    }

    /** Behavior Qualifier: {@code comment/initiate}. */
    public WorkOrderAuditEntry addComment(Comment comment, String executor) {
        Objects.requireNonNull(comment, "comment is mandatory.");
        this.comments.add(comment);
        return record("COMMENT_ADDED", executor, "Comment added.");
    }

    // --- Internal helpers ---

    private WorkOrderAuditEntry transition(WorkOrderStatus newStatus, String action, String executor, String detail) {
        requireExecutor(executor);
        WorkOrderStatus previous = this.status;
        this.status = newStatus;
        this.updatedAt = Instant.now();
        WorkOrderAuditEntry entry = new WorkOrderAuditEntry(this.updatedAt, action, executor, previous, newStatus, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private WorkOrderAuditEntry record(String action, String executor, String detail) {
        requireExecutor(executor);
        this.updatedAt = Instant.now();
        WorkOrderAuditEntry entry = new WorkOrderAuditEntry(this.updatedAt, action, executor, this.status, this.status, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private void requireStatus(WorkOrderStatus... allowed) {
        if (Arrays.asList(allowed).contains(this.status)) {
            return;
        }
        throw new InvalidWorkOrderStatusException(String.format(
                "Illegal transition: WorkOrder is [%s], expected one of %s.", this.status, Arrays.toString(allowed)));
    }

    private void requireNotTerminal(String operation) {
        if (this.status == WorkOrderStatus.CLOSED || this.status == WorkOrderStatus.CANCELLED) {
            throw new InvalidWorkOrderStatusException(String.format(
                    "Compliance Violation: cannot %s a %s WorkOrder; the lifecycle is terminal.", operation, this.status));
        }
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable WorkOrder mutations.");
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public UUID getAssetId() { return assetId; }
    public UUID getRequesterId() { return requesterId; }
    public String getTitle() { return title; }
    public String getSymptom() { return symptom; }
    public String getDiagnosis() { return diagnosis; }
    public String getResolutionCode() { return resolutionCode; }
    public WorkOrderType getType() { return type; }
    public Priority getPriority() { return priority; }
    public WorkOrderStatus getStatus() { return status; }
    public String getLocation() { return location; }
    public UUID getLoanerAssetId() { return loanerAssetId; }
    public List<Part> getParts() { return Collections.unmodifiableList(parts); }
    public int getLaborMinutes() { return laborMinutes; }
    public RmaDetails getRma() { return rma; }
    public ExternalReference getExternalReference() { return externalReference; }
    public List<Comment> getComments() { return Collections.unmodifiableList(comments); }
    public Instant getSlaResponseDueAt() { return slaResponseDueAt; }
    public Instant getSlaResolutionDueAt() { return slaResolutionDueAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<WorkOrderAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    // --- Nested Value Objects ---

    public enum WorkOrderType { CORRECTIVE, PREVENTIVE, WARRANTY_RMA, UPGRADE, DECOMMISSION_PREP }

    /**
     * Immutable forensic ledger entry, mirroring the Asset Registry's {@code AssetAuditEntry} pattern.
     *
     * @param fromStatus status before the action ({@code null} for the initiating entry)
     * @param toStatus   status after the action (equal to {@code fromStatus} for non-transition actions)
     */
    public record WorkOrderAuditEntry(Instant occurredAt, String action, String executor,
                                       WorkOrderStatus fromStatus, WorkOrderStatus toStatus, String detail) {}

    /** Response/resolution SLA targets, consumed by the application layer's calendar port. */
    public enum Priority {
        P1(Duration.ofHours(1), Duration.ofHours(4)),
        P2(Duration.ofHours(4), Duration.ofHours(8)),
        P3(Duration.ofHours(8), Duration.ofHours(24)),
        P4(Duration.ofHours(24), Duration.ofHours(72));

        private final Duration responseTarget;
        private final Duration resolutionTarget;

        Priority(Duration responseTarget, Duration resolutionTarget) {
            this.responseTarget = responseTarget;
            this.resolutionTarget = resolutionTarget;
        }

        public Duration responseTarget() { return responseTarget; }
        public Duration resolutionTarget() { return resolutionTarget; }
    }

    /**
     * <pre>
     * REQUESTED -&gt; TRIAGED -&gt; SCHEDULED -&gt; IN_REPAIR &lt;-&gt; AWAITING_PARTS
     *                  |                          |
     *                  v                          v
     *          ESCALATED_TO_VENDOR -&gt; IN_REPAIR  QUALITY_CHECK -&gt; RESOLVED -&gt; CLOSED (terminal)
     *                                              |  ^                |
     *                                              v  |                v
     *                                          IN_REPAIR          TRIAGED (reopen)
     * REQUESTED, TRIAGED -&gt; CANCELLED (terminal)
     * </pre>
     */
    public enum WorkOrderStatus {
        REQUESTED, TRIAGED, SCHEDULED, ESCALATED_TO_VENDOR, IN_REPAIR, AWAITING_PARTS,
        QUALITY_CHECK, RESOLVED, CLOSED, CANCELLED
    }

    public record Part(UUID partId, String description, int quantity) {
        public Part {
            Objects.requireNonNull(partId, "partId cannot be null.");
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("Part description cannot be blank.");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Part quantity must be positive.");
            }
        }
    }

    /** @param internal invisible to a REQUESTER-scoped read (application-layer filtering). */
    public record Comment(UUID commentId, String author, String text, boolean internal, Instant createdAt) {
        public Comment {
            Objects.requireNonNull(commentId, "commentId cannot be null.");
            if (author == null || author.isBlank()) {
                throw new IllegalArgumentException("Comment author cannot be blank.");
            }
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Comment text cannot be blank.");
            }
            createdAt = createdAt != null ? createdAt : Instant.now();
        }
    }

    public record RmaDetails(String vendorName, String caseNumber, Instant shippedAt, Instant returnedAt) {
        public RmaDetails {
            Objects.requireNonNull(vendorName, "vendorName cannot be null.");
            Objects.requireNonNull(caseNumber, "caseNumber cannot be null.");
        }

        public RmaDetails withReturnedAt(Instant newReturnedAt) {
            return new RmaDetails(vendorName, caseNumber, shippedAt, newReturnedAt);
        }
    }

    public record ExternalReference(String system, String externalId) {
        public ExternalReference {
            Objects.requireNonNull(system, "system cannot be null.");
            Objects.requireNonNull(externalId, "externalId cannot be null.");
        }
    }
}
