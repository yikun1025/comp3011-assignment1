package comp3011.assignment1.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import comp3011.assignment1.exception.ShutdownInProgressException;

/**
 * Guarantees that a graceful shutdown is started exactly once.
 *
 * The flag is an AtomicBoolean, not a boolean. A separate read and write,
 * such as {@code if (!shuttingDown) shuttingDown = true}, would allow two
 * simultaneous callers to pass the check. compareAndSet makes the decision
 * and state change one atomic operation.
 */
@Service
public class ShutdownService {

    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    private final ShutdownExecutor executor;

    public ShutdownService(ShutdownExecutor executor) {
        this.executor = executor;
    }

    /**
     * Starts a graceful shutdown, or rejects the call if one is already running.
     *
     * @throws ShutdownInProgressException if another caller already won the race
     */
    public void requestShutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) {
            throw new ShutdownInProgressException();
        }
        executor.shutdown();
    }
}
