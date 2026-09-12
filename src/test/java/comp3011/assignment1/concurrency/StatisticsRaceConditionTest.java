package comp3011.assignment1.concurrency;

import comp3011.assignment1.exception.ShutdownInProgressException;
import comp3011.assignment1.controller.TranscriptionController;
import comp3011.assignment1.model.TranscriptionResponse;
import comp3011.assignment1.service.ShutdownExecutor;
import comp3011.assignment1.service.ShutdownService;
import comp3011.assignment1.service.StubSpeechToTextService;
import comp3011.assignment1.service.TokenUsageStatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the two pieces of mutable state shared by requests.
 * A plain {@code long += value} can lose updates when request threads overlap;
 * a plain {@code if (!flag) flag = true} can accept shutdown twice. Both tests
 * release many contenders from one gate to make those otherwise rare races
 * reproducible.
 */
@SpringBootTest
@ActiveProfiles("stub")
@TestPropertySource(properties = {
        "app.stub.delay=0ms",
        "logging.level.comp3011.assignment1=WARN"
})
class StatisticsRaceConditionTest {

    private static final int TRANSCRIPTIONS = 400;
    @Autowired
    private TokenUsageStatisticsService statistics;

    @Autowired
    private TranscriptionController transcriptionController;

    @Test
    void concurrentTranscriptionsDoNotLoseTokenUpdates() throws Exception {
        long inputTokensBefore = statistics.inputTokens();
        long outputTokensBefore = statistics.outputTokens();
        CountDownLatch ready = new CountDownLatch(TRANSCRIPTIONS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<TranscriptionResponse>> responses = new ArrayList<>(TRANSCRIPTIONS);

        try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TRANSCRIPTIONS; i++) {
                responses.add(callers.submit(() -> {
                    ready.countDown();
                    startGate.await();
                    return transcriptionController.transcribe(audioPart());
                }));
            }

            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            startGate.countDown();

            List<TranscriptionResponse> transcripts = new ArrayList<>(TRANSCRIPTIONS);
            for (Future<TranscriptionResponse> response : responses) {
                transcripts.add(response.get(45, TimeUnit.SECONDS));
            }
            assertThat(transcripts).allSatisfy(response ->
                    assertThat(response.text()).isEqualTo(StubSpeechToTextService.STUB_TEXT));
        }

        // Stub usage is deterministic: 10 input and 5 output tokens for every
        // successful call. Exact equality catches even a single lost update.
        assertThat(statistics.inputTokens()).isEqualTo(inputTokensBefore
                + TRANSCRIPTIONS * StubSpeechToTextService.STUB_INPUT_TOKENS);
        assertThat(statistics.outputTokens()).isEqualTo(outputTokensBefore
                + TRANSCRIPTIONS * StubSpeechToTextService.STUB_OUTPUT_TOKENS);
    }

    @Test
    void simultaneousShutdownRequestsChooseExactlyOneWinner() throws Exception {
        int contenders = 64;
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger executorCalls = new AtomicInteger();
        ShutdownService shutdownService = new ShutdownService(new ShutdownExecutor() {
            @Override
            public void shutdown() {
                executorCalls.incrementAndGet();
            }
        });

        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> calls = new ArrayList<>(contenders);

        try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < contenders; i++) {
                calls.add(callers.submit(() -> {
                    ready.countDown();
                    startGate.await();
                    try {
                        shutdownService.requestShutdown();
                        accepted.incrementAndGet();
                    } catch (ShutdownInProgressException expected) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }

            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            startGate.countDown();
            for (Future<?> call : calls) {
                call.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(accepted.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(contenders - 1);
        assertThat(executorCalls.get()).isEqualTo(1);
    }

    /**
     * The HTTP-level concurrency test already exercises Tomcat with 250 real
     * requests. Here we call the singleton controller directly so 400 virtual
     * threads race on its shared statistics service without transient socket
     * exhaustion obscuring a lost-update defect.
     */
    private static MockMultipartFile audioPart() {
        byte[] audio = new byte[1_000];
        for (int i = 0; i < audio.length; i++) {
            audio[i] = (byte) (i % 251);
        }
        return new MockMultipartFile("audio", "recording.webm", "audio/webm", audio);
    }
}
