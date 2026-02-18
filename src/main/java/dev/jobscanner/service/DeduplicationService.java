package dev.jobscanner.service;

import dev.jobscanner.entity.SentJob;
import dev.jobscanner.model.Job;
import dev.jobscanner.repository.SentJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for job deduplication using SQLite storage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private final SentJobRepository sentJobRepository;

    /**
     * Filter out jobs that have already been sent.
     *
     * @param jobs List of jobs to filter
     * @return List of new jobs (not previously sent)
     */
    public List<Job> filterNewJobs(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return List.of();
        }

        Set<String> urls = jobs.stream()
                .map(Job::getUrl)
                .collect(Collectors.toSet());
        
        LocalDateTime since = LocalDateTime.now().minusDays(14);
        Set<String> existingUrls = sentJobRepository.findExistingUrls(urls, since);
        if (existingUrls == null) {
            existingUrls = Set.of();
        }

        Set<String> finalExistingUrls = existingUrls;
        List<Job> newJobs = jobs.stream()
                .filter(job -> !finalExistingUrls.contains(job.getUrl()))
                .toList();

        log.info("Deduplication: {} total jobs, {} already sent, {} new",
                jobs.size(), existingUrls.size(), newJobs.size());

        return newJobs;
    }

    /**
     * Mark jobs as sent (save to database).
     *
     * @param jobs List of jobs that were sent
     */
    @Transactional
    public void markAsSent(List<Job> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        List<SentJob> sentJobs = jobs.stream()
                .map(job -> SentJob.builder()
                        .url(job.getUrl())
                        .title(job.getTitle())
                        .company(job.getCompany())
                        .source(job.getSource())
                        .score(job.getScore())
                        .location(job.getLocation())
                        .sentAt(now)
                        .build())
                .toList();

        if (!sentJobs.isEmpty()) {
            sentJobRepository.saveAll(sentJobs);
            log.info("Marked {} jobs as sent", sentJobs.size());
        }
    }
}
