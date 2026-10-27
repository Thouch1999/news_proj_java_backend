package com.api.v1.newssystem.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** A clock that only moves when a test says so (windows, locks and account age are tested without waiting). */
public class MutableClock extends Clock {
    private Instant now;
    private final ZoneId zone;

    public MutableClock() {
        this(Instant.parse("2026-06-01T08:00:00Z"), ZoneId.of("UTC"));
    }

    private MutableClock(Instant now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
