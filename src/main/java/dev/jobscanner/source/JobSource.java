package dev.jobscanner.source;

import dev.jobscanner.model.Job;
import reactor.core.publisher.Flux;

/**
 * Interface for ATS job sources.
 * Each ATS platform implements this interface.
 */
public interface JobSource {

    /**
     * Get the name of this source (e.g., "Lever", "Greenhouse")
     */
    String getName();

    /**
     * Fetch all jobs from this source.
     * Returns a reactive stream for efficient parallel processing.
     */
    Flux<Job> fetchJobs();

    /**
     * Check if this source is enabled.
     */
    default boolean isEnabled() {
        return true;
    }
}
