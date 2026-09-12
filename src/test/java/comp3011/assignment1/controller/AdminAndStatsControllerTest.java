package comp3011.assignment1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import comp3011.assignment1.service.ShutdownExecutor;

/**
 * Regression tests for the two administrative endpoints and the statistics
 * endpoint.
 * Run under the stub profile, so no API key and no network are involved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("stub")
@Import(AdminAndStatsControllerTest.FakeShutdownConfig.class)
class AdminAndStatsControllerTest {

    /**
     * Replaces the real executor for this test context. Records invocations
     * instead of closing the application - otherwise the first test to request
     * a shutdown would take every later test down with it.
     */
    @TestConfiguration
    static class FakeShutdownConfig {

        static final AtomicInteger invocations = new AtomicInteger();

        @Bean
        @Primary
        ShutdownExecutor shutdownExecutor() {
            return new ShutdownExecutor() {
                @Override
                public void shutdown() {
                    invocations.incrementAndGet();
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void statsReturnsBothCountersAsIntegers() throws Exception {
        mockMvc.perform(get("/api/v1/global/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputTokens").isNumber())
                .andExpect(jsonPath("$.outputTokens").isNumber())
                // additionalProperties: false in the spec - a third field would
                // break the contract, so assert the shape is exactly two.
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void firstShutdownIsAcceptedAndSecondIsRejected() throws Exception {
        int before = FakeShutdownConfig.invocations.get();

        mockMvc.perform(post("/api/v1/admin/shutdown"))
                .andExpect(status().isAccepted())
                // Wording is fixed by the spec, so assert it verbatim rather
                // than loosely - a reworded message is a broken contract.
                .andExpect(jsonPath("$.message").value("Graceful shutdown requested."))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/v1/admin/shutdown"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Graceful shutdown is already in progress."))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/shutdown"));

        // The real point of this test: the rejected request must not have
        // started a second shutdown, only reported one.
        assertThat(FakeShutdownConfig.invocations.get() - before).isEqualTo(1);
    }
}
