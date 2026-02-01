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
public class LeverSource extends AbstractJobSource {

    private static final String API_URL = "https://api.lever.co/v0/postings/%s?mode=json";

    private final SourcesConfig sourcesConfig;

    public LeverSource(WebClient.Builder webClientBuilder, ScannerMetrics metrics, SourcesConfig sourcesConfig) {
        super(webClientBuilder, metrics);
        this.sourcesConfig = sourcesConfig;
    }

    @Override
    public String getName() {
        return "Lever";
    }

    @Override
    protected List<String> getCompanies() {
        return sourcesConfig.getLever();
    }

    @Override
    protected Mono<List<Job>> fetchCompanyJobs(String company) {
        String url = String.format(API_URL, company);

        return timedGet(url, LeverJob[].class)
                .map(jobs -> java.util.Arrays.stream(jobs)
                        .map(job -> mapToJob(job, company))
                        .toList())
                .onErrorResume(e -> Mono.just(List.of()));
    }

    private Job mapToJob(LeverJob leverJob, String company) {
        Map<String, String> categories = leverJob.getCategories();
        String location = categories != null ? categories.getOrDefault("location", "") : "";

        return baseJob()
                .title(leverJob.getText())
                .url(leverJob.getHostedUrl())
                .company(formatCompanyName(company))
                .location(location)
                .description(leverJob.getDescriptionPlain() != null ? leverJob.getDescriptionPlain() : "")
                .build();
    }

    private String formatCompanyName(String company) {
        return company.replace("-", " ")
                .substring(0, 1).toUpperCase() + company.replace("-", " ").substring(1);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LeverJob {
        private String text;
        private String hostedUrl;
        private String descriptionPlain;
        private Map<String, String> categories;
    }
}
