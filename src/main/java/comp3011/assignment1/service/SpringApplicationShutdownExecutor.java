package comp3011.assignment1.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Closes the Spring application context, which triggers Spring Boot's graceful
 * shutdown: the connector stops accepting new connections, in-flight requests
 * are given the configured grace period to finish, and only then does the JVM
 * exit.
 *
 * The close runs on a separate thread. Calling it inline would tear the web
 * server down while this very request is still being written, so the client
 * would see a dropped connection instead of the 202 the spec requires. The
 * short delay gives the response time to reach the client before the connector
 * stops.
 *
 * Not annotated @Profile: unlike the STT service, there is no "stub" variant in
 * production. Tests replace this bean directly instead.
 */
@Service
public class SpringApplicationShutdownExecutor extends ShutdownExecutor {

    private static final Logger log = LoggerFactory.getLogger(SpringApplicationShutdownExecutor.class);

    /** Long enough for the 202 to be flushed, short enough not to stall an operator. */
    private static final long RESPONSE_FLUSH_DELAY_MS = 500L;

    private final ApplicationContext context;

    public SpringApplicationShutdownExecutor(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void shutdown() {
        log.info("Graceful shutdown accepted; closing application context in {}ms", RESPONSE_FLUSH_DELAY_MS);

        Thread closer = new Thread(() -> {
            try {
                Thread.sleep(RESPONSE_FLUSH_DELAY_MS);
            } catch (InterruptedException e) {
                // Restore the flag rather than swallowing it: something asked
                // this thread to stop, and shutting down anyway is still the
                // right outcome.
                Thread.currentThread().interrupt();
            }
            SpringApplication.exit(context, () -> 0);
        }, "graceful-shutdown");

        closer.setDaemon(false);
        closer.start();
    }
}