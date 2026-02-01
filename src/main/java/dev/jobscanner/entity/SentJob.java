package dev.jobscanner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for tracking jobs that have been sent via email.
 * Used for deduplication across runs.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sent_jobs", indexes = {
        @Index(name = "idx_url", columnList = "url"),
        @Index(name = "idx_sent_at", columnList = "sentAt")
})
public class SentJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 2048)
    private String url;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @Column(length = 500)
    private String location;
}
