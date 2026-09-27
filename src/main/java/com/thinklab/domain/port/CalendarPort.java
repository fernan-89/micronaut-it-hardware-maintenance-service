package com.thinklab.domain.port;

import java.time.Duration;
import java.time.Instant;

/**
 * Outbound Port computing when an SLA duration elapses (ADR-033). Kept separate from the repository
 * port because it is a pure calculation, not persistence: this is the seam a future
 * {@code calendar-sla} Service Domain (business hours, per-tenant time zones, holidays) plugs into
 * without WorkOrder's own code or storage changing at all.
 */
public interface CalendarPort {

    /**
     * @return the instant {@code duration} of business time after {@code from} elapses.
     */
    Instant addBusinessDuration(Instant from, Duration duration);
}
