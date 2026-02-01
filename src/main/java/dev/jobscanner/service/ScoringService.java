package dev.jobscanner.service;

import dev.jobscanner.config.ScoringConfig;
import dev.jobscanner.model.Job;
import dev.jobscanner.service.RulesService.EligibilityResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service for calculating job applicability scores.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringService {

    private final ScoringConfig scoringConfig;

    /**
     * Result of scoring calculation.
     */
    public record ScoringResult(
            int score,
            boolean shouldApply,
            Map<String, Integer> breakdown) {
    }

    /**
     * Calculate the applicability score for a job.
     *
     * @param job         The job to score
     * @param eligibility The eligibility result from RulesService
     * @return ScoringResult with score, threshold check, and breakdown
     */
    public ScoringResult calculateScore(Job job, EligibilityResult eligibility) {
        Map<String, Integer> breakdown = new HashMap<>();
        int totalScore = 0;

        String title = job.getTitle().toLowerCase();
        String description = job.getDescription().toLowerCase();
        String combined = title + " " + description;

        // 1. Java in title (+20 default)
        if (containsWord(title, "java")) {
            int points = scoringConfig.getJavaInTitle();
            breakdown.put("java_in_title", points);
            totalScore += points;
        }

        // 2. Senior/Lead/Staff level (+10 default)
        if (hasSeniorityIndicator(title)) {
            int points = scoringConfig.getSeniorLevel();
            breakdown.put("senior_level", points);
            totalScore += points;
        }

        // 3. Remote explicit (+15 default)
        if (eligibility.isRemote()) {
            int points = scoringConfig.getRemoteExplicit();
            breakdown.put("remote_explicit", points);
            totalScore += points;
        }

        // 4. No US-only restrictions (+20 default)
        // This is always true if we passed eligibility
        if (eligibility.eligible()) {
            int points = scoringConfig.getNoUsOnly();
            breakdown.put("no_us_only", points);
            totalScore += points;
        }

        // 5. Contract/B2B (+15 default)
        if (eligibility.isContract()) {
            int points = scoringConfig.getContractB2b();
            breakdown.put("contract_b2b", points);
            totalScore += points;
        }

        // 6. LATAM/Brazil Boost (+10 default)
        if (isLatamOrBrazil(job)) {
            int points = scoringConfig.getLatamBrazilBoost();
            breakdown.put("latam_brazil_boost", points);
            totalScore += points;
        }

        // 7. Tech stack compatibility (+20 max)
        int techStackScore = calculateTechStackScore(combined);
        if (techStackScore > 0) {
            // Cap at configured maximum
            techStackScore = Math.min(techStackScore, scoringConfig.getTechStack());
            breakdown.put("tech_stack", techStackScore);
            totalScore += techStackScore;
        }

        // Cap total score at 100
        totalScore = Math.min(totalScore, 100);

        boolean shouldApply = totalScore >= scoringConfig.getThreshold();

        log.debug("Job '{}' scored {} (threshold: {}, should apply: {})",
                job.getTitle(), totalScore, scoringConfig.getThreshold(), shouldApply);

        return new ScoringResult(totalScore, shouldApply, breakdown);
    }

    /**
     * Check if the title contains seniority indicators.
     */
    private boolean hasSeniorityIndicator(String title) {
        for (String term : scoringConfig.getSeniorityTerms()) {
            if (containsWord(title, term.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * * Check if the job is specifically targeting LATAM or Brazil.
     */
    private boolean isLatamOrBrazil(Job job) {
        String location = job.getLocation().toLowerCase();
        String description = job.getDescription().toLowerCase();

        String[] latamTerms = {
                "brazil", "brasil", "latam", "latin america", "south america",
                "são paulo", "sao paulo", "rio de janeiro", "curitiba", "belo horizonte",
                "florianopolis", "remoto brasil", "remote brazil"
        };

        for (String term : latamTerms) {
            if (location.contains(term) || description.contains(term)) {
                return true;
            }
        }
        return false;
    }

    /**
     * * Calculate tech stack score based on matching terms.
     */
    private int calculateTechStackScore(String text) {
        int score = 0;
        for (Map.Entry<String, Integer> entry : scoringConfig.getTechStackTerms().entrySet()) {
            if (containsWord(text, entry.getKey().toLowerCase())) {
                score += entry.getValue();
            }
        }
        return score;
    }

    /**
     * Check if text contains a word with word boundaries.
     */
    private boolean containsWord(String text, String word) {
        String pattern = "\\b" + Pattern.quote(word) + "\\b";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find();
    }

    /**
     * Get the configured score threshold.
     */
    public int getThreshold() {
        return scoringConfig.getThreshold();
    }
}
