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
class TeamtailorSourceTest {

  private MockWebServer mockWebServer;
  private TeamtailorSource teamtailorSource;

  @Mock
  private ScannerMetrics metrics;

  @Mock
  private SourcesConfig sourcesConfig;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    WebClient.Builder builder = WebClient.builder();
    teamtailorSource = new TestTeamtailorSource(builder, metrics, sourcesConfig, mockWebServer.url("/").toString());
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void shouldFetchAndMapJobsCorrectly() {
    String company = "test-company";
    when(sourcesConfig.getTeamtailor()).thenReturn(List.of(company));

    String jsonResponse = """
        {
            "data": [
                {
                    "id": "1",
                    "type": "jobs",
                    "attributes": {
                        "title": "Java Architect",
                        "body": "<p>Teamtailor description</p>",
                        "careersite-job-url": "https://careers.test.com/1"
                    },
                    "relationships": {
                        "locations": {
                            "data": [ { "id": "loc-1", "type": "locations" } ]
                        }
                    }
                }
            ],
            "included": [
                {
                    "id": "loc-1",
                    "type": "locations",
                    "attributes": { "name": "London" }
                }
            ]
        }
        """;

    mockWebServer.enqueue(new MockResponse()
        .setBody(jsonResponse)
        .addHeader("Content-Type", "application/json"));

    StepVerifier.create(teamtailorSource.fetchJobs())
        .assertNext(job -> {
          assertThat(job.getTitle()).isEqualTo("Java Architect");
          assertThat(job.getCompany()).isEqualTo("Test company");
          assertThat(job.getLocation()).isEqualTo("London");
          assertThat(job.getDescription()).isEqualTo("Teamtailor description");
          assertThat(job.getSource()).isEqualTo("Teamtailor");
          assertThat(job.getUrl()).isEqualTo("https://careers.test.com/1");
        })
        .verifyComplete();
  }

  @Test
  void shouldHandleNullData() {
    String company = "test-company";
    when(sourcesConfig.getTeamtailor()).thenReturn(List.of(company));

    mockWebServer.enqueue(new MockResponse()
        .setBody("{ \"data\": null }")
        .addHeader("Content-Type", "application/json"));

    StepVerifier.create(teamtailorSource.fetchJobs())
        .verifyComplete();
  }

  @Test
  void shouldHandleMissingOptionalFields() {
    String company = "test-company";
    when(sourcesConfig.getTeamtailor()).thenReturn(List.of(company));

    String jsonResponse = """
        {
            "data": [ { "id": "1", "attributes": { "title": "Job" } } ]
        }
        """;

    mockWebServer.enqueue(new MockResponse()
        .setBody(jsonResponse)
        .addHeader("Content-Type", "application/json"));

    StepVerifier.create(teamtailorSource.fetchJobs())
        .assertNext(job -> {
          assertThat(job.getTitle()).isEqualTo("Job");
          assertThat(job.getUrl()).contains("teamtailor.com/jobs/1");
          assertThat(job.getLocation()).isEmpty();
        })
        .verifyComplete();
  }

  @Test
  void shouldHandleMismatchedIncludedType() {
    String company = "test-company";
    when(sourcesConfig.getTeamtailor()).thenReturn(List.of(company));

    String jsonResponse = """
        {
            "data": [
                {
                    "id": "1",
                    "attributes": { "title": "Job" },
                    "relationships": {
                        "locations": {
                            "data": [ { "id": "loc-1", "type": "locations" } ]
                        }
                    }
                }
            ],
            "included": [
                {
                    "id": "loc-1",
                    "type": "not-locations",
                    "attributes": { "name": "London" }
                }
            ]
        }
        """;

    mockWebServer.enqueue(new MockResponse()
        .setBody(jsonResponse)
        .addHeader("Content-Type", "application/json"));

    StepVerifier.create(teamtailorSource.fetchJobs())
        .assertNext(job -> assertThat(job.getLocation()).isEmpty())
        .verifyComplete();
  }

  @Test
  void shouldHandleNullIncludedOrRelationships() {
    String company = "test-company";
    when(sourcesConfig.getTeamtailor()).thenReturn(List.of(company));

    String jsonResponse = """
        {
            "data": [
                {
                    "id": "1",
                    "attributes": { "title": "Job" },
                    "relationships": null
                }
            ],
            "included": null
        }
        """;

    mockWebServer.enqueue(new MockResponse()
        .setBody(jsonResponse)
        .addHeader("Content-Type", "application/json"));

    StepVerifier.create(teamtailorSource.fetchJobs())
        .assertNext(job -> assertThat(job.getLocation()).isEmpty())
        .verifyComplete();
  }

  @Test
  void shouldHandleError() {
    String company = "test-company";
    when(sourcesConfig.getTeamtailor()).thenReturn(List.of(company));

    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    StepVerifier.create(teamtailorSource.fetchJobs())
        .verifyComplete();
  }

  static class TestTeamtailorSource extends TeamtailorSource {
    private final String mockUrl;

    public TestTeamtailorSource(WebClient.Builder builder, ScannerMetrics metrics, SourcesConfig config, String url) {
      super(builder, metrics, config);
      this.mockUrl = url;
    }

    @Override
    protected String getApiUrl(String company) {
      return mockUrl;
    }
  }
}
