package dev.jobscanner.source.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jobscanner.config.SourcesConfig;
import dev.jobscanner.metrics.ScannerMetrics;
import dev.jobscanner.model.Job;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ComeetSource extends AbstractJobSource {

  // Comeet pattern:
  // https://www.comeet.co/careers-api/2.0/company/{uid}/positions?token={token}
  // We expect company config to be "uid:token"
  private static final String API_URL = "https://www.comeet.co/careers-api/2.0/company/%s/positions?token=%s";

  private final SourcesConfig sourcesConfig;

  public ComeetSource(WebClient.Builder webClientBuilder, ScannerMetrics metrics, SourcesConfig sourcesConfig) {
    super(webClientBuilder, metrics);
    this.sourcesConfig = sourcesConfig;
  }

  @Override
  public String getName() {
    return "Comeet";
  }

  @Override
  protected List<String> getCompanies() {
    return sourcesConfig.getComeet();
  }

  @Override
  protected Mono<List<Job>> fetchCompanyJobs(String companyConfig) {
    String[] parts = companyConfig.split(":");
    if (parts.length != 2) {
      log.warn("Comeet company config must be in format 'uid:token', got: {}", companyConfig);
      return Mono.just(List.of());
    }

    String uid = parts[0];
    String token = parts[1];
    String url = String.format(API_URL, uid, token);

    return timedGet(url, ComeetJob[].class)
        .map(response -> {
          if (response == null)
            return List.<Job>of();
          return java.util.Arrays.stream(response)
              .map(job -> mapToJob(job, uid))
              .toList();
        });
  }

  private Job mapToJob(ComeetJob cJob, String companyId) {
    return baseJob()
        .title(cJob.getName())
        .url(cJob.getUrlComeet())
        .company(formatCompanyName(companyId))
        .location(cJob.getLocation() != null ? cJob.getLocation().getDisplayName() : "")
        .description(stripHtml(cJob.getDescription())) // Comeet returns HTML description
        .build();
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class ComeetJob {
    private String name;
    @JsonProperty("url_comeet")
    private String urlComeet;
    private String description;
    private ComeetLocation location;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class ComeetLocation {
    @JsonProperty("display_name")
    private String displayName;
  }
}
