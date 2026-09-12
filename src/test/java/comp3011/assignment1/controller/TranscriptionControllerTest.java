package comp3011.assignment1.controller;

import comp3011.assignment1.service.StubSpeechToTextService;
import comp3011.assignment1.service.TokenUsageStatisticsService;
import org.junit.jupiter.api.Test;
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
}
