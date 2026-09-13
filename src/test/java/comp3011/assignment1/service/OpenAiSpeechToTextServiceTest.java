package comp3011.assignment1.service;

import comp3011.assignment1.config.OpenAiProperties;
import comp3011.assignment1.exception.SpeechToTextException;
import comp3011.assignment1.model.TranscriptionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Exercises the real adapter against a mocked upstream. MockRestServiceServer
 * replaces the HTTP transport underneath RestClient, so the request that the
 * adapter builds and the way it maps each upstream outcome are both tested
 * without a network, a key, or a paid call.
 *
 * The stub profile cannot cover this: it replaces the adapter entirely, so the
 * status mapping in OpenAiSpeechToTextService would otherwise only ever run in
 * production.
 */
class OpenAiSpeechToTextServiceTest {

    private static final String BASE_URL = "https://api.openai.com/v1";
    private static final String FAKE_KEY = "sk-test-not-a-real-key";
    private static final byte[] AUDIO = {1, 2, 3, 4};

    private MockRestServiceServer upstream;
    private OpenAiSpeechToTextService service;

    @BeforeEach
    void bindMockUpstream() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        upstream = MockRestServiceServer.bindTo(builder).build();
        service = new OpenAiSpeechToTextService(
                builder.build(),
                new OpenAiProperties(BASE_URL, "gpt-4o-mini-transcribe", FAKE_KEY));
    }

    @Test
    void sendsABearerTokenAndReadsTextAndUsageFromTheResponse() {
        upstream.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + FAKE_KEY))
                .andRespond(withSuccess(
                        "{\"text\":\"hello\",\"usage\":{\"input_tokens\":30,\"output_tokens\":8}}",
                        MediaType.APPLICATION_JSON));

        TranscriptionResult result = service.transcribe(AUDIO, "audio/webm");

        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.inputTokens()).isEqualTo(30);
        assertThat(result.outputTokens()).isEqualTo(8);
        upstream.verify();
    }

    @Test
    void missingUsageCountsAsZeroTokensRatherThanFailing() {
        upstream.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andRespond(withSuccess("{\"text\":\"hello\"}", MediaType.APPLICATION_JSON));

        TranscriptionResult result = service.transcribe(AUDIO, "audio/webm");

        assertThat(result.inputTokens()).isZero();
        assertThat(result.outputTokens()).isZero();
    }

    @Test
    void aResponseWithNoTranscriptIsA502NotAnEmptySuccess() {
        upstream.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.transcribe(AUDIO, "audio/webm"))
                .isInstanceOf(SpeechToTextException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void rateLimitingBecomes503() {
        upstream.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> service.transcribe(AUDIO, "audio/webm"))
                .isInstanceOf(SpeechToTextException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void anUpstreamPayloadTooLargeStays413() {
        upstream.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andRespond(withStatus(HttpStatus.CONTENT_TOO_LARGE));

        assertThatThrownBy(() -> service.transcribe(AUDIO, "audio/webm"))
                .isInstanceOf(SpeechToTextException.class)
                .extracting("status").isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    /**
     * An upstream 401 means this deployment is misconfigured, not that the
     * browser failed to authenticate, so it must not be forwarded as 401.
     */
    @Test
    void anyOtherUpstreamErrorBecomes502() {
        upstream.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.transcribe(AUDIO, "audio/webm"))
                .isInstanceOf(SpeechToTextException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void aTransportFailureBecomes504() {
        upstream.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andRespond(withException(new IOException("read timed out")));

        assertThatThrownBy(() -> service.transcribe(AUDIO, "audio/webm"))
                .isInstanceOf(SpeechToTextException.class)
                .extracting("status").isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }
}
