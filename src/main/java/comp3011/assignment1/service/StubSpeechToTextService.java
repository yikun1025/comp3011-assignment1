package comp3011.assignment1.service;

import comp3011.assignment1.model.TranscriptionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;

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

    public StubSpeechToTextService(@Value("${app.stub.delay:200ms}") Duration delay) {
        this.delay = delay;
    }

    @Override
    public TranscriptionResult transcribe(byte[] audio, String contentType) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stub transcription was interrupted", e);
        }
        return new TranscriptionResult(STUB_TEXT, STUB_INPUT_TOKENS, STUB_OUTPUT_TOKENS);
    }
}