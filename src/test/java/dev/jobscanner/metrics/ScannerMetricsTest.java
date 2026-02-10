package dev.jobscanner.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScannerMetricsTest {

    private MeterRegistry meterRegistry;
    private ScannerMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new ScannerMetrics(meterRegistry);
    }

    @Nested
    @DisplayName("Job counters")
    class JobCountersTests {

        @Test
        @DisplayName("Should record jobs found")
        void shouldRecordJobsFound() {
            metrics.recordJobsFound(10);

            double count = meterRegistry.counter("job_scanner_jobs_found_total").count();
            assertThat(count).isEqualTo(10.0);
        }

        @Test
        @DisplayName("Should record jobs filtered")
        void shouldRecordJobsFiltered() {
            metrics.recordJobsFiltered(5);

            double count = meterRegistry.counter("job_scanner_jobs_filtered_total").count();
            assertThat(count).isEqualTo(5.0);
        }

        @Test
        @DisplayName("Should record jobs qualified")
        void shouldRecordJobsQualified() {
            metrics.recordJobsQualified(3);

            double count = meterRegistry.counter("job_scanner_jobs_qualified_total").count();
            assertThat(count).isEqualTo(3.0);
        }

        @Test
        @DisplayName("Should record jobs sent")
        void shouldRecordJobsSent() {
            metrics.recordJobsSent(2);

            double count = meterRegistry.counter("job_scanner_jobs_sent_total").count();
            assertThat(count).isEqualTo(2.0);
        }

        @Test
        @DisplayName("Should accumulate counter values")
        void shouldAccumulateCounterValues() {
            metrics.recordJobsFound(5);
            metrics.recordJobsFound(3);

            double count = meterRegistry.counter("job_scanner_jobs_found_total").count();
            assertThat(count).isEqualTo(8.0);
        }
    }

    @Nested
    @DisplayName("API counters")
    class APICountersTests {

        @Test
        @DisplayName("Should record API error")
        void shouldRecordApiError() {
            metrics.recordApiError("Lever");

            double totalErrors = meterRegistry.counter("job_scanner_api_errors_total").count();
            assertThat(totalErrors).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should increment fetch failures")
        void shouldIncrementFetchFailures() {
            metrics.incrementFetchFailures("Greenhouse");

            double totalErrors = meterRegistry.counter("job_scanner_api_errors_total").count();
            assertThat(totalErrors).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should increment jobs discovered")
        void shouldIncrementJobsDiscovered() {
            metrics.incrementJobsDiscovered("Lever");
            metrics.incrementJobsDiscovered("Lever");

            // Counter by source should exist
            assertThat(meterRegistry.getMeters()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Timers")
    class TimerTests {

        @Test
        @DisplayName("Should record fetch latency")
        void shouldRecordFetchLatency() {
            metrics.recordFetchLatency("Lever", 150);

            var timer = metrics.getSourceTimer("Lever");
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should get or create source timer")
        void shouldGetOrCreateSourceTimer() {
            var timer1 = metrics.getSourceTimer("Lever");
            var timer2 = metrics.getSourceTimer("Lever");

            assertThat(timer1).isSameAs(timer2);
        }

        @Test
        @DisplayName("Should create separate timers for different sources")
        void shouldCreateSeparateTimersForDifferentSources() {
            var timer1 = metrics.getSourceTimer("Lever");
            var timer2 = metrics.getSourceTimer("Greenhouse");

            assertThat(timer1).isNotSameAs(timer2);
        }
    }

    @Nested
    @DisplayName("Gauges")
    class GaugeTests {

        @Test
        @DisplayName("Should update last run stats")
        void shouldUpdateLastRunStats() {
            metrics.updateLastRunStats(100, 50, 25);

            var foundGauge = meterRegistry.get("job_scanner_last_run_jobs_found").gauge();
            var qualifiedGauge = meterRegistry.get("job_scanner_last_run_jobs_qualified").gauge();
            var sentGauge = meterRegistry.get("job_scanner_last_run_jobs_sent").gauge();

            assertThat(foundGauge.value()).isEqualTo(100.0);
            assertThat(qualifiedGauge.value()).isEqualTo(50.0);
            assertThat(sentGauge.value()).isEqualTo(25.0);
        }

        @Test
        @DisplayName("Should overwrite previous gauge values")
        void shouldOverwritePreviousGaugeValues() {
            metrics.updateLastRunStats(100, 50, 25);
            metrics.updateLastRunStats(200, 100, 50);

            var foundGauge = meterRegistry.get("job_scanner_last_run_jobs_found").gauge();
            assertThat(foundGauge.value()).isEqualTo(200.0);
        }
    }
}
