package dev.jobscanner.service;

import dev.jobscanner.entity.SentJob;
import dev.jobscanner.model.Job;
import dev.jobscanner.repository.SentJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeduplicationServiceTest {

    @Mock
    private SentJobRepository sentJobRepository;

    @Captor
    private ArgumentCaptor<List<SentJob>> sentJobsCaptor;

    private DeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        deduplicationService = new DeduplicationService(sentJobRepository);
    }

    private Job createJob(String url, String title) {
        return Job.builder()
                .id("test-" + url.hashCode())
                .title(title)
                .description("Test description")
                .url(url)
                .company("Test Company")
                .location("Remote")
                .source("Test")
                .score(80)
                .discoveredAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Filter new jobs")
    class FilterNewJobsTests {

        @Test
        @DisplayName("Should return all jobs when none are in database")
        void shouldReturnAllJobsWhenNoneInDb() {
            List<Job> jobs = List.of(
                    createJob("https://example.com/job1", "Java Developer 1"),
                    createJob("https://example.com/job2", "Java Developer 2"));
            when(sentJobRepository.findExistingUrls(any())).thenReturn(Set.of());

            List<Job> result = deduplicationService.filterNewJobs(jobs);

            assertThat(result)
                    .hasSize(2)
                    .containsExactlyElementsOf(jobs);
        }

        @Test
        @DisplayName("Should filter out existing jobs")
        void shouldFilterOutExistingJobs() {
            List<Job> jobs = List.of(
                    createJob("https://example.com/job1", "Java Developer 1"),
                    createJob("https://example.com/job2", "Java Developer 2"),
                    createJob("https://example.com/job3", "Java Developer 3"));
            when(sentJobRepository.findExistingUrls(any()))
                    .thenReturn(Set.of("https://example.com/job1", "https://example.com/job3"));

            List<Job> result = deduplicationService.filterNewJobs(jobs);

            assertThat(result)
                    .hasSize(1)
                    .extracting(Job::getUrl)
                    .containsExactly("https://example.com/job2");
        }

        @Test
        @DisplayName("Should return empty list when all jobs exist")
        void shouldReturnEmptyWhenAllJobsExist() {
            List<Job> jobs = List.of(
                    createJob("https://example.com/job1", "Java Developer 1"),
                    createJob("https://example.com/job2", "Java Developer 2"));
            when(sentJobRepository.findExistingUrls(any()))
                    .thenReturn(Set.of("https://example.com/job1", "https://example.com/job2"));

            List<Job> result = deduplicationService.filterNewJobs(jobs);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when input is empty")
        void shouldReturnEmptyWhenInputIsEmpty() {
            List<Job> result = deduplicationService.filterNewJobs(List.of());

            assertThat(result).isEmpty();
            verify(sentJobRepository, never()).findExistingUrls(any());
        }
    }

    @Nested
    @DisplayName("Mark as sent")
    class MarkAsSentTests {

        @Test
        @DisplayName("Should save all jobs to repository")
        @SuppressWarnings("null")
        void shouldSaveAllJobsToRepository() {
            List<Job> jobs = List.of(
                    createJob("https://example.com/job1", "Java Developer 1"),
                    createJob("https://example.com/job2", "Java Developer 2"));

            deduplicationService.markAsSent(jobs);

            verify(sentJobRepository).saveAll(sentJobsCaptor.capture());
            List<SentJob> savedJobs = sentJobsCaptor.getValue();
            assertThat(savedJobs).hasSize(2);
        }

        @Test
        @DisplayName("Should map job fields correctly")
        @SuppressWarnings("null")
        void shouldMapJobFieldsCorrectly() {
            Job job = createJob("https://example.com/job1", "Senior Java Developer");
            job.setCompany("Acme Corp");
            job.setSource("Lever");
            job.setScore(85);
            job.setLocation("Remote - Brazil");

            deduplicationService.markAsSent(List.of(job));

            verify(sentJobRepository).saveAll(sentJobsCaptor.capture());
            SentJob savedJob = sentJobsCaptor.getValue().get(0);

            assertThat(savedJob)
                    .extracting(SentJob::getUrl, SentJob::getTitle, SentJob::getCompany, SentJob::getSource,
                            SentJob::getScore, SentJob::getLocation)
                    .containsExactly("https://example.com/job1", "Senior Java Developer", "Acme Corp", "Lever", 85,
                            "Remote - Brazil");
            assertThat(savedJob.getSentAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Check already sent")
    class CheckAlreadySentTests {

        @Test
        @DisplayName("Should return true when URL exists")
        void shouldReturnTrueWhenUrlExists() {
            when(sentJobRepository.existsByUrl("https://example.com/job1")).thenReturn(true);

            boolean result = deduplicationService.isAlreadySent("https://example.com/job1");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when URL does not exist")
        void shouldReturnFalseWhenUrlDoesNotExist() {
            when(sentJobRepository.existsByUrl("https://example.com/job1")).thenReturn(false);

            boolean result = deduplicationService.isAlreadySent("https://example.com/job1");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should return jobs sent today count")
        void shouldReturnJobsSentTodayCount() {
            when(sentJobRepository.countSentToday(any())).thenReturn(15L);

            long result = deduplicationService.getJobsSentToday();

            assertThat(result).isEqualTo(15);
        }

        @Test
        @DisplayName("Should return total sent jobs count")
        void shouldReturnTotalSentJobsCount() {
            when(sentJobRepository.count()).thenReturn(100L);

            long result = deduplicationService.getTotalSentJobs();

            assertThat(result).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Cleanup")
    class CleanupTests {

        @Test
        @DisplayName("Should delete old records")
        void shouldDeleteOldRecords() {
            deduplicationService.cleanupOldRecords(30);

            verify(sentJobRepository).deleteBySentAtBefore(any(LocalDateTime.class));
        }
    }
}
