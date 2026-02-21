package dev.jobscanner.repository;

import dev.jobscanner.entity.SentJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Repository for managing sent job records.
 */
@Repository
public interface SentJobRepository extends JpaRepository<SentJob, Long> {

    /**
     * Find URLs from a list that have already been sent.
     */

    @Query("SELECT s.url FROM SentJob s WHERE s.url IN :urls AND s.sentAt > :since")
    Set<String> findExistingUrls(Set<String> urls, LocalDateTime since);
}
