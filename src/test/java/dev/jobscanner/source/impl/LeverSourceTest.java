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
class LeverSourceTest {

  private MockWebServer mockWebServer;
  private LeverSource leverSource;

  @Mock
  private ScannerMetrics metrics;

  @Mock
  private SourcesConfig sourcesConfig;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    WebClient.Builder builder = WebClient.builder();
    leverSource = new TestLeverSource(builder, metrics, sourcesConfig, mockWebServer.url("/").toString());
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void shouldFetchAndMapJobsCorrectly() {
    String company = "test-company";
    when(sourcesConfig.getLever()).thenReturn(List.of(company));

    String jsonResponse = """
        [
            {
                "text": "Senior Java Developer",
                "hostedUrl": "https://lever.co/test/1",
                "descriptionPlain": "Lever description",
                "categories": { "location": "Remote, Brazil" }
            }
        ]
        """;

    mockWebServer.enqueue(new MockResponse()
        .setBody(jsonResponse)
        .addHeader("Content-Type", "application/json"));

    StepVerifier.create(leverSource.fetchJobs())
        .assertNext(job -> {
          assertThat(job.getTitle()).isEqualTo("Senior Java Developer");
          assertThat(job.getCompany()).isEqualTo("Test company");
          assertThat(job.getLocation()).isEqualTo("Remote, Brazil");
          assertThat(job.getSource()).isEqualTo("Lever");
        })
        .verifyComplete();
  }

  static class TestLeverSource extends LeverSource {
    private final String mockUrl;

    public TestLeverSource(WebClient.Builder builder, ScannerMetrics metrics, SourcesConfig config, String url) {
      super(builder, metrics, config);
      this.mockUrl = url;
    }

    @Override
    protected String getApiUrl(String company) {
      return mockUrl;
    }
  }
}
