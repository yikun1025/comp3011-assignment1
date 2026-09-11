package comp3011.assignment1;

import static org.assertj.core.api.Assertions.assertThat;

import comp3011.assignment1.config.OpenAiProperties;
import comp3011.assignment1.service.SpeechToTextService;
import comp3011.assignment1.service.StubSpeechToTextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the local development path needs no credential.
 *
 * <p>The second assertion is the load-bearing one. Selecting the stub
 * implementation is not enough on its own: if {@code OpenAiProperties} were
 * still created, its {@code @NotBlank} key would fail validation on any machine
 * without the environment variable set, and the test suite would stop being
 * runnable offline.
 *
 * <p>Written with AI assistance (Claude); see ACKNOWLEDGEMENTS.md.
 */
@SpringBootTest
@ActiveProfiles("stub")
class StubWiringTest {

    @Autowired
    private SpeechToTextService speechToTextService;

    @Autowired
    private ApplicationContext context;

    @Test
    void stubProfileUsesTheStubService() {
        assertThat(speechToTextService).isInstanceOf(StubSpeechToTextService.class);
    }

    @Test
    void stubProfileNeedsNoApiKey() {
        assertThat(context.getBeanNamesForType(OpenAiProperties.class)).isEmpty();
    }
}