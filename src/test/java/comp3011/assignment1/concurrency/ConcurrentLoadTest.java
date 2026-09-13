package comp3011.assignment1.concurrency;

import comp3011.assignment1.service.StubSpeechToTextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
 * Drives the running server over real HTTP, rather than MockMvc, so the test
 * includes Tomcat's request dispatch and connection handling. Every request
 * blocks in the stub for 300 ms, modelling a Cloud API wait rather than CPU
 * work. The latch releases all callers together: submitting tasks in a loop
 * alone would not guarantee a meaningful concurrency test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("stub")
@TestPropertySource(properties = {
        "app.stub.delay=300ms",
        "logging.level.comp3011.assignment1=WARN"
})
class ConcurrentLoadTest {

    private static final int REQUESTS = 250;

    /** Refused connects are retried this many times; see sendWithConnectRetry. */
    private static final int MAX_CONNECT_ATTEMPTS = 5;

    private final AtomicInteger connectRetries = new AtomicInteger();
    private static final String BOUNDARY = "----Assignment1ConcurrentTest";

    @LocalServerPort
    private int port;

    @Autowired
    private StubSpeechToTextService stub;

    @BeforeEach
    void resetStubMetrics() {
        stub.resetMetrics();
    }

    @Test
    void handlesMoreThanTwoHundredSimultaneousBlockingRequests() throws Exception {
        URI endpoint = URI.create("http://localhost:" + port + "/api/v1/transcribe");
        byte[] body = multipartAudioBody();
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Integer>> responses = new ArrayList<>(REQUESTS);

        try (ExecutorService httpExecutor = Executors.newVirtualThreadPerTaskExecutor();
             ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {

            HttpClient client = HttpClient.newBuilder()
                    .executor(httpExecutor)
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            for (int i = 0; i < REQUESTS; i++) {
                responses.add(callers.submit(() -> {
                    HttpRequest request = HttpRequest.newBuilder(endpoint)
                            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                            .timeout(Duration.ofSeconds(30))
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                            .build();

                    ready.countDown();
                    startGate.await();
                    return sendWithConnectRetry(client, request);
                }));
            }

            assertThat(ready.await(20, TimeUnit.SECONDS))
                    .as("all callers should be ready before the load begins")
                    .isTrue();

            long startedAt = System.nanoTime();
            startGate.countDown();

            List<Integer> statuses = new ArrayList<>(REQUESTS);
            for (Future<Integer> response : responses) {
                statuses.add(response.get(45, TimeUnit.SECONDS));
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            assertThat(statuses).containsOnly(200);
            assertThat(stub.peakConcurrentCalls())
                    .as("the service must have more than 200 calls in flight at once")
                    .isGreaterThan(200);
            assertThat(elapsed)
                    .as("250 serial 300 ms calls would take about 75 seconds")
                    .isLessThan(Duration.ofSeconds(15));

            System.out.printf("Load test: %d requests in %d ms, peak in flight %d, refused connects retried %d%n",
                    REQUESTS, elapsed.toMillis(), stub.peakConcurrentCalls(), connectRetries.get());
        }
    }

    /*
     * Windows caps the TCP accept backlog at 200 and refuses a connection
     * above it; Linux queues it and the client's SYN retransmit gets through
     * a moment later. Retrying a refused connect reproduces the Linux
     * behaviour, so the test measures the server rather than the operating
     * system it happens to run on. Only a refusal is retried: a timeout or an
     * HTTP error is a real result and must surface as one. The three
     * assertions above are untouched - a server that serialised its work
     * would still fail the peak-concurrency check.
     */
    private int sendWithConnectRetry(HttpClient client, HttpRequest request) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            } catch (ConnectException refused) {
                if (attempt == MAX_CONNECT_ATTEMPTS) {
                    throw refused;
                }
                connectRetries.incrementAndGet();
                Thread.sleep(25L * attempt);
            }
        }
    }

    private static byte[] multipartAudioBody() {
        byte[] audio = new byte[1_000];
        for (int i = 0; i < audio.length; i++) {
            audio[i] = (byte) (i % 251);
        }

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"audio\"; filename=\"recording.webm\"\r\n"
                + "Content-Type: audio/webm\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(audio);
        body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }
}
