package dev.jobscanner.service;

import dev.jobscanner.metrics.ScannerMetrics;
import dev.jobscanner.model.Job;
import dev.jobscanner.service.RulesService.EligibilityResult;
import dev.jobscanner.service.ScoringService.ScoringResult;
import dev.jobscanner.source.JobSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

/**
 * Main orchestration service for job scanning pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobScannerService {

    private final List<JobSource> jobSources;
    private final RulesService rulesService;
    private final ScoringService scoringService;
    private final DeduplicationService deduplicationService;
    private final EmailService emailService;
    private final ScannerMetrics metrics;

    @Value("${scanner.dry-run:false}")
    private boolean dryRun;

    /**
     * Execute the full job scanning pipeline.
     *
     * @return List of qualified jobs that were processed
     */
    public Mono<List<Job>> runPipeline() {
        log.info("========================================");
        log.info("Job Scanner Pipeline Starting");
        log.info("========================================");
        log.info("Sources configured: {}", jobSources.size());
        log.info("Dry run mode: {}", dryRun);

        return fetchAllJobs()
                .collectList()
                .flatMap(allJobs -> {
                    log.info("Total jobs fetched: {}", allJobs.size());
                    metrics.recordJobsFound(allJobs.size());

                    // Deduplicate
                    List<Job> newJobs = deduplicationService.filterNewJobs(allJobs);
                    log.info("New jobs after deduplication: {}", newJobs.size());

                    if (newJobs.isEmpty()) {
                        log.info("No new jobs to process");
                        metrics.updateLastRunStats(allJobs.size(), 0, 0);
                        return Mono.just(List.<Job>of());
                    }

                    // Process each job through rules and scoring
                    List<Job> qualifiedJobs = newJobs.stream()
                            .map(this::processJob)
                            .filter(job -> job != null)
                            .sorted(Comparator.comparingInt(Job::getScore).reversed())
                            .toList();

                    int filteredCount = newJobs.size() - qualifiedJobs.size();
                    metrics.recordJobsFiltered(filteredCount);
                    metrics.recordJobsQualified(qualifiedJobs.size());

                    log.info("Qualified jobs (score >= {}): {}",
                            scoringService.getThreshold(), qualifiedJobs.size());

                    if (qualifiedJobs.isEmpty()) {
                        log.info("No qualifying jobs found today");
                        metrics.updateLastRunStats(allJobs.size(), 0, 0);
                        return Mono.just(List.<Job>of());
                    }

                    // Send email and mark as sent
                    return sendAndMarkJobs(qualifiedJobs, allJobs.size());
                });
    }

    /**
     * Fetch jobs from all configured sources.
     */
    private Flux<Job> fetchAllJobs() {
        return Flux.fromIterable(jobSources)
                .flatMap(source -> {
                    log.info("Fetching from source: {}", source.getName());
                    return source.fetchJobs()
                            .doOnNext(job -> log.debug("Found job: {}", job.getTitle()));
                });
    }

    /**
     * Process a single job through eligibility and scoring.
     *
     * @return The job with score/eligibility set, or null if not eligible
     */
    private Job processJob(Job job) {
        // Check eligibility
        EligibilityResult eligibility = rulesService.checkEligibility(job);

        if (!eligibility.eligible()) {
            log.debug("Job '{}' not eligible: {}", job.getTitle(), eligibility.blockReason());
            return null;
        }

        // Update eligibility flags on job
        job.setRemote(eligibility.isRemote());
        job.setContract(eligibility.isContract());

        // Calculate score
        ScoringResult scoring = scoringService.calculateScore(job, eligibility);

        if (!scoring.shouldApply()) {
            log.debug("Job '{}' score too low: {}", job.getTitle(), scoring.score());
            return null;
        }

        // Update score on job
        job.setScore(scoring.score());
        job.setScoreBreakdown(scoring.breakdown());

        log.debug("Job qualified: '{}' (score: {})", job.getTitle(), scoring.score());
        return job;
    }

    /**
     * Send email with qualified jobs and mark them as sent.
     */
    private Mono<List<Job>> sendAndMarkJobs(List<Job> qualifiedJobs, int totalFound) {
        if (dryRun) {
            log.info("DRY RUN - Would send email with {} jobs:", qualifiedJobs.size());
            qualifiedJobs.forEach(job -> log.info("  - [{}] {} @ {} (score: {})",
                    job.getSource(), job.getTitle(), job.getCompany(), job.getScore()));
            metrics.updateLastRunStats(totalFound, qualifiedJobs.size(), 0);
            return Mono.just(qualifiedJobs);
        }

        return emailService.sendJobDigest(qualifiedJobs)
                .flatMap(success -> {
                    if (success) {
                        deduplicationService.markAsSent(qualifiedJobs);
                        metrics.recordJobsSent(qualifiedJobs.size());
                        metrics.updateLastRunStats(totalFound, qualifiedJobs.size(), qualifiedJobs.size());
                        log.info("Email sent successfully with {} jobs", qualifiedJobs.size());
                        return Mono.just(qualifiedJobs);
                    } else {
                        log.error("Failed to send email - jobs NOT marked as sent");
                        metrics.updateLastRunStats(totalFound, qualifiedJobs.size(), 0);
                        return Mono.error(new RuntimeException("Email sending failed"));
                    }
                });
    }
}
