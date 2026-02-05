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
public class AshbySource extends AbstractJobSource {

    private static final String API_URL = "https://jobs.ashbyhq.com/api/non-user-graphql?op=ApiJobBoardWithTeams";
    private static final String GRAPHQL_QUERY = """
            query ApiJobBoardWithTeams($organizationHostedJobsPageName: String!) {
                jobBoard: jobBoardWithTeams(organizationHostedJobsPageName: $organizationHostedJobsPageName) {
                    jobPostings { id title locationName descriptionPlain }
                }
            }
            """;

    private final SourcesConfig sourcesConfig;

    public AshbySource(WebClient.Builder webClientBuilder, ScannerMetrics metrics, SourcesConfig sourcesConfig) {
        super(webClientBuilder, metrics);
        this.sourcesConfig = sourcesConfig;
    }

    @Override
    public String getName() {
        return "Ashby";
    }

    protected String getApiUrl() {
        return API_URL;
    }

    @Override
    protected List<String> getCompanies() {
        return sourcesConfig.getAshby();
    }

    @Override
    protected Mono<List<Job>> fetchCompanyJobs(String company) {
        Map<String, Object> payload = Map.of(
                "operationName", "ApiJobBoardWithTeams",
                "variables", Map.of("organizationHostedJobsPageName", company),
                "query", GRAPHQL_QUERY);

        return timedPost(getApiUrl(), payload, AshbyResponse.class)
                .map(response -> {
                    if (response.getData() == null || response.getData().getJobBoard() == null) {
                        return List.<Job>of();
                    }
                    List<AshbyJob> jobs = response.getData().getJobBoard().getJobPostings();
                    if (jobs == null)
                        return List.<Job>of();
                    return jobs.stream()
                            .map(job -> mapToJob(job, company))
                            .toList();
                });
    }

    private Job mapToJob(AshbyJob ashbyJob, String company) {
        return baseJob()
                .title(ashbyJob.getTitle())
                .url("https://jobs.ashbyhq.com/" + company + "/" + ashbyJob.getId())
                .company(formatCompanyName(company))
                .location(ashbyJob.getLocationName() != null ? ashbyJob.getLocationName() : "")
                .description(ashbyJob.getDescriptionPlain() != null ? ashbyJob.getDescriptionPlain() : "")
                .build();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AshbyResponse {
        private AshbyData data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AshbyData {
        private AshbyJobBoard jobBoard;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AshbyJobBoard {
        private List<AshbyJob> jobPostings;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AshbyJob {
        private String id;
        private String title;
        private String locationName;
        private String descriptionPlain;
    }
}
