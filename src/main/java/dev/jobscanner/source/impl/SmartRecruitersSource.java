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
public class SmartRecruitersSource extends AbstractJobSource {

    private static final String API_URL_TEMPLATE = "https://api.smartrecruiters.com/v1/companies/%s/postings";

    private final SourcesConfig sourcesConfig;

    public SmartRecruitersSource(WebClient.Builder webClientBuilder, ScannerMetrics metrics,
            SourcesConfig sourcesConfig) {
        super(webClientBuilder, metrics);
        this.sourcesConfig = sourcesConfig;
    }

    @Override
    public String getName() {
        return "SmartRecruiters";
    }

    protected String getApiUrl(String company) {
        return String.format(API_URL_TEMPLATE, company);
    }

    @Override
    protected List<String> getCompanies() {
        return sourcesConfig.getSmartrecruiters();
    }

    @Override
    protected Mono<List<Job>> fetchCompanyJobs(String company) {
        String url = getApiUrl(company);

        return timedGet(url, SmartRecruitersResponse.class)
                .map(response -> {
                    if (response.getContent() == null) {
                        return List.<Job>of();
                    }
                    return response.getContent().stream()
                            .map(job -> mapToJob(job, company))
                            .toList();
                })
                .onErrorResume(e -> Mono.just(List.of()));
    }

    private Job mapToJob(SmartRecruitersJob srJob, String company) {
        String location = "";
        if (srJob.getLocation() != null) {
            if (srJob.getLocation().getCity() != null) {
                location = srJob.getLocation().getCity();
            } else if (srJob.getLocation().getCountry() != null) {
                location = srJob.getLocation().getCountry();
            }
        }

        String url = srJob.getRef() != null
                ? srJob.getRef()
                : "https://jobs.smartrecruiters.com/" + company + "/" + srJob.getId();

        return baseJob()
                .title(srJob.getName())
                .url(url)
                .company(srJob.getCompany() != null && srJob.getCompany().getName() != null
                        ? srJob.getCompany().getName()
                        : formatCompanyName(company))
                .location(location)
                .description(srJob.getJobAd() != null && srJob.getJobAd().getSections() != null
                        ? extractDescription(srJob.getJobAd().getSections())
                        : "")
                .build();
    }

    private String extractDescription(SmartRecruitersJobAdSections sections) {
        StringBuilder sb = new StringBuilder();
        if (sections.getJobDescription() != null && sections.getJobDescription().getText() != null) {
            sb.append(stripHtml(sections.getJobDescription().getText()));
        }
        if (sections.getQualifications() != null && sections.getQualifications().getText() != null) {
            sb.append(" ").append(stripHtml(sections.getQualifications().getText()));
        }
        return sb.toString().trim();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SmartRecruitersResponse {
        private List<SmartRecruitersJob> content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SmartRecruitersJob {
        private String id;
        private String name;
        private String ref;
        private SmartRecruitersCompany company;
        private SmartRecruitersLocation location;
        private SmartRecruitersJobAd jobAd;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SmartRecruitersCompany {
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SmartRecruitersLocation {
        private String city;
        private String country;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SmartRecruitersJobAd {
        private SmartRecruitersJobAdSections sections;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SmartRecruitersJobAdSections {
        private SmartRecruitersSection jobDescription;
        private SmartRecruitersSection qualifications;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SmartRecruitersSection {
        private String text;
    }
}
