package dev.jobscanner.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class Job {
    private String title;
    private String url;
    private String company;
    private String location;
    private String description;
    private String source; // ATS name (Lever, Greenhouse, etc.)

    // Eligibility flags (set by pipeline after RulesService check)
    private boolean remote;
    private boolean contract;

    // Scoring
    private int score;
    private Map<String, Integer> scoreBreakdown;

    // AI enhancement (optional, populated by JobEnhancer)
    private String aiAnalysis;

    // Metadata
    private Instant discoveredAt;
}
