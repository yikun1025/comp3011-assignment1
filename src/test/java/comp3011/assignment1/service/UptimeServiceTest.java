package comp3011.assignment1.service;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.ZoneId;
import java.time.Instant;
import java.time.ZoneOffset;
import comp3011.assignment1.model.UptimeResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UptimeServiceTest {

    @Test
    void reportsElapsedSecondsBetweenStartAndNow() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(start);

        UptimeService service = new UptimeService(clock);

        clock.setNow(Instant.parse("2026-01-01T00:00:12.500Z"));

        UptimeResponse response = service.getUptime();

        assertEquals(12.5, response.serverUptimeSeconds());
    }

    /**
     * The start instant here has a fraction of a second. If the service ever
     * truncated it (say to whole seconds for a tidier timestamp) the discarded
     * fraction would inflate every reading, and the test above would not
     * notice because its start is exactly on the second.
     */
    @Test
    void doesNotRoundTheStartInstant() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00.900Z"));
        UptimeService service = new UptimeService(clock);

        clock.setNow(Instant.parse("2026-01-01T00:00:02.134Z"));

        assertEquals(1.234, service.getUptime().serverUptimeSeconds());
    }

    /** A client recomputing utcNow - utcServerStart must get the reported number. */
    @Test
    void timestampsAgreeWithTheReportedSeconds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00.900Z"));
        UptimeService service = new UptimeService(clock);

        clock.setNow(Instant.parse("2026-01-01T00:00:02.134Z"));
        UptimeResponse response = service.getUptime();

        Instant start = Instant.parse(response.utcServerStart());
        Instant now = Instant.parse(response.utcNow());
        double fromTimestamps = java.time.Duration.between(start, now).toNanos() / 1_000_000_000.0;

        assertEquals(fromTimestamps, response.serverUptimeSeconds());
    }

    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }

        void setNow(Instant now) { this.now = now; }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}