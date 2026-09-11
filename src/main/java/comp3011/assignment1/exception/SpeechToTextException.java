package comp3011.assignment1.exception;

import org.springframework.http.HttpStatus;

public class SpeechToTextException extends RuntimeException {

    private final HttpStatus status;

    public SpeechToTextException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public SpeechToTextException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}