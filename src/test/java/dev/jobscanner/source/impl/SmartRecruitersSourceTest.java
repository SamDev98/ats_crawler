package dev.jobscanner.source.impl;

import dev.jobscanner.config.SourcesConfig;
import dev.jobscanner.metrics.ScannerMetrics;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartRecruitersSourceTest {

    private MockWebServer mockWebServer;
    private SmartRecruitersSource smartRecruitersSource;

    @Mock
    private ScannerMetrics metrics;

    @Mock
    private SourcesConfig sourcesConfig;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient.Builder builder = WebClient.builder();
        smartRecruitersSource = new TestSmartRecruitersSource(builder, metrics, sourcesConfig,
                mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldFetchAndMapJobsCorrectly() {
        String company = "test-company";
        when(sourcesConfig.getSmartrecruiters()).thenReturn(List.of(company));

        String jsonResponse = """
                {
                    "content": [
                        {
                            "id": "abc-123",
                            "name": "Backend Engineer (Java)",
                            "location": { "city": "Sao Paulo", "country": "BR" },
                            "ref": "https://api.smartrecruiters.com/jobs/1",
                            "postingUrl": "https://jobs.test.com/1",
                            "company": { "name": "Real Company" },
                            "jobAd": {
                                "sections": {
                                    "jobDescription": { "text": "desc" },
                                    "qualifications": { "text": "qual" }
                                }
                            }
                        }
                    ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        StepVerifier.create(smartRecruitersSource.fetchJobs())
                .assertNext(job -> {
                    assertThat(job.getTitle()).isEqualTo("Backend Engineer (Java)");
                    assertThat(job.getCompany()).isEqualTo("Real Company");
                    assertThat(job.getLocation()).isEqualTo("Sao Paulo");
                    assertThat(job.getDescription()).contains("desc qual");
                    assertThat(job.getUrl()).isEqualTo("https://jobs.test.com/1");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleNullContent() {
        String company = "test-company";
        when(sourcesConfig.getSmartrecruiters()).thenReturn(List.of(company));

        mockWebServer.enqueue(new MockResponse()
                .setBody("{ \"content\": null }")
                .addHeader("Content-Type", "application/json"));

        StepVerifier.create(smartRecruitersSource.fetchJobs())
                .verifyComplete();
    }

    @Test
    void shouldHandleMissingLocationAndCompany() {
        String company = "test-company";
        when(sourcesConfig.getSmartrecruiters()).thenReturn(List.of(company));

        String jsonResponse = "{ \"content\": [ { \"id\": \"1\", \"name\": \"Job\" } ] }";

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        StepVerifier.create(smartRecruitersSource.fetchJobs())
                .assertNext(job -> {
                    assertThat(job.getCompany()).isEqualTo("Test company");
                    assertThat(job.getLocation()).isEmpty();
                    assertThat(job.getUrl()).contains("/test-company/1");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleCountryIfCityMissing() {
        String company = "test-company";
        when(sourcesConfig.getSmartrecruiters()).thenReturn(List.of(company));

        String jsonResponse = "{ \"content\": [ { \"id\": \"1\", \"name\": \"Job\", \"location\": { \"country\": \"Brazil\" } } ] }";

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        StepVerifier.create(smartRecruitersSource.fetchJobs())
                .assertNext(job -> assertThat(job.getLocation()).isEqualTo("Brazil"))
                .verifyComplete();
    }

    @Test
    void shouldHandleMissingJobAdSections() {
        String company = "test-company";
        when(sourcesConfig.getSmartrecruiters()).thenReturn(List.of(company));

        String jsonResponse = """
                {
                    "content": [
                        {
                            "id": "1",
                            "name": "Job",
                            "jobAd": { "sections": null }
                        }
                    ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        StepVerifier.create(smartRecruitersSource.fetchJobs())
                .assertNext(job -> assertThat(job.getDescription()).isEmpty())
                .verifyComplete();
    }

    @Test
    void shouldHandleOnlyQualifications() {
        String company = "test-company";
        when(sourcesConfig.getSmartrecruiters()).thenReturn(List.of(company));

        String jsonResponse = """
                {
                    "content": [
                        {
                            "id": "1",
                            "name": "Job",
                            "jobAd": {
                                "sections": {
                                    "jobDescription": null,
                                    "qualifications": { "text": "Must know Java" }
                                }
                            }
                        }
                    ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        StepVerifier.create(smartRecruitersSource.fetchJobs())
                .assertNext(job -> assertThat(job.getDescription()).isEqualTo("Must know Java"))
                .verifyComplete();
    }

    static class TestSmartRecruitersSource extends SmartRecruitersSource {
        private final String mockUrl;

        public TestSmartRecruitersSource(WebClient.Builder builder, ScannerMetrics metrics, SourcesConfig config,
                String url) {
            super(builder, metrics, config);
            this.mockUrl = url;
        }

        @Override
        protected String getApiUrl(String company) {
            return mockUrl;
        }
    }
}
