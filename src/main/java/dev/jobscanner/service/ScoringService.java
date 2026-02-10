package dev.jobscanner.service;

import dev.jobscanner.config.ScoringConfig;
import dev.jobscanner.config.UserProfile;
import dev.jobscanner.model.Job;
import dev.jobscanner.service.RulesService.EligibilityResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Service for calculating job applicability scores.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringService {

    private final ScoringConfig scoringConfig;
    private final UserProfile userProfile;

    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private static final List<String> DEFAULT_LATAM_TERMS = List.of(
            "brazil", "brasil", "latam", "latin america", "south america",
            "são paulo", "sao paulo", "rio de janeiro", "curitiba", "belo horizonte",
            "florianopolis", "remoto brasil", "remote brazil");

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
        if (job == null) {
            return new ScoringResult(0, false, Map.of());
        }

        Map<String, Integer> breakdown = new HashMap<>();
        int totalScore = 0;

        String title = (job.getTitle() != null) ? job.getTitle().toLowerCase() : "";
        String description = (job.getDescription() != null) ? job.getDescription().toLowerCase() : "";
        String combined = title + " " + description;

        // Use weights from profile.json if available, otherwise fallback to
        // ScoringConfig
        Map<String, Integer> weights = (userProfile.getScoring() != null
                && userProfile.getScoring().getWeights() != null)
                        ? userProfile.getScoring().getWeights()
                        : Map.of();

        // 1. Java in title
        if (containsWord(title, "java")) {
            int points = weights.getOrDefault("java_in_title", scoringConfig.getJavaInTitle());
            breakdown.put("java_in_title", points);
            totalScore += points;
        }

        // 2. Senior/Lead/Staff level
        if (hasSeniorityIndicator(title)) {
            int points = weights.getOrDefault("senior_level", scoringConfig.getSeniorLevel());
            breakdown.put("senior_level", points);
            totalScore += points;
        }

        // 3. Remote explicit
        if (eligibility.isRemote()) {
            int points = weights.getOrDefault("remote_explicit", scoringConfig.getRemoteExplicit());
            breakdown.put("remote_explicit", points);
            totalScore += points;
        }

        // 4. No US-only restrictions
        if (eligibility.eligible()) {
            int points = weights.getOrDefault("no_us_only", scoringConfig.getNoUsOnly());
            breakdown.put("no_us_only", points);
            totalScore += points;
        }

        // 5. Contract/B2B
        if (eligibility.isContract()) {
            int points = weights.getOrDefault("contract_b2b", scoringConfig.getContractB2b());
            breakdown.put("contract_b2b", points);
            totalScore += points;
        }

        // 6. LATAM/Brazil Boost
        if (isLatamOrBrazil(job)) {
            int points = weights.getOrDefault("latam_brazil_boost", scoringConfig.getLatamBrazilBoost());
            breakdown.put("latam_brazil_boost", points);
            totalScore += points;
        }

        // 7. Tech stack compatibility
        int techStackScore = calculateTechStackScore(combined);
        if (techStackScore > 0) {
            int maxTechStack = weights.getOrDefault("tech_stack_max", scoringConfig.getTechStack());
            techStackScore = Math.min(techStackScore, maxTechStack);
            breakdown.put("tech_stack", techStackScore);
            totalScore += techStackScore;
        }

        // Cap total score at 100
        totalScore = Math.min(totalScore, 100);

        int threshold = getThreshold();
        boolean shouldApply = totalScore >= threshold;

        log.debug("Job '{}' scored {} (threshold: {}, should apply: {})",
                job.getTitle(), totalScore, threshold, shouldApply);

        return new ScoringResult(totalScore, shouldApply, breakdown);
    }

    /**
     * Check if the title contains seniority indicators.
     */
    private boolean hasSeniorityIndicator(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }

        // Use seniority terms from profile.json if available
        List<String> terms = (userProfile.getSeniorityTerms() != null && !userProfile.getSeniorityTerms().isEmpty())
                ? userProfile.getSeniorityTerms()
                : scoringConfig.getSeniorityTerms();

        return terms.stream()
                .anyMatch(term -> containsWord(title, term.toLowerCase()));
    }

    /**
     * Check if the job is specifically targeting LATAM or Brazil.
     */
    private boolean isLatamOrBrazil(Job job) {
        String location = (job.getLocation() != null) ? job.getLocation().toLowerCase() : "";
        String description = (job.getDescription() != null) ? job.getDescription().toLowerCase() : "";

        for (String term : resolveLocationTerms()) {
            if (location.contains(term) || description.contains(term)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolve location terms: user profile (pre-lowercased) or defaults.
     */
    private List<String> resolveLocationTerms() {
        List<String> userLocations = userProfile.getLocations();
        if (userLocations != null && !userLocations.isEmpty()) {
            return userLocations.stream()
                    .map(String::toLowerCase)
                    .toList();
        }
        return DEFAULT_LATAM_TERMS;
    }

    /**
     * * Calculate tech stack score based on matching terms.
     */
    private int calculateTechStackScore(String text) {
        int score = 0;
        // Use tech stack weights from profile.json if available
        Map<String, Integer> techWeights = (userProfile.getTechStackWeights() != null
                && !userProfile.getTechStackWeights().isEmpty())
                        ? userProfile.getTechStackWeights()
                        : scoringConfig.getTechStackTerms();

        for (Map.Entry<String, Integer> entry : techWeights.entrySet()) {
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
        if (text == null || word == null || word.isBlank()) {
            return false;
        }
        String regex = "\\b" + Pattern.quote(word.toLowerCase()) + "\\b";
        Pattern pattern = PATTERN_CACHE.computeIfAbsent(regex, k -> Pattern.compile(k, Pattern.CASE_INSENSITIVE));
        return pattern.matcher(text).find();
    }

    /**
     * Get the configured score threshold.
     */
    public int getThreshold() {
        if (userProfile.getScoring() != null && userProfile.getScoring().getThreshold() > 0) {
            return userProfile.getScoring().getThreshold();
        }
        return scoringConfig.getThreshold();
    }
}
