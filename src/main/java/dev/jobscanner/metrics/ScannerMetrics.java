package dev.jobscanner.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prometheus metrics for job scanner operations.
 */
@Component
public class ScannerMetrics {

    private static final String TAG_SOURCE = "source";
    private final MeterRegistry registry;

    // Counters
    private final Counter jobsFoundCounter;
    private final Counter jobsFilteredCounter;
    private final Counter jobsQualifiedCounter;
    private final Counter jobsSentCounter;
    private final Counter apiCallsCounter;
    private final Counter apiErrorsCounter;

    // Timers (per source)
    private final ConcurrentHashMap<String, Timer> sourceTimers = new ConcurrentHashMap<>();

    // Gauges
    private final AtomicInteger lastRunJobsFound = new AtomicInteger(0);
    private final AtomicInteger lastRunJobsQualified = new AtomicInteger(0);
    private final AtomicInteger lastRunJobsSent = new AtomicInteger(0);

    public ScannerMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Jobs counters
        this.jobsFoundCounter = Counter.builder("job_scanner_jobs_found_total")
                .description("Total jobs found from all sources")
                .register(registry);

        this.jobsFilteredCounter = Counter.builder("job_scanner_jobs_filtered_total")
                .description("Total jobs filtered out (ineligible or low score)")
                .register(registry);

        this.jobsQualifiedCounter = Counter.builder("job_scanner_jobs_qualified_total")
                .description("Total jobs that passed eligibility and score threshold")
                .register(registry);

        this.jobsSentCounter = Counter.builder("job_scanner_jobs_sent_total")
                .description("Total jobs sent via email")
                .register(registry);

        // API counters
        this.apiCallsCounter = Counter.builder("job_scanner_api_calls_total")
                .description("Total API calls to ATS sources")
                .register(registry);

        this.apiErrorsCounter = Counter.builder("job_scanner_api_errors_total")
                .description("Total API errors from ATS sources")
                .register(registry);

        // Gauges for last run statistics
        Gauge.builder("job_scanner_last_run_jobs_found", lastRunJobsFound, AtomicInteger::get)
                .description("Jobs found in last run")
                .register(registry);

        Gauge.builder("job_scanner_last_run_jobs_qualified", lastRunJobsQualified, AtomicInteger::get)
                .description("Jobs qualified in last run")
                .register(registry);

        Gauge.builder("job_scanner_last_run_jobs_sent", lastRunJobsSent, AtomicInteger::get)
                .description("Jobs sent in last run")
                .register(registry);
    }

    /**
     * Get or create a timer for a specific source.
     */
    public Timer getSourceTimer(String sourceName) {
        return sourceTimers.computeIfAbsent(sourceName, name ->
                Timer.builder("job_scanner_source_fetch_duration")
                        .description("Time to fetch jobs from source")
                        .tag(TAG_SOURCE, name)
                        .register(registry)
        );
    }

    /**
     * Record that jobs were found from a source.
     */
    public void recordJobsFound(int count) {
        jobsFoundCounter.increment(count);
    }

    /**
     * Record that jobs were filtered out.
     */
    public void recordJobsFiltered(int count) {
        jobsFilteredCounter.increment(count);
    }

    /**
     * Record that jobs qualified for email.
     */
    public void recordJobsQualified(int count) {
        jobsQualifiedCounter.increment(count);
    }

    /**
     * Record that jobs were sent via email.
     */
    public void recordJobsSent(int count) {
        jobsSentCounter.increment(count);
    }

    /**
     * Record an API call to a source.
     */
    public void recordApiCall(String source) {
        apiCallsCounter.increment();
        Counter.builder("job_scanner_api_calls_by_source_total")
                .tag(TAG_SOURCE, source)
                .register(registry)
                .increment();
    }

    /**
     * Record an API error from a source.
     */
    public void recordApiError(String source) {
        apiErrorsCounter.increment();
        Counter.builder("job_scanner_api_errors_by_source_total")
                .tag(TAG_SOURCE, source)
                .register(registry)
                .increment();
    }

    /**
     * Update last run statistics.
     */
    public void updateLastRunStats(int found, int qualified, int sent) {
        lastRunJobsFound.set(found);
        lastRunJobsQualified.set(qualified);
        lastRunJobsSent.set(sent);
    }

    /**
     * Increment fetch failures counter for a source.
     */
    public void incrementFetchFailures(String source) {
        apiErrorsCounter.increment();
        Counter.builder("job_scanner_fetch_failures_by_source_total")
                .tag(TAG_SOURCE, source)
                .register(registry)
                .increment();
    }

    /**
     * Increment jobs discovered counter for a source.
     */
    public void incrementJobsDiscovered(String source) {
        Counter.builder("job_scanner_jobs_discovered_by_source_total")
                .tag(TAG_SOURCE, source)
                .register(registry)
                .increment();
    }

    /**
     * Record fetch latency for a source.
     */
    public void recordFetchLatency(String source, long latencyMs) {
        getSourceTimer(source).record(java.time.Duration.ofMillis(latencyMs));
    }
}
