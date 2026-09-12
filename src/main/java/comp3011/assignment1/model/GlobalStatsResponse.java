package comp3011.assignment1.model;

/**
 * Response body for GET /api/v1/global/stats.
 *
 */
public record GlobalStatsResponse(long inputTokens, long outputTokens) {
}