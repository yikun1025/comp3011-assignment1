package comp3011.assignment1.exception;

import org.springframework.http.HttpStatus;

/**
 * A speech-to-text failure, carrying the status the client should see.
 *
 * There is intentionally no constructor that takes a cause. The exceptions
 * this one replaces come from an HTTP client or a JSON parser, and their
 * messages may quote the request that failed, Authorization header included.
 * A chained cause would carry that text into framework logging that this
 * application does not control; leaving the constructor out makes the
 * omission a property of the type rather than a habit at each throw site.
 */
public class SpeechToTextException extends RuntimeException {

    private final HttpStatus status;

    public SpeechToTextException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}