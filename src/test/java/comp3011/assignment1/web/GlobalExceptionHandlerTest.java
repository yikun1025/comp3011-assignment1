package comp3011.assignment1.web;

import comp3011.assignment1.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A generic exception may originate in an HTTP library. Its message is not a
 * safe logging surface because it can include request details or credentials.
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    @Test
    void unexpectedExceptionDoesNotWriteItsMessageToTheLog(CapturedOutput output) {
        String secretLikeValue = "test-key-must-never-appear-in-a-log";
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/transcribe");
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC));

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException(secretLikeValue), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .extracting(ErrorResponse::message, ErrorResponse::path)
                .containsExactly("An unexpected server error occurred.", "/api/v1/transcribe");
        assertThat(output.getAll()).doesNotContain(secretLikeValue);
        assertThat(output.getAll()).contains("IllegalStateException", "/api/v1/transcribe");
        // The location is what makes the log line useful. The exception was
        // constructed in this test method, so that frame must be present.
        assertThat(output.getAll()).contains("GlobalExceptionHandlerTest.java");
    }
}
