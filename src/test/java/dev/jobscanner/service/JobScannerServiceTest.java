package dev.jobscanner.service;

import dev.jobscanner.ai.JobEnhancer;
import dev.jobscanner.metrics.ScannerMetrics;
import dev.jobscanner.model.Job;
import dev.jobscanner.service.RulesService.EligibilityResult;
import dev.jobscanner.service.ScoringService.ScoringResult;
import dev.jobscanner.source.JobSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobScannerServiceTest {

        @Mock
        private JobSource jobSource1;

        @Mock
        private JobSource jobSource2;

        @Mock
        private RulesService rulesService;

        @Mock
        private ScoringService scoringService;

        @Mock
        private DeduplicationService deduplicationService;

        @Mock
        private EmailService emailService;

        @Mock
        private JobEnhancer jobEnhancer;

        @Mock
        private ScannerMetrics metrics;

        private JobScannerService jobScannerService;

        @BeforeEach
        void setUp() {
                List<JobSource> sources = List.of(jobSource1, jobSource2);
                jobScannerService = new JobScannerService(
                                sources, rulesService, scoringService, deduplicationService,
                                emailService, jobEnhancer, metrics);
                ReflectionTestUtils.setField(jobScannerService, "dryRun", false);
        }

        private Job createJob(String id, String title) {
                return Job.builder()
                                .title(title)
                                .description("Test description with Java and Spring Boot")
                                .url("https://example.com/job/" + id)
                                .company("Test Company")
                                .location("Remote")
                                .source("Test")
                                .discoveredAt(Instant.now())
                                .build();
        }

        @Nested
        @DisplayName("Pipeline execution")
        class PipelineTests {

                @Test
                @DisplayName("Should process jobs through full pipeline")
                void shouldProcessJobsThroughFullPipeline() {
                        Job job1 = createJob("1", "Senior Java Developer");
                        Job job2 = createJob("2", "Java Engineer");

                        when(jobSource1.getName()).thenReturn("Source1");
                        when(jobSource2.getName()).thenReturn("Source2");
                        when(jobSource1.fetchJobs()).thenReturn(Flux.just(job1));
                        when(jobSource2.fetchJobs()).thenReturn(Flux.just(job2));
                        when(deduplicationService.filterNewJobs(any())).thenReturn(List.of(job1, job2));
                        when(rulesService.checkEligibility(any()))
                                        .thenReturn(EligibilityResult.eligible(true, false, true));
                        when(scoringService.calculateScore(any(), any()))
                                        .thenReturn(new ScoringResult(80, true, Map.of("test", 80)));
                        when(scoringService.getThreshold()).thenReturn(70);
                        when(jobEnhancer.isEnabled()).thenReturn(false);
                        when(emailService.sendJobDigest(any())).thenReturn(Mono.just(true));

                        StepVerifier.create(jobScannerService.runPipeline())
                                        .assertNext(result -> assertThat(result).hasSize(2))
                                        .verifyComplete();

                        verify(deduplicationService).markAsSent(any());
                        verify(metrics).recordJobsSent(2);
                }

                @Test
                @DisplayName("Should return empty list when no new jobs")
                void shouldReturnEmptyWhenNoNewJobs() {
                        Job job = createJob("1", "Java Developer");

                        when(jobSource1.getName()).thenReturn("Source1");
                        when(jobSource2.getName()).thenReturn("Source2");
                        when(jobSource1.fetchJobs()).thenReturn(Flux.just(job));
                        when(jobSource2.fetchJobs()).thenReturn(Flux.empty());
                        when(deduplicationService.filterNewJobs(any())).thenReturn(List.of());

                        StepVerifier.create(jobScannerService.runPipeline())
                                        .assertNext(result -> assertThat(result).isEmpty())
                                        .verifyComplete();

                        verify(emailService, never()).sendJobDigest(any());
                }

                @Test
                @DisplayName("Should filter out ineligible jobs")
                void shouldFilterOutIneligibleJobs() {
                        Job job1 = createJob("1", "Java Developer");
                        Job job2 = createJob("2", "Python Developer");

                        when(jobSource1.getName()).thenReturn("Source1");
                        when(jobSource2.getName()).thenReturn("Source2");
                        when(jobSource1.fetchJobs()).thenReturn(Flux.just(job1));
                        when(jobSource2.fetchJobs()).thenReturn(Flux.just(job2));
                        when(deduplicationService.filterNewJobs(any())).thenReturn(List.of(job1, job2));
                        when(rulesService.checkEligibility(job1))
                                        .thenReturn(EligibilityResult.eligible(true, false, true));
                        when(rulesService.checkEligibility(job2))
                                        .thenReturn(EligibilityResult.blocked("Not Java-related"));
                        when(scoringService.calculateScore(eq(job1), any()))
                                        .thenReturn(new ScoringResult(80, true, Map.of("test", 80)));
                        when(scoringService.getThreshold()).thenReturn(70);
                        when(jobEnhancer.isEnabled()).thenReturn(false);
                        when(emailService.sendJobDigest(any())).thenReturn(Mono.just(true));

                        StepVerifier.create(jobScannerService.runPipeline())
                                        .assertNext(result -> assertThat(result).hasSize(1))
                                        .verifyComplete();
                }

                @Test
                @DisplayName("Should filter out low score jobs")
                void shouldFilterOutLowScoreJobs() {
                        Job job1 = createJob("1", "Senior Java Developer");
                        Job job2 = createJob("2", "Java Developer");

                        when(jobSource1.getName()).thenReturn("Source1");
                        when(jobSource2.getName()).thenReturn("Source2");
                        when(jobSource1.fetchJobs()).thenReturn(Flux.just(job1));
                        when(jobSource2.fetchJobs()).thenReturn(Flux.just(job2));
                        when(deduplicationService.filterNewJobs(any())).thenReturn(List.of(job1, job2));
                        when(rulesService.checkEligibility(any()))
                                        .thenReturn(EligibilityResult.eligible(true, false, true));
                        when(scoringService.calculateScore(eq(job1), any()))
                                        .thenReturn(new ScoringResult(80, true, Map.of("test", 80)));
                        when(scoringService.calculateScore(eq(job2), any()))
                                        .thenReturn(new ScoringResult(50, false, Map.of("test", 50)));
                        when(scoringService.getThreshold()).thenReturn(70);
                        when(jobEnhancer.isEnabled()).thenReturn(false);
                        when(emailService.sendJobDigest(any())).thenReturn(Mono.just(true));

                        StepVerifier.create(jobScannerService.runPipeline())
                                        .assertNext(result -> assertThat(result).hasSize(1))
                                        .verifyComplete();
                }

                @Test
                @DisplayName("Should sort jobs by score descending")
                void shouldSortJobsByScoreDescending() {
                        Job job1 = createJob("1", "Java Developer");
                        Job job2 = createJob("2", "Senior Java Developer");

                        when(jobSource1.getName()).thenReturn("Source1");
                        when(jobSource2.getName()).thenReturn("Source2");
                        when(jobSource1.fetchJobs()).thenReturn(Flux.just(job1));
                        when(jobSource2.fetchJobs()).thenReturn(Flux.just(job2));
                        when(deduplicationService.filterNewJobs(any())).thenReturn(List.of(job1, job2));
                        when(rulesService.checkEligibility(any()))
                                        .thenReturn(EligibilityResult.eligible(true, false, true));
                        when(scoringService.calculateScore(eq(job1), any()))
                                        .thenReturn(new ScoringResult(70, true, Map.of("test", 70)));
                        when(scoringService.calculateScore(eq(job2), any()))
                                        .thenReturn(new ScoringResult(90, true, Map.of("test", 90)));
                        when(scoringService.getThreshold()).thenReturn(70);
                        when(jobEnhancer.isEnabled()).thenReturn(false);
                        when(emailService.sendJobDigest(any())).thenReturn(Mono.just(true));

                        StepVerifier.create(jobScannerService.runPipeline())
                                        .assertNext(result -> {
                                                assertThat(result).hasSize(2);
                                                assertThat(result.get(0).getScore())
                                                                .isGreaterThan(result.get(1).getScore());
                                        })
                                        .verifyComplete();
                }
        }

        @Nested
        @DisplayName("Dry run mode")
        class DryRunTests {

                @Test
                @DisplayName("Should not send email in dry run mode")
                void shouldNotSendEmailInDryRunMode() {
                        ReflectionTestUtils.setField(Objects.requireNonNull(jobScannerService), "dryRun", true);

                        Job job = createJob("1", "Java Developer");

                        when(jobSource1.getName()).thenReturn("Source1");
                        when(jobSource2.getName()).thenReturn("Source2");
                        when(jobSource1.fetchJobs()).thenReturn(Flux.just(job));
                        when(jobSource2.fetchJobs()).thenReturn(Flux.empty());
                        when(deduplicationService.filterNewJobs(any())).thenReturn(List.of(job));
                        when(rulesService.checkEligibility(any()))
                                        .thenReturn(EligibilityResult.eligible(true, false, true));
                        when(scoringService.calculateScore(any(), any()))
                                        .thenReturn(new ScoringResult(80, true, Map.of("test", 80)));
                        when(scoringService.getThreshold()).thenReturn(70);
                        when(jobEnhancer.isEnabled()).thenReturn(false);

                        StepVerifier.create(jobScannerService.runPipeline())
                                        .assertNext(result -> assertThat(result).hasSize(1))
                                        .verifyComplete();

                        verify(emailService, never()).sendJobDigest(any());
                        verify(deduplicationService, never()).markAsSent(any());
                }
        }

        @Nested
        @DisplayName("AI enhancement")
        class AIEnhancementTests {

                @Test
                @DisplayName("Should enhance jobs when AI is enabled")
                void shouldEnhanceJobsWhenAIEnabled() {
                        Job job = createJob("1", "Java Developer");

                        when(jobSource1.getName()).thenReturn("Source1");
                        when(jobSource2.getName()).thenReturn("Source2");
                        when(jobSource1.fetchJobs()).thenReturn(Flux.just(job));
                        when(jobSource2.fetchJobs()).thenReturn(Flux.empty());
                        when(deduplicationService.filterNewJobs(any())).thenReturn(List.of(job));
                        when(rulesService.checkEligibility(any()))
                                        .thenReturn(EligibilityResult.eligible(true, false, true));
                        when(scoringService.calculateScore(any(), any()))
                                        .thenReturn(new ScoringResult(80, true, Map.of("test", 80)));
                        when(scoringService.getThreshold()).thenReturn(70);
                        when(jobEnhancer.isEnabled()).thenReturn(true);
                        when(jobEnhancer.enhanceAll(any())).thenReturn(Mono.just(List.of(job)));
                        when(emailService.sendJobDigest(any())).thenReturn(Mono.just(true));

                        StepVerifier.create(jobScannerService.runPipeline())
                                        .assertNext(result -> assertThat(result).hasSize(1))
                                        .verifyComplete();

                        verify(jobEnhancer).enhanceAll(any());
                }

                @Test
                @DisplayName("Should skip AI enhancement when disabled")
                void shouldSkipAIEnhancementWhenDisabled() {
                        Job job = createJob("1", "Java Developer");

                        when(jobSource1.getName()).thenReturn("Source1");
                        when(jobSource2.getName()).thenReturn("Source2");
                        when(jobSource1.fetchJobs()).thenReturn(Flux.just(job));
                        when(jobSource2.fetchJobs()).thenReturn(Flux.empty());
                        when(deduplicationService.filterNewJobs(any())).thenReturn(List.of(job));
                        when(rulesService.checkEligibility(any()))
                                        .thenReturn(EligibilityResult.eligible(true, false, true));
                        when(scoringService.calculateScore(any(), any()))
                                        .thenReturn(new ScoringResult(80, true, Map.of("test", 80)));
                        when(scoringService.getThreshold()).thenReturn(70);
                        when(jobEnhancer.isEnabled()).thenReturn(false);
                        when(emailService.sendJobDigest(any())).thenReturn(Mono.just(true));

                        StepVerifier.create(jobScannerService.runPipeline())
                                        .assertNext(result -> assertThat(result).hasSize(1))
                                        .verifyComplete();

                        verify(jobEnhancer, never()).enhanceAll(any());
                }

                @Test
                @DisplayName("Should continue without AI on enhancement failure")
                void shouldContinueWithoutAIOnEnhancementFailure() {
                        Job job = createJob("1", "Java Developer");

                        when(jobSource1.getName()).thenReturn("Source1");
                        when(jobSource2.getName()).thenReturn("Source2");
                        when(jobSource1.fetchJobs()).thenReturn(Flux.just(job));
                        when(jobSource2.fetchJobs()).thenReturn(Flux.empty());
                        when(deduplicationService.filterNewJobs(any())).thenReturn(List.of(job));
                        when(rulesService.checkEligibility(any()))
                                        .thenReturn(EligibilityResult.eligible(true, false, true));
                        when(scoringService.calculateScore(any(), any()))
                                        .thenReturn(new ScoringResult(80, true, Map.of("test", 80)));
                        when(scoringService.getThreshold()).thenReturn(70);
                        when(jobEnhancer.isEnabled()).thenReturn(true);
                        when(jobEnhancer.enhanceAll(any())).thenReturn(Mono.error(new RuntimeException("AI error")));
                        when(emailService.sendJobDigest(any())).thenReturn(Mono.just(true));

                        StepVerifier.create(jobScannerService.runPipeline())
                                        .assertNext(result -> assertThat(result).hasSize(1))
                                        .verifyComplete();
                }
        }

        @Nested
        @DisplayName("Email sending")
        class EmailSendingTests {

                @Test
                @DisplayName("Should mark jobs as sent after successful email")
                void shouldMarkJobsAsSentAfterSuccessfulEmail() {
                        Job job = createJob("1", "Java Developer");

                        when(jobSource1.getName()).thenReturn("Source1");
                        when(jobSource2.getName()).thenReturn("Source2");
                        when(jobSource1.fetchJobs()).thenReturn(Flux.just(job));
                        when(jobSource2.fetchJobs()).thenReturn(Flux.empty());
                        when(deduplicationService.filterNewJobs(any())).thenReturn(List.of(job));
                        when(rulesService.checkEligibility(any()))
                                        .thenReturn(EligibilityResult.eligible(true, false, true));
                        when(scoringService.calculateScore(any(), any()))
                                        .thenReturn(new ScoringResult(80, true, Map.of("test", 80)));
                        when(scoringService.getThreshold()).thenReturn(70);
                        when(jobEnhancer.isEnabled()).thenReturn(false);
                        when(emailService.sendJobDigest(any())).thenReturn(Mono.just(true));

                        StepVerifier.create(jobScannerService.runPipeline())
                                        .assertNext(result -> assertThat(result).hasSize(1))
                                        .verifyComplete();

                        verify(deduplicationService).markAsSent(any());
                }

                @Test
                @DisplayName("Should not mark jobs as sent on email failure")
                void shouldNotMarkJobsAsSentOnEmailFailure() {
                        Job job = createJob("1", "Java Developer");

                        when(jobSource1.getName()).thenReturn("Source1");
                        when(jobSource2.getName()).thenReturn("Source2");
                        when(jobSource1.fetchJobs()).thenReturn(Flux.just(job));
                        when(jobSource2.fetchJobs()).thenReturn(Flux.empty());
                        when(deduplicationService.filterNewJobs(any())).thenReturn(List.of(job));
                        when(rulesService.checkEligibility(any()))
                                        .thenReturn(EligibilityResult.eligible(true, false, true));
                        when(scoringService.calculateScore(any(), any()))
                                        .thenReturn(new ScoringResult(80, true, Map.of("test", 80)));
                        when(scoringService.getThreshold()).thenReturn(70);
                        when(jobEnhancer.isEnabled()).thenReturn(false);
                        when(emailService.sendJobDigest(any())).thenReturn(Mono.just(false));

                        StepVerifier.create(jobScannerService.runPipeline())
                                        .expectError(RuntimeException.class)
                                        .verify();

                        verify(deduplicationService, never()).markAsSent(any());
                }
        }

        @Nested
        @DisplayName("Metrics")
        class MetricsTests {

                @Test
                @DisplayName("Should record metrics throughout pipeline")
                void shouldRecordMetricsThroughoutPipeline() {
                        Job job = createJob("1", "Java Developer");

                        when(jobSource1.getName()).thenReturn("Source1");
                        when(jobSource2.getName()).thenReturn("Source2");
                        when(jobSource1.fetchJobs()).thenReturn(Flux.just(job));
                        when(jobSource2.fetchJobs()).thenReturn(Flux.empty());
                        when(deduplicationService.filterNewJobs(any())).thenReturn(List.of(job));
                        when(rulesService.checkEligibility(any()))
                                        .thenReturn(EligibilityResult.eligible(true, false, true));
                        when(scoringService.calculateScore(any(), any()))
                                        .thenReturn(new ScoringResult(80, true, Map.of("test", 80)));
                        when(scoringService.getThreshold()).thenReturn(70);
                        when(jobEnhancer.isEnabled()).thenReturn(false);
                        when(emailService.sendJobDigest(any())).thenReturn(Mono.just(true));

                        jobScannerService.runPipeline().block();

                        verify(metrics).recordJobsFound(1);
                        verify(metrics).recordJobsFiltered(0);
                        verify(metrics).recordJobsQualified(1);
                        verify(metrics).recordJobsSent(1);
                        verify(metrics).updateLastRunStats(1, 1, 1);
                }
        }
}
