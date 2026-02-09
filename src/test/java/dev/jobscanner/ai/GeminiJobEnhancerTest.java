package dev.jobscanner.ai;

import dev.jobscanner.model.Job;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiJobEnhancerTest {

    private MockWebServer mockWebServer;
    private GeminiJobEnhancer enhancer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Create enhancer with mock server URL
        String baseUrl = mockWebServer.url("/").toString();
        enhancer = new TestableGeminiJobEnhancer("test-api-key", "gemini-1.5-flash", baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private Job createJob(String title, String description) {
        return Job.builder()
                .id("test-123")
                .title(title)
                .description(description)
                .url("https://example.com/job")
                .company("Test Company")
                .location("Remote")
                .source("Test")
                .discoveredAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should return true for isEnabled when API key is set")
    void shouldReturnTrueForIsEnabled() {
        assertThat(enhancer.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should return false for isEnabled when API key is blank")
    void shouldReturnFalseForIsEnabledWhenApiKeyBlank() {
        GeminiJobEnhancer blankKeyEnhancer = new TestableGeminiJobEnhancer("", "gemini-1.5-flash", "http://localhost");
        assertThat(blankKeyEnhancer.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should enhance job with AI analysis")
    void shouldEnhanceJobWithAIAnalysis() {
        String mockResponse = """
                {
                    "candidates": [{
                        "content": {
                            "parts": [{
                                "text": "1. **Stack Principal**: Java, Spring Boot, Kafka\\n2. **Nível**: Sênior\\n3. **Remote**: Sim, global"
                            }]
                        }
                    }]
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setHeader("Content-Type", "application/json"));

        Job job = createJob("Senior Java Developer", "We need someone with Java, Spring Boot, and Kafka experience.");

        StepVerifier.create(enhancer.enhance(job))
                .assertNext(result -> assertThat(result.getAiAnalysis())
                        .isNotNull()
                        .contains("Stack Principal"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return job unchanged when description is null")
    void shouldReturnJobUnchangedWhenDescriptionNull() {
        Job job = Job.builder()
                .id("test-123")
                .title("Java Developer")
                .description(null)
                .url("https://example.com/job")
                .company("Test Company")
                .location("Remote")
                .source("Test")
                .discoveredAt(Instant.now())
                .build();

        StepVerifier.create(enhancer.enhance(job))
                .assertNext(result -> {
                    assertThat(result).isSameAs(job);
                    assertThat(result.getAiAnalysis()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return job unchanged when description is blank")
    void shouldReturnJobUnchangedWhenDescriptionBlank() {
        Job job = createJob("Java Developer", "   ");

        StepVerifier.create(enhancer.enhance(job))
                .assertNext(result -> {
                    assertThat(result).isSameAs(job);
                    assertThat(result.getAiAnalysis()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle API error gracefully")
    void shouldHandleApiErrorGracefully() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        Job job = createJob("Java Developer", "Test description");

        StepVerifier.create(enhancer.enhance(job))
                .assertNext(result -> {
                    assertThat(result).isSameAs(job);
                    assertThat(result.getAiAnalysis()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle empty response gracefully")
    void shouldHandleEmptyResponseGracefully() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"candidates\": []}")
                .setHeader("Content-Type", "application/json"));

        Job job = createJob("Java Developer", "Test description");

        StepVerifier.create(enhancer.enhance(job))
                .assertNext(result -> {
                    assertThat(result).isSameAs(job);
                    assertThat(result.getAiAnalysis()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should enhance all jobs in list")
    void shouldEnhanceAllJobsInList() {
        String mockResponse = """
                {
                    "candidates": [{
                        "content": {
                            "parts": [{
                                "text": "Analysis result"
                            }]
                        }
                    }]
                }
                """;
        mockWebServer.enqueue(new MockResponse().setBody(mockResponse).setHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setBody(mockResponse).setHeader("Content-Type", "application/json"));

        List<Job> jobs = List.of(
                createJob("Java Developer 1", "Description 1"),
                createJob("Java Developer 2", "Description 2"));

        StepVerifier.create(enhancer.enhanceAll(jobs))
                .assertNext(result -> assertThat(result).hasSize(2))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return empty list when input is empty")
    void shouldReturnEmptyListWhenInputEmpty() {
        StepVerifier.create(enhancer.enhanceAll(List.of()))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("Should truncate very long descriptions")
    void shouldTruncateVeryLongDescriptions() {
        String mockResponse = """
                {
                    "candidates": [{
                        "content": {
                            "parts": [{
                                "text": "Analysis of truncated content"
                            }]
                        }
                    }]
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setHeader("Content-Type", "application/json"));

        String longDescription = "A".repeat(10000);
        Job job = createJob("Java Developer", longDescription);

        StepVerifier.create(enhancer.enhance(job))
                .assertNext(result -> assertThat(result.getAiAnalysis()).isNotNull())
                .verifyComplete();
    }

    /**
     * Testable version that allows injecting a custom base URL.
     */
    static class TestableGeminiJobEnhancer extends GeminiJobEnhancer {
        TestableGeminiJobEnhancer(String apiKey, String model, String baseUrl) {
            super(apiKey, model, baseUrl, "/v1beta/models/%s:generateContent");
        }
    }
}
