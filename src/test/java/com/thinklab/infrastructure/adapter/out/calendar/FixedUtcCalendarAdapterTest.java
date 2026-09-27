package com.thinklab.infrastructure.adapter.out.calendar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixedUtcCalendarAdapterTest {

    private final FixedUtcCalendarAdapter adapter = new FixedUtcCalendarAdapter();

    @Test
    @DisplayName("v1 treats every instant as business time: a plain addition")
    void addsDurationDirectly() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Duration duration = Duration.ofHours(4);

        assertEquals(from.plus(duration), adapter.addBusinessDuration(from, duration));
    }

    @Test
    @DisplayName("null from or duration is rejected")
    void guards() {
        assertThrows(NullPointerException.class, () -> adapter.addBusinessDuration(null, Duration.ofHours(1)));
        assertThrows(NullPointerException.class, () -> adapter.addBusinessDuration(Instant.now(), null));
    }
}
