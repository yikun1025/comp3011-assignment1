package comp3011.assignment1.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import comp3011.assignment1.model.GlobalStatsResponse;
import comp3011.assignment1.service.TokenUsageStatisticsService;

/**
 * Read-only view over the global token counters.
 *
 * Kept separate from AdminController because the spec groups these endpoints
 * under different tags and different path prefixes: /api/v1/global here,
 * /api/v1/admin there. One controller per prefix keeps the routing table
 * readable and matches the shape the spec already describes.
 *
 * Like every controller in this application, this class holds no mutable
 * state. Spring creates a single instance and every concurrent request runs
 * through it, so a field that changed per request would be a race waiting to
 * happen. The only state lives in TokenUsageStatisticsService, which is built
 * for concurrent access.
 */
@RestController
@RequestMapping("/api/v1/global")
public class GlobalStatsController {

    private final TokenUsageStatisticsService statistics;

    // Constructor injection: the dependency is final and the object cannot be
    // constructed in a half-wired state.
    public GlobalStatsController(TokenUsageStatisticsService statistics) {
        this.statistics = statistics;
    }

    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public GlobalStatsResponse getGlobalStats() {
        // Two independent reads, so this is not an instantaneous snapshot.
        // Both counters only grow, so the response can lag but cannot be wrong.
        return new GlobalStatsResponse(statistics.inputTokens(), statistics.outputTokens());
    }
}