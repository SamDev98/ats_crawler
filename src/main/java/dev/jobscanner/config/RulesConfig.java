package dev.jobscanner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for eligibility rules.
 * Loaded from rules.yml under 'rules' prefix.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rules")
public class RulesConfig {

    private List<String> blockTerms = new ArrayList<>();
    private List<String> remoteIndicators = new ArrayList<>();
    private List<String> contractIndicators = new ArrayList<>();
    private List<String> javaTerms = new ArrayList<>();
}
