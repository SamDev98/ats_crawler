package dev.jobscanner.ai;

import dev.jobscanner.model.Job;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Interface for job enhancement services.
 * Can be implemented by AI-powered or no-op implementations.
 */
public interface JobEnhancer {

    /**
     * Enhance a single job with additional analysis.
     *
     * @param job The job to enhance
     * @return Mono with the enhanced job
     */
    Mono<Job> enhance(Job job);

    /**
     * Enhance multiple jobs.
     *
     * @param jobs List of jobs to enhance
     * @return Mono with list of enhanced jobs
     */
    Mono<List<Job>> enhanceAll(List<Job> jobs);

    /**
     * Check if AI enhancement is available.
     *
     * @return true if AI enhancement is enabled and configured
     */
    boolean isEnabled();
}
