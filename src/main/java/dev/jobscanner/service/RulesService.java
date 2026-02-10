package dev.jobscanner.service;

import dev.jobscanner.config.RulesConfig;
import dev.jobscanner.config.UserProfile;
import dev.jobscanner.model.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service for checking job eligibility based on configurable rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RulesService {

    private final RulesConfig rulesConfig;
    private final UserProfile userProfile;

    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * Result of eligibility check.
     */
    public record EligibilityResult(
            boolean eligible,
            String blockReason,
            boolean isRemote,
            boolean isContract,
            boolean isJavaRelated) {
        public static EligibilityResult blocked(String reason) {
            return new EligibilityResult(false, reason, false, false, false);
        }

        public static EligibilityResult eligible(boolean isRemote, boolean isContract, boolean isJavaRelated) {
            return new EligibilityResult(true, null, isRemote, isContract, isJavaRelated);
        }
    }

    /**
     * Check if a job passes eligibility rules.
     *
     * @param job The job to check
     * @return EligibilityResult with status and details
     */
    public EligibilityResult checkEligibility(Job job) {
        if (job == null) {
            return EligibilityResult.blocked("Null job object");
        }

        String title = (job.getTitle() != null) ? job.getTitle().toLowerCase() : "";
        String description = (job.getDescription() != null) ? job.getDescription().toLowerCase() : "";
        String combined = title + " " + description;

        // Check for blocking terms
        for (String blockTerm : rulesConfig.getBlockTerms()) {
            if (containsPhrase(combined, blockTerm.toLowerCase())) {
                log.debug("Job '{}' blocked by term: {}", job.getTitle(), blockTerm);
                return EligibilityResult.blocked(blockTerm);
            }
        }

        // Check if Java-related
        boolean isJavaRelated = isJavaRelated(title, description);
        if (!isJavaRelated) {
            log.debug("Job '{}' not Java-related", job.getTitle());
            return EligibilityResult.blocked("Not Java-related");
        }

        // Check for remote indicators
        boolean isRemote = hasRemoteIndicator(combined);

        // Check for contract indicators
        boolean isContract = hasContractIndicator(combined);

        return EligibilityResult.eligible(isRemote, isContract, isJavaRelated);
    }

    /**
     * Check if the job is Java-related based on title or description.
     */
    private boolean isJavaRelated(String title, String description) {
        String titleLower = title.toLowerCase();
        String descLower = description.toLowerCase();

        // 1. Check target technologies from profile.json
        if (matchesTargetTech(titleLower, descLower)) {
            return true;
        }

        // 2. Check Java terms from rulesConfig in title
        for (String javaTerm : rulesConfig.getJavaTerms()) {
            if (containsWord(titleLower, javaTerm.toLowerCase())) {
                return true;
            }
        }

        // 3. Last fallback: check "java" word in description
        return containsWord(descLower, "java");
    }

    private boolean matchesTargetTech(String title, String description) {
        List<String> targetTech = userProfile.getTargetTechnologies();
        if (targetTech == null || targetTech.isEmpty()) {
            return false;
        }

        for (String tech : targetTech) {
            String techLower = tech.toLowerCase();
            if (containsPhrase(title, techLower) || containsPhrase(description, techLower)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the text contains any remote indicator.
     */
    private boolean hasRemoteIndicator(String text) {
        for (String indicator : rulesConfig.getRemoteIndicators()) {
            if (containsPhrase(text, indicator.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the text contains any contract indicator.
     */
    private boolean hasContractIndicator(String text) {
        for (String indicator : rulesConfig.getContractIndicators()) {
            if (containsPhrase(text, indicator.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if text contains a phrase (case-insensitive).
     */
    private boolean containsPhrase(String text, String phrase) {
        if (text == null || phrase == null)
            return false;
        return text.toLowerCase().contains(phrase.toLowerCase());
    }

    /**
     * Check if text contains a word with word boundaries.
     */
    private boolean containsWord(String text, String word) {
        if (text == null || word == null || word.isBlank()) {
            return false;
        }
        // Use word boundaries to avoid matching "javascript" when looking for "java"
        String regex = "\\b" + Pattern.quote(word.toLowerCase()) + "\\b";
        Pattern pattern = PATTERN_CACHE.computeIfAbsent(regex, k -> Pattern.compile(k, Pattern.CASE_INSENSITIVE));
        return pattern.matcher(text.toLowerCase()).find();
    }
}
