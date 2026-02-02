package dev.jobscanner;

import dev.jobscanner.model.Job;
import dev.jobscanner.service.JobScannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orchestrates the execution of the job scanner pipeline.
 * Separated from the main Application class for better testability and SRP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineRunner {

  private static final String SEPARATOR = "========================================";

  private final JobScannerService jobScannerService;

  @Value("${scanner.metrics-wait-seconds:0}")
  private int metricsWaitSeconds;

  /**
   * Executes the job scanner pipeline and handles the post-execution wait.
   * 
   * @return Number of qualified jobs found
   */
  public int execute() {
    log.info(SEPARATOR);
    log.info("Job Scanner Starting");
    log.info(SEPARATOR);

    try {
      List<Job> qualifiedJobs = jobScannerService.runPipeline().block();
      int count = qualifiedJobs != null ? qualifiedJobs.size() : 0;

      log.info(SEPARATOR);
      log.info("Job Scanner Completed Successfully");
      log.info("Jobs sent: {}", count);
      log.info(SEPARATOR);

      handleMetricsWait();

      return count;
    } catch (Exception e) {
      log.error("Job Scanner failed: {}", e.getMessage(), e);
      throw new IllegalStateException("Pipeline execution failed", e);
    }
  }

  private void handleMetricsWait() {
    if (metricsWaitSeconds > 0) {
      log.info("Keeping alive for {} seconds (metrics scrape)...", metricsWaitSeconds);
      try {
        Thread.sleep(metricsWaitSeconds * 1000L);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        log.warn("Metrics wait interrupted");
      }
    }
  }
}
