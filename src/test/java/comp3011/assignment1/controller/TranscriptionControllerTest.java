package comp3011.assignment1.controller;

import comp3011.assignment1.exception.SpeechToTextException;
import comp3011.assignment1.service.StubSpeechToTextService;
import comp3011.assignment1.service.TokenUsageStatisticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests for the browser-facing upload endpoint. The stub profile
 * keeps the tests deterministic and confirms controller behaviour without a
 * Cloud call or an API key.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("stub")
class TranscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenUsageStatisticsService statistics;

    @Autowired
    private StubSpeechToTextService stub;

    @AfterEach
    void clearInjectedFailure() {
        stub.resetMetrics();
    }

    @Test
    void validAudioReturnsOnlyTheTranscriptAndRecordsUsage() throws Exception {
        long inputTokensBefore = statistics.inputTokens();
        long outputTokensBefore = statistics.outputTokens();
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "recording.webm", "audio/webm", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/transcribe").file(audio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(StubSpeechToTextService.STUB_TEXT))
                // Token counts are internal statistics, not part of the public
                // transcription response contract.
                .andExpect(jsonPath("$.length()").value(1));

        assertThat(statistics.inputTokens()).isEqualTo(
                inputTokensBefore + StubSpeechToTextService.STUB_INPUT_TOKENS);
        assertThat(statistics.outputTokens()).isEqualTo(
                outputTokensBefore + StubSpeechToTextService.STUB_OUTPUT_TOKENS);
    }

    @Test
    void emptyAudioReturnsTheStandardBadRequestBody() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "recording.webm", "audio/webm", new byte[0]);

        mockMvc.perform(multipart("/api/v1/transcribe").file(audio))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Audio file must not be empty."))
                .andExpect(jsonPath("$.path").value("/api/v1/transcribe"))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void missingAudioPartAndNonMultipartBodyHaveHelpfulErrors() throws Exception {
        MockMultipartFile wrongPart = new MockMultipartFile(
                "different", "recording.webm", "audio/webm", new byte[]{1});

        mockMvc.perform(multipart("/api/v1/transcribe").file(wrongPart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required multipart part 'audio' is missing."))
                .andExpect(jsonPath("$.length()").value(5));

        mockMvc.perform(post("/api/v1/transcribe")
                        .contentType("application/octet-stream")
                        .content(new byte[]{1, 2, 3}))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.message").value("Audio must be uploaded as multipart/form-data."))
                .andExpect(jsonPath("$.path").value("/api/v1/transcribe"))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void unsupportedAudioTypeIsRejectedBeforeTheStubOrCloudServiceRuns() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "recording.txt", "text/plain", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/transcribe").file(audio))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value("Unsupported audio type: text/plain"))
                .andExpect(jsonPath("$.length()").value(5));
    }

    /**
     * The status chosen by the service must reach the client unchanged and
     * in the standard error shape. 504 is the one a client is most likely to
     * act on (retry), so it is the one pinned here.
     */
    @Test
    void upstreamTimeoutIsReportedWithItsOwnStatusAndTheStandardBody() throws Exception {
        stub.failNextCallWith(new SpeechToTextException(
                HttpStatus.GATEWAY_TIMEOUT, "Transcription service did not respond in time."));

        mockMvc.perform(multipart("/api/v1/transcribe").file(validAudio()))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.error").value("Gateway Timeout"))
                .andExpect(jsonPath("$.message").value("Transcription service did not respond in time."))
                .andExpect(jsonPath("$.path").value("/api/v1/transcribe"))
                .andExpect(jsonPath("$.length()").value(5));
    }

    /** A failed call produced no transcript, so it must not count towards usage. */
    @Test
    void failedTranscriptionDoesNotRecordTokenUsage() throws Exception {
        long inputTokensBefore = statistics.inputTokens();
        long outputTokensBefore = statistics.outputTokens();
        stub.failNextCallWith(new SpeechToTextException(
                HttpStatus.SERVICE_UNAVAILABLE, "Transcription service is unavailable."));

        mockMvc.perform(multipart("/api/v1/transcribe").file(validAudio()))
                .andExpect(status().isServiceUnavailable());

        assertThat(statistics.inputTokens()).isEqualTo(inputTokensBefore);
        assertThat(statistics.outputTokens()).isEqualTo(outputTokensBefore);
    }

    /**
     * Anything the service throws that is not a SpeechToTextException is
     * unanticipated, and its message is untrusted: a client library may quote
     * the request it was building. The client gets the fixed 500 body and
     * none of the exception text.
     */
    @Test
    void unexpectedServiceFailureIsAFixed500ThatEchoesNothing() throws Exception {
        String detail = "connection to upstream failed with header Authorization: Bearer sk-test-000";
        stub.failNextCallWith(new IllegalStateException(detail));

        String body = mockMvc.perform(multipart("/api/v1/transcribe").file(validAudio()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected server error occurred."))
                .andExpect(jsonPath("$.length()").value(5))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("sk-test").doesNotContain("Authorization");
    }

    private static MockMultipartFile validAudio() {
        return new MockMultipartFile("audio", "recording.webm", "audio/webm", new byte[]{1, 2, 3});
    }
}
