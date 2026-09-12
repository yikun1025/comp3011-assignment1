package comp3011.assignment1.exception;

/**
 * Thrown when a shutdown is requested while one is already under way.
 *
 * Carries no status or message of its own: the spec fixes both the 409 and the
 * wording, so they are written once in GlobalExceptionHandler where the
 * ErrorResponse is actually built. Duplicating them here would give two places
 * to change and two places to get wrong.
 *
 * Extends RuntimeException rather than Exception. The service that throws it
 * and the controller that triggers it are separated by Spring's dispatch, so a
 * checked exception would only force "throws" declarations through code that
 * cannot do anything useful with it.
 */
public class ShutdownInProgressException extends RuntimeException {

    public ShutdownInProgressException() {
        super("Graceful shutdown is already in progress.");
    }
}