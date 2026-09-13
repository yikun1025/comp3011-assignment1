package comp3011.assignment1.service;

import comp3011.assignment1.model.TranscriptionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fake speech-to-text service for offline runs and tests.
 * Never calls OpenAI and needs no API key; always returns a fixed transcript.
 * The artificial delay imitates the network wait of a real Cloud call,
 * so concurrency tests exercise genuinely blocking requests.
 */
@Service
@Profile("stub")
public class StubSpeechToTextService implements SpeechToTextService {

    public static final String STUB_TEXT = "This is a stub transcription.";
    public static final long STUB_INPUT_TOKENS = 10;
    public static final long STUB_OUTPUT_TOKENS = 5;

    private final Duration delay;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peakInFlight = new AtomicInteger();
    private final AtomicReference<RuntimeException> nextFailure = new AtomicReference<>();

    public StubSpeechToTextService(@Value("${app.stub.delay:200ms}") Duration delay) {
        this.delay = delay;
    }

    @Override
    public TranscriptionResult transcribe(byte[] audio, String contentType) {
        RuntimeException failure = nextFailure.getAndSet(null);
        if (failure != null) {
            throw failure;
        }

        int current = inFlight.incrementAndGet();
        peakInFlight.accumulateAndGet(current, Math::max);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stub transcription was interrupted", e);
        } finally {
            inFlight.decrementAndGet();
        }
        return new TranscriptionResult(STUB_TEXT, STUB_INPUT_TOKENS, STUB_OUTPUT_TOKENS);
    }

    /** Largest number of calls simultaneously waiting in the stub since the last reset. */
    public int peakConcurrentCalls() {
        return peakInFlight.get();
    }

    /**
     * Makes the next call throw instead of returning a transcript. Test-only:
     * it is how the controller's error contract is exercised without a
     * network, since the real provider cannot be told to fail on demand.
     */
    public void failNextCallWith(RuntimeException failure) {
        nextFailure.set(failure);
    }

    /** Test-only metric reset; callers invoke it only when no transcription is in flight. */
    public void resetMetrics() {
        inFlight.set(0);
        peakInFlight.set(0);
        nextFailure.set(null);
    }
}
