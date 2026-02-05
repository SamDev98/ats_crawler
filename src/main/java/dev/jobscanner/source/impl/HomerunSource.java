package dev.jobscanner.source.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jobscanner.config.SourcesConfig;
import dev.jobscanner.metrics.ScannerMetrics;
import dev.jobscanner.model.Job;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class HomerunSource extends AbstractJobSource {

  // Homerun pattern: https://{company}.homerun.co/jobs.json
  private static final String API_URL = "https://%s.homerun.co/jobs.json";

  private final SourcesConfig sourcesConfig;

  public HomerunSource(WebClient.Builder webClientBuilder, ScannerMetrics metrics, SourcesConfig sourcesConfig) {
    super(webClientBuilder, metrics);
    this.sourcesConfig = sourcesConfig;
  }

  @Override
  public String getName() {
    return "Homerun";
  }

  @Override
  protected List<String> getCompanies() {
    return sourcesConfig.getHomerun();
  }

  @Override
  protected Mono<List<Job>> fetchCompanyJobs(String company) {
    String url = String.format(API_URL, company);

    return timedGet(url, HomerunResponse.class)
        .map(response -> {
          if (response == null || response.getJobs() == null)
            return List.<Job>of();
          return response.getJobs().stream()
              .map(job -> mapToJob(job, company))
              .toList();
        });
  }

  private Job mapToJob(HomerunJob hJob, String company) {
    return baseJob()
        .title(hJob.getTitle())
        .url(hJob.getUrl())
        .company(formatCompanyName(company))
        .location(hJob.getLocation() != null ? hJob.getLocation().getCity() : "")
        .description(stripHtml(hJob.getDescription()))
        .build();
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class HomerunResponse {
    private List<HomerunJob> jobs;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class HomerunJob {
    private String title;
    private String url;
    private String description;
    private HomerunLocation location;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class HomerunLocation {
    private String city;
  }
}
