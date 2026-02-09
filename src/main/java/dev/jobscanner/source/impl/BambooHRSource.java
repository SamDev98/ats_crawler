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
import java.util.Objects;

@Slf4j
@Component
public class BambooHRSource extends AbstractJobSource {

  private static final String API_URL_TEMPLATE = "https://%s.bamboohr.com/jobs/jobs.php?inline=true";

  private final SourcesConfig sourcesConfig;

  public BambooHRSource(WebClient.Builder webClientBuilder, ScannerMetrics metrics, SourcesConfig sourcesConfig) {
    super(webClientBuilder, metrics);
    this.sourcesConfig = sourcesConfig;
  }

  @Override
  public String getName() {
    return "BambooHR";
  }

  @Override
  protected List<String> getCompanies() {
    return sourcesConfig.getBamboohr();
  }

  @Override
  protected Mono<List<Job>> fetchCompanyJobs(String company) {
    String url = String.format(API_URL_TEMPLATE, company);

    return webClient.get()
        .uri(Objects.requireNonNull(url))
        .header("X-Requested-With", "XMLHttpRequest")
        .header("Referer", "https://" + company + ".bamboohr.com/jobs/")
        .retrieve()
        .bodyToMono(BambooHRJob[].class)
        .timeout(java.time.Duration.ofSeconds(30))
        .map(response -> {
          if (response == null) {
            return List.<Job>of();
          }
          return java.util.Arrays.stream(response)
              .map(job -> mapToJob(job, company))
              .toList();
        });
  }

  private Job mapToJob(BambooHRJob bambooJob, String company) {
    return baseJob()
        .title(bambooJob.getJobTitle())
        .url("https://" + company + ".bamboohr.com/jobs/view.php?id=" + bambooJob.getId())
        .company(formatCompanyName(company))
        .location(bambooJob.getLocation() != null ? bambooJob.getLocation() : "")
        .description(bambooJob.getJobTitle()) // BambooHR embed API has limited description in JSON
        .build();
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class BambooHRJob {
    private String id;
    private String jobTitle;
    private String location;
  }
}
