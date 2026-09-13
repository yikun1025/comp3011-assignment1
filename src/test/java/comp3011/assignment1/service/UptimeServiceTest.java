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
    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }

        void setNow(Instant now) { this.now = now; }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}