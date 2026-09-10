package comp3011.assignment1.service;

import comp3011.assignment1.model.UptimeResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class UptimeService {

    private final Clock clock;
    private final Instant startTime;

    public UptimeService(Clock clock) {
        this.clock = clock;
        this.startTime = clock.instant();
    }

    public UptimeResponse getUptime() {
        Instant now = clock.instant();
        Duration uptime = Duration.between(startTime, now);
        double seconds = uptime.toNanos() / 1_000_000_000.0;

        return new UptimeResponse(
                startTime.toString(),
                now.toString(),
                seconds
        );
    }
}