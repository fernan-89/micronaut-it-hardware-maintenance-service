package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.CompleteRepairRequest;
import com.thinklab.application.dto.request.EscalateWorkOrderRequest;
import com.thinklab.application.dto.request.InitiateCommentRequest;
import com.thinklab.application.dto.request.InitiatePartRequest;
import com.thinklab.application.dto.request.InitiateWorkOrderRequest;
import com.thinklab.application.dto.request.PassQualityCheckRequest;
import com.thinklab.application.dto.request.TriageWorkOrderRequest;
import com.thinklab.application.dto.request.UpdateWorkOrderRequest;
import com.thinklab.application.dto.response.WorkOrderAuditEntryResponse;
import com.thinklab.application.dto.response.WorkOrderResponse;
import com.thinklab.application.usecase.CompleteRepairUseCase;
import com.thinklab.application.usecase.ControlWorkOrderUseCase;
import com.thinklab.application.usecase.EscalateWorkOrderUseCase;
import com.thinklab.application.usecase.InitiateCommentUseCase;
import com.thinklab.application.usecase.InitiatePartUseCase;
import com.thinklab.application.usecase.InitiateWorkOrderUseCase;
import com.thinklab.application.usecase.PassQualityCheckUseCase;
import com.thinklab.application.usecase.RetrieveWorkOrderAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveWorkOrderUseCase;
import com.thinklab.application.usecase.RetrieveWorkOrdersUseCase;
import com.thinklab.application.usecase.StartRepairUseCase;
import com.thinklab.application.usecase.TriageWorkOrderUseCase;
import com.thinklab.application.usecase.UpdateWorkOrderUseCase;
import com.thinklab.application.usecase.VendorReturnedUseCase;
import com.thinklab.domain.model.WorkOrder.WorkOrderStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-hardware-maintenance} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.WorkOrder} is the
 * Control Record. Every route follows {@code /it-hardware-maintenance/v1/{control-record-id}/{behavior-qualifier}}.
 * There is no {@code DELETE}: {@code control/cancel} and {@code control/close} are terminal, soft
 * status transitions.
 *
 * <p><b>Self-service scoping (ADR-032):</b> {@code X-Role}, set by the kit's {@code SecurityFilter}
 * from the verified token when security is on (client-supplied and trusted only when security is
 * off, same posture as every other header here), narrows a {@code REQUESTER} caller to their own
 * tickets and hides internal comments - every use case that needs this receives {@code role} as a
 * plain, optional string, never a hard platform-wide dependency on RBAC being enabled.
 */
@Controller("/it-hardware-maintenance/v1")
public class WorkOrderController {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";
    static final String ROLE_HEADER = "X-Role";

    private final InitiateWorkOrderUseCase initiateWorkOrderUseCase;
    private final RetrieveWorkOrderUseCase retrieveWorkOrderUseCase;
    private final RetrieveWorkOrdersUseCase retrieveWorkOrdersUseCase;
    private final UpdateWorkOrderUseCase updateWorkOrderUseCase;
    private final TriageWorkOrderUseCase triageWorkOrderUseCase;
    private final ControlWorkOrderUseCase controlWorkOrderUseCase;
    private final StartRepairUseCase startRepairUseCase;
    private final EscalateWorkOrderUseCase escalateWorkOrderUseCase;
    private final VendorReturnedUseCase vendorReturnedUseCase;
    private final CompleteRepairUseCase completeRepairUseCase;
    private final PassQualityCheckUseCase passQualityCheckUseCase;
    private final InitiatePartUseCase initiatePartUseCase;
    private final InitiateCommentUseCase initiateCommentUseCase;
    private final RetrieveWorkOrderAuditLogUseCase retrieveWorkOrderAuditLogUseCase;

    public WorkOrderController(
            InitiateWorkOrderUseCase initiateWorkOrderUseCase,
            RetrieveWorkOrderUseCase retrieveWorkOrderUseCase,
            RetrieveWorkOrdersUseCase retrieveWorkOrdersUseCase,
            UpdateWorkOrderUseCase updateWorkOrderUseCase,
            TriageWorkOrderUseCase triageWorkOrderUseCase,
            ControlWorkOrderUseCase controlWorkOrderUseCase,
            StartRepairUseCase startRepairUseCase,
            EscalateWorkOrderUseCase escalateWorkOrderUseCase,
            VendorReturnedUseCase vendorReturnedUseCase,
            CompleteRepairUseCase completeRepairUseCase,
            PassQualityCheckUseCase passQualityCheckUseCase,
            InitiatePartUseCase initiatePartUseCase,
            InitiateCommentUseCase initiateCommentUseCase,
            RetrieveWorkOrderAuditLogUseCase retrieveWorkOrderAuditLogUseCase
    ) {
        this.initiateWorkOrderUseCase = initiateWorkOrderUseCase;
        this.retrieveWorkOrderUseCase = retrieveWorkOrderUseCase;
        this.retrieveWorkOrdersUseCase = retrieveWorkOrdersUseCase;
        this.updateWorkOrderUseCase = updateWorkOrderUseCase;
        this.triageWorkOrderUseCase = triageWorkOrderUseCase;
        this.controlWorkOrderUseCase = controlWorkOrderUseCase;
        this.startRepairUseCase = startRepairUseCase;
        this.escalateWorkOrderUseCase = escalateWorkOrderUseCase;
        this.vendorReturnedUseCase = vendorReturnedUseCase;
        this.completeRepairUseCase = completeRepairUseCase;
        this.passQualityCheckUseCase = passQualityCheckUseCase;
        this.initiatePartUseCase = initiatePartUseCase;
        this.initiateCommentUseCase = initiateCommentUseCase;
        this.retrieveWorkOrderAuditLogUseCase = retrieveWorkOrderAuditLogUseCase;
    }

    /** Behavior Qualifier: {@code initiate}. Files a new WorkOrder, by staff or self-service. */
    @Post("/initiate")
    public Mono<HttpResponse<WorkOrderResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role,
            @Body @Valid InitiateWorkOrderRequest request
    ) {
        log.info("[ACTION: INITIATE_WORKORDER] [EXECUTOR: {}] Received request for organisation: {} asset: {}", executor, tenantId, request.assetId());

        return initiateWorkOrderUseCase.execute(UUID.fromString(tenantId), request, executor, role).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. Fetches a single WorkOrder by UUID. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<WorkOrderResponse>> retrieveById(
            @PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role
    ) {
        log.info("[ACTION: RETRIEVE_WORKORDER] Received request to get WorkOrder by ID: {}", id);

        return retrieveWorkOrderUseCase.execute(id, executor, role).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection). Lists WorkOrders scoped to a tenant. */
    @Get("/retrieve")
    public Mono<List<WorkOrderResponse>> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role,
            @QueryValue @Nullable WorkOrderStatus status
    ) {
        log.info("[ACTION: RETRIEVE_WORKORDERS] Received request to list WorkOrders for organisation: {} status: {}", tenantId, status);

        return Mono.defer(() -> retrieveWorkOrdersUseCase.execute(UUID.fromString(tenantId), status, executor, role).collectList());
    }

    /** Behavior Qualifier: {@code update}. Updates basic WorkOrder info. */
    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Body @Valid UpdateWorkOrderRequest request) {
        return updateWorkOrderUseCase.execute(id, request, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code triage}. REQUESTED -&gt; TRIAGED. */
    @Put("/{id}/triage")
    public Mono<HttpResponse<Void>> triage(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Body @Valid TriageWorkOrderRequest request) {
        return triageWorkOrderUseCase.execute(id, request, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code schedule}. TRIAGED -&gt; SCHEDULED. */
    @Put("/{id}/schedule")
    public Mono<HttpResponse<Void>> schedule(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlWorkOrderUseCase.Action.SCHEDULE, executor);
    }

    /** Behavior Qualifier: {@code control/start}. SCHEDULED -&gt; IN_REPAIR; publishes {@code repair-started}. */
    @Put("/{id}/control/start")
    public Mono<HttpResponse<Void>> controlStart(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        log.info("[ACTION: CONTROL_WORKORDER] [EXECUTOR: {}] start for ID: {}", executor, id);
        return startRepairUseCase.execute(id, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/await-parts}. IN_REPAIR -&gt; AWAITING_PARTS. */
    @Put("/{id}/control/await-parts")
    public Mono<HttpResponse<Void>> controlAwaitParts(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlWorkOrderUseCase.Action.AWAIT_PARTS, executor);
    }

    /** Behavior Qualifier: {@code control/resume}. AWAITING_PARTS -&gt; IN_REPAIR. */
    @Put("/{id}/control/resume")
    public Mono<HttpResponse<Void>> controlResume(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlWorkOrderUseCase.Action.RESUME, executor);
    }

    /** Behavior Qualifier: {@code control/escalate}. TRIAGED -&gt; ESCALATED_TO_VENDOR. */
    @Put("/{id}/control/escalate")
    public Mono<HttpResponse<Void>> controlEscalate(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Body @Valid EscalateWorkOrderRequest request) {
        return escalateWorkOrderUseCase.execute(id, request, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/vendor-returned}. ESCALATED_TO_VENDOR -&gt; IN_REPAIR. */
    @Put("/{id}/control/vendor-returned")
    public Mono<HttpResponse<Void>> controlVendorReturned(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        log.info("[ACTION: CONTROL_WORKORDER] [EXECUTOR: {}] vendor-returned for ID: {}", executor, id);
        return vendorReturnedUseCase.execute(id, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/complete-repair}. IN_REPAIR -&gt; QUALITY_CHECK. */
    @Put("/{id}/control/complete-repair")
    public Mono<HttpResponse<Void>> controlCompleteRepair(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Body @Valid CompleteRepairRequest request) {
        return completeRepairUseCase.execute(id, request, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/pass}. QUALITY_CHECK -&gt; RESOLVED; publishes {@code repair-completed}. */
    @Put("/{id}/control/pass")
    public Mono<HttpResponse<Void>> controlPass(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Body @Valid PassQualityCheckRequest request) {
        return passQualityCheckUseCase.execute(id, request, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/fail}. QUALITY_CHECK -&gt; IN_REPAIR. */
    @Put("/{id}/control/fail")
    public Mono<HttpResponse<Void>> controlFail(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlWorkOrderUseCase.Action.FAIL, executor);
    }

    /** Behavior Qualifier: {@code control/close}. RESOLVED -&gt; CLOSED (terminal). */
    @Put("/{id}/control/close")
    public Mono<HttpResponse<Void>> controlClose(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlWorkOrderUseCase.Action.CLOSE, executor);
    }

    /** Behavior Qualifier: {@code control/reopen}. RESOLVED -&gt; TRIAGED. */
    @Put("/{id}/control/reopen")
    public Mono<HttpResponse<Void>> controlReopen(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlWorkOrderUseCase.Action.REOPEN, executor);
    }

    /** Behavior Qualifier: {@code control/cancel}. Terminal, replaces DELETE. */
    @Put("/{id}/control/cancel")
    public Mono<HttpResponse<Void>> controlCancel(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlWorkOrderUseCase.Action.CANCEL, executor);
    }

    /** Behavior Qualifier: {@code part/initiate}. */
    @Post("/{id}/part/initiate")
    public Mono<HttpResponse<Void>> initiatePart(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Body @Valid InitiatePartRequest request) {
        return initiatePartUseCase.execute(id, request, executor).thenReturn(HttpResponse.status(HttpStatus.CREATED));
    }

    /** Behavior Qualifier: {@code comment/initiate}. */
    @Post("/{id}/comment/initiate")
    public Mono<HttpResponse<Void>> initiateComment(
            @PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role,
            @Body @Valid InitiateCommentRequest request
    ) {
        return initiateCommentUseCase.execute(id, request, executor, role).thenReturn(HttpResponse.status(HttpStatus.CREATED));
    }

    /** Behavior Qualifier: {@code audit-log/retrieve}. Immutable forensic ledger of the WorkOrder. */
    @Get("/{id}/audit-log/retrieve")
    public Mono<List<WorkOrderAuditEntryResponse>> retrieveAuditLog(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return retrieveWorkOrderAuditLogUseCase.execute(id, executor, role);
    }

    private Mono<HttpResponse<Void>> control(UUID id, ControlWorkOrderUseCase.Action action, String executor) {
        log.info("[ACTION: CONTROL_WORKORDER] [EXECUTOR: {}] {} for ID: {}", executor, action, id);

        return controlWorkOrderUseCase.execute(id, action, executor).thenReturn(HttpResponse.noContent());
    }
}
