package comp3011.assignment1.web;

import comp3011.assignment1.exception.ShutdownInProgressException;
import comp3011.assignment1.exception.SpeechToTextException;
import comp3011.assignment1.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
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
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Audio must be uploaded as multipart/form-data.", request);
    }

    @ExceptionHandler(SpeechToTextException.class)
    public ResponseEntity<ErrorResponse> handleSpeechToText(
            SpeechToTextException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    /**
     * Exceptions that already carry their own HTTP status,
     * e.g. the 400 thrown by TranscriptionController for an empty upload.
     * Without this handler they would fall through to the 500 catch-all.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = (ex.getReason() != null) ? ex.getReason() : status.getReasonPhrase();
        return build(status, message, request);
    }
    /**
     *
     * 400: the upload has no multipart part named "audio",
     * e.g. the front end appended the file under a different name.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            MissingServletRequestPartException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Required multipart part '" + ex.getRequestPartName() + "' is missing.", request);
    }

    /**
     * 413: the upload is larger than spring.servlet.multipart.max-file-size.
     * Spring rejects it while parsing the request, before the controller runs.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.CONTENT_TOO_LARGE,
                "Audio file exceeds the maximum upload size.", request);
    }

    /**
     * A repeat shutdown request is a conflict with the server's current state,
     * not a malformed request - the client did nothing wrong, it is simply too
     * late. Status and wording are both fixed by the API spec.
     */
    @ExceptionHandler(ShutdownInProgressException.class)
    public ResponseEntity<ErrorResponse> handleShutdownInProgress(
            ShutdownInProgressException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
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