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
class GreenhouseSourceTest {

  private MockWebServer mockWebServer;
  private GreenhouseSource greenhouseSource;

  @Mock
  private ScannerMetrics metrics;

  @Mock
  private SourcesConfig sourcesConfig;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    WebClient.Builder builder = WebClient.builder();
    greenhouseSource = new TestGreenhouseSource(builder, metrics, sourcesConfig, mockWebServer.url("/").toString());
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void shouldFetchAndMapJobsCorrectly() {
    String company = "test-company";
    when(sourcesConfig.getGreenhouse()).thenReturn(List.of(company));

    String jsonResponse = """
        {
            "jobs": [
                {
                    "id": 12345,
                    "title": "Staff Java Engineer",
                    "location": { "name": "Berlin, Germany" },
                    "absolute_url": "https://boards.greenhouse.io/test-company/jobs/12345"
                }
            ]
        }
        """;

    mockWebServer.enqueue(new MockResponse()
        .setBody(jsonResponse)
        .addHeader("Content-Type", "application/json"));

    StepVerifier.create(greenhouseSource.fetchJobs())
        .assertNext(job -> {
          assertThat(job.getTitle()).isEqualTo("Staff Java Engineer");
          assertThat(job.getCompany()).isEqualTo("Test company");
          assertThat(job.getLocation()).isEqualTo("Berlin, Germany");
          assertThat(job.getSource()).isEqualTo("Greenhouse");
        })
        .verifyComplete();
  }

  static class TestGreenhouseSource extends GreenhouseSource {
    private final String mockUrl;

    public TestGreenhouseSource(WebClient.Builder builder, ScannerMetrics metrics, SourcesConfig config, String url) {
      super(builder, metrics, config);
      this.mockUrl = url;
    }

    @Override
    protected String getApiUrl(String company) {
      return mockUrl;
    }
  }
}
