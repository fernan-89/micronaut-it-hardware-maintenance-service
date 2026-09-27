package com.thinklab.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox event payload published on {@code control/start} (subject {@code ...repair-started}) and
 * {@code control/pass} (subject {@code ...repair-completed}) - the two moments an Asset's own
 * lifecycle genuinely changes because of this WorkOrder (kit ADR-003). The asset-registry's own
 * {@code WorkOrderEventHandler} reacts by calling its existing {@code ControlAssetUseCase} with
 * {@code Action.MAINTENANCE} / {@code Action.DEPLOY} - this service never calls asset-registry
 * synchronously.
 */
public record WorkOrderRepairEvent(UUID workOrderId, UUID organisationId, UUID assetId, Instant occurredAt) {

    public static final String REPAIR_STARTED_SUBJECT = "thinklab.it-hardware-maintenance.workorder.repair-started";
    public static final String REPAIR_COMPLETED_SUBJECT = "thinklab.it-hardware-maintenance.workorder.repair-completed";
}
