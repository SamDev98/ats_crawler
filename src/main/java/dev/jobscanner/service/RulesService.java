package dev.jobscanner.service;

import dev.jobscanner.config.RulesConfig;
import dev.jobscanner.model.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Service for checking job eligibility based on configurable rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RulesService {

    private final RulesConfig rulesConfig;

    /**
     * Result of eligibility check.
     */
    public record EligibilityResult(
            boolean eligible,
            String blockReason,
            boolean isRemote,
            boolean isContract,
            boolean isJavaRelated
    ) {
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
        String title = job.getTitle().toLowerCase();
        String description = job.getDescription().toLowerCase();
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
        // First, check if title contains Java terms (highest priority)
        for (String javaTerm : rulesConfig.getJavaTerms()) {
            if (containsWord(title, javaTerm.toLowerCase())) {
                return true;
            }
        }

        // Then check description (but with stricter matching for "java")
        if (containsWord(description, "java")) {
            return true;
        }

        // Check for specific frameworks in description
        String[] frameworkTerms = {"spring boot", "springboot", "spring framework", "quarkus", "micronaut"};
        for (String term : frameworkTerms) {
            if (containsPhrase(description, term)) {
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
        return text.contains(phrase);
    }

    /**
     * Check if text contains a word with word boundaries.
     */
    private boolean containsWord(String text, String word) {
        // Use word boundaries to avoid matching "javascript" when looking for "java"
        String pattern = "\\b" + Pattern.quote(word) + "\\b";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
}
