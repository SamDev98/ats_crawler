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
import java.util.Map;

@Slf4j
@Component
public class GreenhouseSource extends AbstractJobSource {

    private static final String API_URL = "https://boards-api.greenhouse.io/v1/boards/%s/jobs?content=true";

    private final SourcesConfig sourcesConfig;

    public GreenhouseSource(WebClient.Builder webClientBuilder, ScannerMetrics metrics, SourcesConfig sourcesConfig) {
        super(webClientBuilder, metrics);
        this.sourcesConfig = sourcesConfig;
    }

    @Override
    public String getName() {
        return "Greenhouse";
    }

    protected String getApiUrl(String company) {
        return String.format(API_URL, company);
    }

    @Override
    protected List<String> getCompanies() {
        return sourcesConfig.getGreenhouse();
    }

    @Override
    protected Mono<List<Job>> fetchCompanyJobs(String company) {
        String url = getApiUrl(company);

        return timedGet(url, GreenhouseResponse.class)
                .map(response -> {
                    if (response.getJobs() == null)
                        return List.<Job>of();
                    return response.getJobs().stream()
                            .map(job -> mapToJob(job, company))
                            .toList();
                })
                .onErrorResume(e -> Mono.just(List.of()));
    }

    private Job mapToJob(GreenhouseJob ghJob, String company) {
        String location = "";
        if (ghJob.getLocation() != null) {
            location = ghJob.getLocation().getOrDefault("name", "");
        }

        return baseJob()
                .title(ghJob.getTitle())
                .url(ghJob.getAbsoluteUrl())
                .company(formatCompanyName(company))
                .location(location)
                .description(stripHtml(ghJob.getContent()))
                .build();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GreenhouseResponse {
        private List<GreenhouseJob> jobs;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GreenhouseJob {
        private String title;
        private String absoluteUrl;
        private String content;
        private Map<String, String> location;
    }
}
