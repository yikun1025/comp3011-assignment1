package comp3011.assignment1.service;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import comp3011.assignment1.model.UptimeResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UptimeServiceTest {

    private java.lang.Object system;

    @Test
    void reportsElapsedSecondsBetweenStartAndNow() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant later = Instant.parse("2026-01-01T00:00:12.500Z");

        UptimeService service = new UptimeService(Clock.fixed(start, ZoneOffset.UTC));

        system.pr
    }
}