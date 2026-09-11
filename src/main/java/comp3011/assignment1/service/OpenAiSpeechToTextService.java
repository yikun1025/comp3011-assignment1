package comp3011.assignment1.service;

import comp3011.assignment1.config.OpenAiProperties;
import comp3011.assignment1.exception.SpeechToTextException;
import comp3011.assignment1.model.TranscriptionResult;
import comp3011.assignment1.util.AudioFilenames;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

/**
 * Calls the Cloud STT provider over HTTP.
 */
@Service
@Profile("!stub")
public class OpenAiSpeechToTextService implements SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSpeechToTextService.class);

    private static final String UNAVAILABLE_MESSAGE = "Transcription service is unavailable.";
    private static final String TOO_LARGE_MESSAGE = "Recording is too large to transcribe.";
    private static final String TIMEOUT_MESSAGE = "Transcription service did not respond in time.";

    private final RestClient restClient;
    private final OpenAiProperties properties;

    public OpenAiSpeechToTextService(RestClient restClient, OpenAiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public TranscriptionResult transcribe(byte[] audio, String contentType) {
        String filename = AudioFilenames.filenameFor(contentType);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", asUploadedFile(audio, filename));
        body.add("model", properties.model());

        log.info("STT request starting: bytes={}, filename={}, model={}",
                audio.length, filename, properties.model());

        long startedAt = System.nanoTime();
        OpenAiTranscriptionResponse response;
        try {
            response = restClient.post()
                    .uri("/audio/transcriptions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, upstream) -> {
                        throw mapUpstreamError(upstream.getStatusCode());
                    })
                    .body(OpenAiTranscriptionResponse.class);
        } catch (ResourceAccessException ex) {
            log.warn("STT request failed to complete after {} ms; responding 504",
                    elapsedMillisSince(startedAt));
            throw new SpeechToTextException(HttpStatus.GATEWAY_TIMEOUT, TIMEOUT_MESSAGE, ex);
        }

        long elapsedMillis = elapsedMillisSince(startedAt);

        if (response == null || response.text() == null) {
            log.warn("STT upstream returned no transcript after {} ms; responding 502", elapsedMillis);
            throw new SpeechToTextException(HttpStatus.BAD_GATEWAY, UNAVAILABLE_MESSAGE);
        }

        long inputTokens = response.usage() == null ? 0L : response.usage().inputTokens();
        long outputTokens = response.usage() == null ? 0L : response.usage().outputTokens();

        log.info("STT request succeeded: status=200, elapsedMs={}, inputTokens={}, outputTokens={}",
                elapsedMillis, inputTokens, outputTokens);

        return new TranscriptionResult(response.text(), inputTokens, outputTokens);
    }

    private ByteArrayResource asUploadedFile(byte[] audio, String filename) {
        return new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private SpeechToTextException mapUpstreamError(HttpStatusCode upstreamStatus) {
        int code = upstreamStatus.value();
        SpeechToTextException translated = switch (code) {
            case 429 -> new SpeechToTextException(
                    HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
            case 413 -> new SpeechToTextException(
                    HttpStatus.CONTENT_TOO_LARGE, TOO_LARGE_MESSAGE);
            default -> new SpeechToTextException(
                    HttpStatus.BAD_GATEWAY, UNAVAILABLE_MESSAGE);
        };
        log.warn("STT upstream returned {}; responding {}", code, translated.getStatus().value());
        return translated;
    }

    private static long elapsedMillisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * The slice of the provider's response this application depends on.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAiTranscriptionResponse(String text, Usage usage) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Usage(
                @com.fasterxml.jackson.annotation.JsonProperty("input_tokens") long inputTokens,
                @com.fasterxml.jackson.annotation.JsonProperty("output_tokens") long outputTokens) {
        }
    }
}