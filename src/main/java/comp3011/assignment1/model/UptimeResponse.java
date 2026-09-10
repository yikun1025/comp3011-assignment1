package comp3011.assignment1.model;

public record UptimeResponse(
        String utcServerStart,
        String utcNow,
        double serverUptimeSeconds
) { }