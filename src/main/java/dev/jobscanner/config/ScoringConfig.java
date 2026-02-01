package dev.jobscanner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for scoring weights.
 * Loaded from weights.yml under 'scoring' prefix.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "scoring")
public class ScoringConfig {

    private int threshold = 70;
    private int javaInTitle = 20;
    private int seniorLevel = 10;
    private int remoteExplicit = 15;
    private int noUsOnly = 20;
    private int contractB2b = 15;
    private int latamBrazilBoost = 10;
    private int techStack = 20;
    private Map<String, Integer> techStackTerms = new HashMap<>();
    private List<String> seniorityTerms = new ArrayList<>();
}
