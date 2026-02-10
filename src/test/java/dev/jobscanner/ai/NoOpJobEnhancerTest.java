package dev.jobscanner.ai;

import dev.jobscanner.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpJobEnhancerTest {

    private NoOpJobEnhancer enhancer;

    @BeforeEach
    void setUp() {
        enhancer = new NoOpJobEnhancer();
    }

    private Job createJob(String title) {
        return Job.builder()
                .title(title)
                .description("Test description")
                .url("https://example.com/job")
                .company("Test Company")
                .location("Remote")
                .source("Test")
                .discoveredAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should return false for isEnabled")
    void shouldReturnFalseForIsEnabled() {
        assertThat(enhancer.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should return same job from enhance")
    void shouldReturnSameJobFromEnhance() {
        Job job = createJob("Java Developer");

        StepVerifier.create(enhancer.enhance(job))
                .assertNext(result -> assertThat(result).isSameAs(job))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return same list from enhanceAll")
    void shouldReturnSameListFromEnhanceAll() {
        List<Job> jobs = List.of(
                createJob("Java Developer 1"),
                createJob("Java Developer 2"));

        StepVerifier.create(enhancer.enhanceAll(jobs))
                .assertNext(result -> assertThat(result).isSameAs(jobs))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle empty list")
    void shouldHandleEmptyList() {
        List<Job> jobs = List.of();

        StepVerifier.create(enhancer.enhanceAll(jobs))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }
}
