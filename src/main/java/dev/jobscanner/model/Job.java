package dev.jobscanner.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class Job {
    private String id;
    private String title;
    private String url;
    private String company;
    private String location;
    private String description;
    private String source; // ATS name (Lever, Greenhouse, etc.)

    // Eligibility flags
    private boolean eligible;
    private boolean remote;
    private boolean contract;
    private String blockReason;

    // Scoring
    private int score;
    private Map<String, Integer> scoreBreakdown;

    // AI enhancements (optional, populated by JobEnhancer)
    private String aiAnalysis;        // Full AI analysis text
    private String summary;
    private String normalizedLocation;
    private String aiTechnologies;
    private String aiLevel;
    private String aiRemoteStatus;
    private String aiContractType;
    private String aiRedFlags;
    private int aiMatchScore;

    // Metadata
    private Instant discoveredAt;

    public String getUrlHash() {
        return String.valueOf(url.hashCode());
    }
}
