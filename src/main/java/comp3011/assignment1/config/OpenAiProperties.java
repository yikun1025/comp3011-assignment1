package comp3011.assignment1.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the upstream speech-to-text service, bound from the
 * app.openai.* properties. Registered only under the non-stub profile, so the
 * stub profile does not require an API key to exist.
 */
@ConfigurationProperties(prefix = "app.openai")
@Validated
public record OpenAiProperties(
        String baseUrl,
        String model,
        @NotBlank(message = "OPENAI_API_KEY must be set when the real STT service is active.")
        String apiKey
) {

    /**
     * The generated record toString() would print the API key. Spring prints the
     * whole properties object when configuration binding fails, so the default
     * version would put the key straight into the logs.
     */
    @Override
    public String toString() {
        return "OpenAiProperties[baseUrl=" + baseUrl
                + ", model=" + model
                + ", apiKey=***]";
    }
}