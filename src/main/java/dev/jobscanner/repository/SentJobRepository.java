package dev.jobscanner.repository;

import dev.jobscanner.entity.SentJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Repository for managing sent job records.
 */
@Repository
public interface SentJobRepository extends JpaRepository<SentJob, Long> {

    /**
     * Check if a job URL has already been sent.
     */
    boolean existsByUrl(String url);

    /**
     * Find all URLs that have been sent.
     */
    @Query("SELECT s.url FROM SentJob s")
    Set<String> findAllUrls();

    /**
     * Find URLs from a list that have already been sent.
     */
    @Query("SELECT s.url FROM SentJob s WHERE s.url IN :urls")
    Set<String> findExistingUrls(Set<String> urls);

    /**
     * Find jobs sent within a date range.
     */
    List<SentJob> findBySentAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Count jobs sent today.
     */
    @Query("SELECT COUNT(s) FROM SentJob s WHERE s.sentAt >= :startOfDay")
    long countSentToday(LocalDateTime startOfDay);

    /**
     * Find jobs sent by source.
     */
    List<SentJob> findBySource(String source);

    /**
     * Delete jobs older than a certain date (for cleanup).
     */
    void deleteBySentAtBefore(LocalDateTime date);
}
