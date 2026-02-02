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

import java.util.List;

@Slf4j
@Component
public class TeamtailorSource extends AbstractJobSource {

    private static final String API_URL_TEMPLATE = "https://%s.teamtailor.com/api/v1/jobs";

    private final SourcesConfig sourcesConfig;

    public TeamtailorSource(WebClient.Builder webClientBuilder, ScannerMetrics metrics, SourcesConfig sourcesConfig) {
        super(webClientBuilder, metrics);
        this.sourcesConfig = sourcesConfig;
    }

    @Override
    public String getName() {
        return "Teamtailor";
    }

    protected String getApiUrl(String company) {
        return String.format(API_URL_TEMPLATE, company);
    }

    @Override
    protected List<String> getCompanies() {
        return sourcesConfig.getTeamtailor();
    }

    @Override
    protected Mono<List<Job>> fetchCompanyJobs(String company) {
        String url = getApiUrl(company);

        return timedGet(url, TeamtailorResponse.class)
                .map(response -> {
                    if (response.getData() == null) {
                        return List.<Job>of();
                    }
                    return response.getData().stream()
                            .map(job -> mapToJob(job, company, response.getIncluded()))
                            .toList();
                })
                .onErrorResume(e -> Mono.just(List.of()));
    }

    private Job mapToJob(TeamtailorJob ttJob, String company, List<TeamtailorIncluded> included) {
        TeamtailorAttributes attrs = ttJob.getAttributes();
        if (attrs == null) {
            attrs = new TeamtailorAttributes();
        }

        String location = findLocation(ttJob, included);

        return baseJob()
                .title(attrs.getTitle())
                .url(attrs.getCareersiteJobUrl() != null
                        ? attrs.getCareersiteJobUrl()
                        : "https://" + company + ".teamtailor.com/jobs/" + ttJob.getId())
                .company(formatCompanyName(company))
                .location(location)
                .description(attrs.getBody() != null ? stripHtml(attrs.getBody()) : "")
                .build();
    }

    private String findLocation(TeamtailorJob ttJob, List<TeamtailorIncluded> included) {
        if (included == null || ttJob.getRelationships() == null
                || ttJob.getRelationships().getLocations() == null
                || ttJob.getRelationships().getLocations().getData() == null) {
            return "";
        }

        for (TeamtailorRelationshipData locRef : ttJob.getRelationships().getLocations().getData()) {
            for (TeamtailorIncluded inc : included) {
                if ("locations".equals(inc.getType()) && locRef.getId().equals(inc.getId())
                        && inc.getAttributes() != null && inc.getAttributes().getName() != null) {
                    return inc.getAttributes().getName();
                }
            }
        }
        return "";
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TeamtailorResponse {
        private List<TeamtailorJob> data;
        private List<TeamtailorIncluded> included;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TeamtailorJob {
        private String id;
        private String type;
        private TeamtailorAttributes attributes;
        private TeamtailorRelationships relationships;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TeamtailorAttributes {
        private String title;
        private String body;
        @JsonProperty("careersite-job-url")
        private String careersiteJobUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TeamtailorRelationships {
        private TeamtailorRelationshipItem locations;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TeamtailorRelationshipItem {
        private List<TeamtailorRelationshipData> data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TeamtailorRelationshipData {
        private String id;
        private String type;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TeamtailorIncluded {
        private String id;
        private String type;
        private TeamtailorIncludedAttributes attributes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TeamtailorIncludedAttributes {
        private String name;
    }
}
