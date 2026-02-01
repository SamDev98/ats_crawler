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
public class RecruiteeSource extends AbstractJobSource {

    private static final String API_URL_TEMPLATE = "https://%s.recruitee.com/api/offers";

    private final SourcesConfig sourcesConfig;

    public RecruiteeSource(WebClient.Builder webClientBuilder, ScannerMetrics metrics, SourcesConfig sourcesConfig) {
        super(webClientBuilder, metrics);
        this.sourcesConfig = sourcesConfig;
    }

    @Override
    public String getName() {
        return "Recruitee";
    }

    @Override
    protected List<String> getCompanies() {
        return sourcesConfig.getRecruitee();
    }

    @Override
    protected Mono<List<Job>> fetchCompanyJobs(String company) {
        String url = String.format(API_URL_TEMPLATE, company);

        return timedGet(url, RecruiteeResponse.class)
                .map(response -> {
                    if (response.getOffers() == null) {
                        return List.<Job>of();
                    }
                    return response.getOffers().stream()
                            .map(job -> mapToJob(job, company))
                            .toList();
                })
                .onErrorResume(e -> Mono.just(List.of()));
    }

    private Job mapToJob(RecruiteeJob recruiteeJob, String company) {
        return baseJob()
                .title(recruiteeJob.getTitle())
                .url(recruiteeJob.getCareers_url() != null
                        ? recruiteeJob.getCareers_url()
                        : "https://" + company + ".recruitee.com/o/" + recruiteeJob.getSlug())
                .company(recruiteeJob.getCompany_name() != null
                        ? recruiteeJob.getCompany_name()
                        : formatCompanyName(company))
                .location(recruiteeJob.getLocation() != null ? recruiteeJob.getLocation() : "")
                .description(recruiteeJob.getDescription() != null
                        ? stripHtml(recruiteeJob.getDescription())
                        : "")
                .build();
    }

    private String formatCompanyName(String company) {
        return company.replace("-", " ")
                .substring(0, 1).toUpperCase() + company.replace("-", " ").substring(1);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RecruiteeResponse {
        private List<RecruiteeJob> offers;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RecruiteeJob {
        private Long id;
        private String slug;
        private String title;
        private String description;
        private String location;
        private String company_name;
        private String careers_url;
    }
}
