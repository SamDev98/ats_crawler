package dev.jobscanner.source.impl;

import dev.jobscanner.metrics.ScannerMetrics;
import dev.jobscanner.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AbstractJobSourceTest {

  private AbstractJobSource abstractJobSource;

  @Mock
  private ScannerMetrics metrics;

  @BeforeEach
  void setUp() {
    abstractJobSource = new AbstractJobSource(WebClient.builder(), metrics) {
      @Override
      protected List<String> getCompanies() {
        return List.of();
      }

      @Override
      protected Mono<List<Job>> fetchCompanyJobs(String company) {
        return Mono.just(List.of());
      }

      @Override
      public String getName() {
        return "TestProvider";
      }
    };
  }

  @Test
  void shouldStripHtmlCorrectly() {
    assertThat(abstractJobSource.stripHtml("<p>Hello <b>World</b></p>")).isEqualTo("Hello World");
    assertThat(abstractJobSource.stripHtml(null)).isEmpty();
    assertThat(abstractJobSource.stripHtml("   ")).isEmpty();
    assertThat(abstractJobSource.stripHtml("Plain Text")).isEqualTo("Plain Text");
  }

  @Test
  void shouldFormatCompanyName() {
    assertThat(abstractJobSource.formatCompanyName("google")).isEqualTo("Google");
    assertThat(abstractJobSource.formatCompanyName("nu-bank")).isEqualTo("Nu bank");
    assertThat(abstractJobSource.formatCompanyName("a")).isEqualTo("A");
    assertThat(abstractJobSource.formatCompanyName(null)).isEmpty();
    assertThat(abstractJobSource.formatCompanyName("")).isEmpty();
  }

  @Test
  void shouldCreateBaseJobWithDiscoveredAt() {
    Job job = abstractJobSource.baseJob().title("Test").build();
    assertThat(job.getSource()).isEqualTo("TestProvider");
    assertThat(job.getDiscoveredAt()).isNotNull();
  }
}
