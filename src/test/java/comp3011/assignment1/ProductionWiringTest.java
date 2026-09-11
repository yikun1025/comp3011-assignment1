package comp3011.assignment1;

import comp3011.assignment1.config.OpenAiProperties;
import comp3011.assignment1.service.OpenAiSpeechToTextService;
import comp3011.assignment1.service.SpeechToTextService;
import org.springframework.beans.factory.annotation.Autowired;

public class ProductionWiringTest {
}
package comp3011.assignment1;

import static org.assertj.core.api.Assertions.assertThat;

import comp3011.assignment1.config.OpenAiProperties;
import comp3011.assignment1.service.OpenAiSpeechToTextService;
import comp3011.assignment1.service.SpeechToTextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Guards the wiring that only ever runs in the marking environment.
 */
@SpringBootTest
@TestPropertySource(properties = "app.openai.api-key=test-key-not-a-real-credential")
class ProductionWiringTest {

    @Autowired
    private SpeechToTextService speechToTextService;

    @Autowired
    private OpenAiProperties properties;

    @Test
    void defaultProfileUsesTheRealService() {
        assertThat(speechToTextService).isInstanceOf(OpenAiSpeechToTextService.class);
    }

    @Test
    void modelNameMatchesTheSpecificationExactly() {
        assertThat(properties.model()).isEqualTo("gpt-4o-mini-transcribe");
    }

    @Test
    void baseUrlPointsAtTheProvider() {
        assertThat(properties.baseUrl()).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void toStringDoesNotExposeTheKey() {
        assertThat(properties.toString()).doesNotContain("test-key-not-a-real-credential");
    }
}
