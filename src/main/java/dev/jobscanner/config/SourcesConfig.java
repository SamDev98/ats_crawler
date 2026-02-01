package dev.jobscanner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for ATS sources and their company lists.
 * Loaded from application.yml under 'sources' prefix.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sources")
public class SourcesConfig {

    private List<String> lever = new ArrayList<>();
    private List<String> greenhouse = new ArrayList<>();
    private List<String> ashby = new ArrayList<>();
    private List<String> workable = new ArrayList<>();
    private List<String> smartrecruiters = new ArrayList<>();
    private List<String> recruitee = new ArrayList<>();
    private List<String> teamtailor = new ArrayList<>();

    /**
     * Get total number of configured companies across all sources.
     */
    public int getTotalCompanies() {
        return lever.size() + greenhouse.size() + ashby.size() +
               workable.size() + smartrecruiters.size() +
               recruitee.size() + teamtailor.size();
    }
}
