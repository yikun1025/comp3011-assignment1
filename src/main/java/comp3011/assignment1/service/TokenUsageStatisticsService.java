package comp3011.assignment1.service;

import java.util.concurrent.atomic.LongAdder;

import org.springframework.stereotype.Service;

/**
 * Application-wide running total of the tokens reported by the STT provider.
 */
@Service
public class TokenUsageStatisticsService {

    private final LongAdder inputTokens = new LongAdder();
    private final LongAdder outputTokens = new LongAdder();

    /**
     * Adds one transcription's usage to the running totals.
     * Called from the request thread, once per successful transcription.
     */
    public void record(long input, long output) {
        // The YAML spec declares both totals as minimum 0. Negative values
        // should never arrive, but ignoring them makes that a property of this
        // class rather than a hope about the upstream response.
        if (input > 0) {
            inputTokens.add(input);
        }
        if (output > 0) {
            outputTokens.add(output);
        }
    }

    public long inputTokens() {
        return inputTokens.sum();
    }

    public long outputTokens() {
        return outputTokens.sum();
    }
}