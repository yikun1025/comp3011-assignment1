package comp3011.assignment1.web;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import comp3011.assignment1.exception.ShutdownInProgressException;
import comp3011.assignment1.exception.SpeechToTextException;
import comp3011.assignment1.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Produces one documented JSON error shape for application and framework failures. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    /** A route or static resource was not found. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint " + request.getMethod()
                + " " + request.getRequestURI() + ".", request);
    }
    /** The path exists but does not support the request method. */
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
    /** The multipart request did not contain the required {@code audio} part. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            MissingServletRequestPartException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Required multipart part '" + ex.getRequestPartName() + "' is missing.", request);
    }

    /**
     * 400: the multipart body could not be parsed at all, e.g. an upload cut
     * off when the connection dropped. This is the client's request being
     * broken, not the server, so it must not fall through to the 500.
     * MaxUploadSizeExceededException is a subclass and keeps its own 413
     * handler below; Spring picks the most specific match.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMalformedMultipart(
            MultipartException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "The upload was malformed or incomplete. Record again and retry.", request);
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

    /** The final safety net for exceptions without a more specific mapping. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // The exception message is never logged: an upstream library can put
        // request details in it, sensitive headers included. Stack frames are
        // a different matter - class, method and line number cannot contain
        // request data, and without them a 500 in production is undiagnosable.
        // So the location is logged and the message is not.
        log.error("Unhandled exception: type={}, path={}, at={}",
                ex.getClass().getSimpleName(), request.getRequestURI(), origin(ex));
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred.", request);
    }

    /** The first few frames of the trace as one line, e.g. "Foo.bar(Foo.java:12) <- Baz.qux(Baz.java:34)". */
    private static String origin(Throwable ex) {
        StackTraceElement[] frames = ex.getStackTrace();
        StringBuilder at = new StringBuilder();
        for (int i = 0; i < Math.min(frames.length, 4); i++) {
            if (i > 0) {
                at.append(" <- ");
            }
            at.append(frames[i]);
        }
        return at.length() == 0 ? "(no stack trace)" : at.toString();
    }

    // Builds the response shape required by every error endpoint.

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                HttpServletRequest request) {
        return build(status, message, request, new HttpHeaders());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                HttpServletRequest request, HttpHeaders headers) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(clock).toString(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return new ResponseEntity<>(body, headers, status);
    }
}
