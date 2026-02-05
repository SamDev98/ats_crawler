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
public class WorkableSource extends AbstractJobSource {

    private static final String API_URL_TEMPLATE = "https://apply.workable.com/api/v3/accounts/%s/jobs";

    private final SourcesConfig sourcesConfig;

    public WorkableSource(WebClient.Builder webClientBuilder, ScannerMetrics metrics, SourcesConfig sourcesConfig) {
        super(webClientBuilder, metrics);
        this.sourcesConfig = sourcesConfig;
    }

    @Override
    public String getName() {
        return "Workable";
    }

    protected String getApiUrl(String company) {
        return String.format(API_URL_TEMPLATE, company);
    }

    @Override
    protected List<String> getCompanies() {
        return sourcesConfig.getWorkable();
    }

    @Override
    protected Mono<List<Job>> fetchCompanyJobs(String company) {
        String url = getApiUrl(company);

        return timedGet(url, WorkableResponse.class)
                .map(response -> {
                    if (response.getResults() == null) {
                        return List.<Job>of();
                    }
                    return response.getResults().stream()
                            .map(job -> mapToJob(job, company))
                            .toList();
                });
    }

    private Job mapToJob(WorkableJob workableJob, String company) {
        String location = workableJob.getLocation() != null
                ? workableJob.getLocation().getLocationStr()
                : "";

        return baseJob()
                .title(workableJob.getTitle())
                .url("https://apply.workable.com/" + company + "/j/" + workableJob.getShortcode() + "/")
                .company(formatCompanyName(company))
                .location(location != null ? location : "")
                .description(workableJob.getDescription() != null ? workableJob.getDescription() : "")
                .build();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class WorkableResponse {
        private List<WorkableJob> results;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class WorkableJob {
        private String shortcode;
        private String title;
        private String description;
        private WorkableLocation location;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class WorkableLocation {
        @com.fasterxml.jackson.annotation.JsonProperty("location_str")
        private String locationStr;
        private String city;
        private String country;
    }
}
