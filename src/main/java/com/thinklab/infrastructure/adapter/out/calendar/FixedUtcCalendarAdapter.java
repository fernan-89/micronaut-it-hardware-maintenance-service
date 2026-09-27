package com.thinklab.infrastructure.adapter.out.calendar;

import com.thinklab.domain.port.CalendarPort;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * v1 {@link CalendarPort}: every instant is treated as business time (no weekends, no holidays, no
 * per-tenant time zone), so "4 business hours" is simply {@code from.plus(Duration.ofHours(4))}
 * (ADR-033). Deliberately the only implementation of the port until a real {@code calendar-sla}
 * Service Domain exists - swapping it in later is a bean substitution, not a data migration, because
 * every SLA due date already lives on {@code WorkOrder} as a plain {@link Instant}.
 */
@Singleton
public class FixedUtcCalendarAdapter implements CalendarPort {

    @Override
    public Instant addBusinessDuration(Instant from, Duration duration) {
        Objects.requireNonNull(from, "Infrastructure constraint violated: from cannot be null.");
        Objects.requireNonNull(duration, "Infrastructure constraint violated: duration cannot be null.");
        return from.plus(duration);
    }
}
