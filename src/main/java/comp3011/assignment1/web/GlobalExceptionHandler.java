package comp3011.assignment1.web;

import comp3011.assignment1.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.Set;

import java.time.Clock;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    /**
     * 404：path not exist
     * Spring can't find the static resours then throw NoResourceFoundException。
     */

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint " + request.getMethod()
                + " " + request.getRequestURI() + ".", request);
    }
    /**
     * 405：path exist but not correct method 。
     * Spring  HttpRequestMethodNotSupportedException，
     */

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null) {
            headers.setAllow(supported);
        }
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "Request method '" + request.getMethod() + "' is not supported.",
                request, headers);
    }

    /**
     *  500：if no matches the error then direct to 500
     */

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred.", request);
    }

    // --- error response general recall ---

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                HttpServletRequest request) {
        return build(status, message, request, new HttpHeaders());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                HttpServletRequest request, HttpHeaders headers) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(clock).toString(), // timestamp
                status.value(), // status → 405
                status.getReasonPhrase(),  // error → "Method Not Allowed"
                message,  // message
                request.getRequestURI() // path
        );
        return new ResponseEntity<>(body, headers, status);
    }
}