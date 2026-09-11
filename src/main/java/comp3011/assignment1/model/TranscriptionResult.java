package comp3011.assignment1.model;

/**
 * Internal result returned by a speech-to-text service.
 * Carries token usage for statistics; not exposed directly to API clients.
 */
public record TranscriptionResult(String text, long inputTokens, long outputTokens) {

    public TranscriptionResult {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
    }
}