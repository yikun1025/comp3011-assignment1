package comp3011.assignment1.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@Profile("!stub")
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiClientConfig {

    /**
     * The HTTP client used to call the upstream speech-to-text service.
     *
     * Both timeouts are bounded. An unbounded read timeout would let a stalled
     * upstream pin a request thread indefinitely, which under concurrent load
     * would exhaust the thread pool. The read timeout is set well above the
     * expected transcription time but far below anything a user would wait for.
     */
    @Bean
    RestClient openAiRestClient(OpenAiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }
}