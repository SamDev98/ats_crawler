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
class WorkableSourceTest {

  private MockWebServer mockWebServer;
  private WorkableSource workableSource;

  @Mock
  private ScannerMetrics metrics;

  @Mock
  private SourcesConfig sourcesConfig;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    WebClient.Builder builder = WebClient.builder();
    workableSource = new TestWorkableSource(builder, metrics, sourcesConfig, mockWebServer.url("/").toString());
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void shouldFetchAndMapJobsCorrectly() {
    String company = "test-company";
    when(sourcesConfig.getWorkable()).thenReturn(List.of(company));

    String jsonResponse = """
        {
            "results": [
                {
                    "id": "1",
                    "title": "Principal Java Engineer",
                    "shortcode": "test-job-slug",
                    "description": "Workable description",
                    "location": { "location_str": "London, UK" }
                }
            ]
        }
        """;

    mockWebServer.enqueue(new MockResponse()
        .setBody(jsonResponse)
        .addHeader("Content-Type", "application/json"));

    StepVerifier.create(workableSource.fetchJobs())
        .assertNext(job -> {
          assertThat(job.getTitle()).isEqualTo("Principal Java Engineer");
          assertThat(job.getCompany()).isEqualTo("Test company");
          assertThat(job.getLocation()).isEqualTo("London, UK");
          assertThat(job.getSource()).isEqualTo("Workable");
          assertThat(job.getUrl()).contains("test-job-slug");
        })
        .verifyComplete();
  }

  static class TestWorkableSource extends WorkableSource {
    private final String mockUrl;

    public TestWorkableSource(WebClient.Builder builder, ScannerMetrics metrics, SourcesConfig config, String url) {
      super(builder, metrics, config);
      this.mockUrl = url;
    }

    @Override
    protected String getApiUrl(String company) {
      return mockUrl;
    }
  }
}
