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
class AshbySourceTest {

  private MockWebServer mockWebServer;
  private AshbySource ashbySource;

  @Mock
  private ScannerMetrics metrics;

  @Mock
  private SourcesConfig sourcesConfig;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    WebClient.Builder builder = WebClient.builder();
    ashbySource = new TestAshbySource(builder, metrics, sourcesConfig, mockWebServer.url("/").toString());
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void shouldFetchAndMapJobsCorrectly() {
    String company = "test-company";
    when(sourcesConfig.getAshby()).thenReturn(List.of(company));

    String jsonResponse = """
        {
            "data": {
                "jobBoard": {
                    "jobPostings": [
                        {
                            "id": "job-1",
                            "title": "Java Engineer",
                            "locationName": "Remote",
                            "descriptionPlain": "Ashby description"
                        }
                    ]
                }
            }
        }
        """;

    mockWebServer.enqueue(new MockResponse()
        .setBody(jsonResponse)
        .addHeader("Content-Type", "application/json"));

    StepVerifier.create(ashbySource.fetchJobs())
        .assertNext(job -> {
          assertThat(job.getTitle()).isEqualTo("Java Engineer");
          assertThat(job.getCompany()).isEqualTo("Test company");
          assertThat(job.getLocation()).isEqualTo("Remote");
          assertThat(job.getSource()).isEqualTo("Ashby");
        })
        .verifyComplete();
  }

  static class TestAshbySource extends AshbySource {
    private final String mockUrl;

    public TestAshbySource(WebClient.Builder builder, ScannerMetrics metrics, SourcesConfig config, String url) {
      super(builder, metrics, config);
      this.mockUrl = url;
    }

    @Override
    protected String getApiUrl() {
      return mockUrl;
    }
  }
}
