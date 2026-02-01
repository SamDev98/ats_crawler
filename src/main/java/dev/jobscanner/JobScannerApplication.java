package dev.jobscanner;

import dev.jobscanner.model.Job;
import dev.jobscanner.service.JobScannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.List;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
@RequiredArgsConstructor
public class JobScannerApplication implements CommandLineRunner {

    private final JobScannerService jobScannerService;

    @Value("${scanner.metrics-wait-seconds:30}")
    private int metricsWaitSeconds;

    public static void main(String[] args) {
        SpringApplication.run(JobScannerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("Job Scanner Starting");
        log.info("========================================");

        try {
            List<Job> qualifiedJobs = jobScannerService.runPipeline().block();

            log.info("========================================");
            log.info("Job Scanner Completed Successfully");
            log.info("Jobs sent: {}", qualifiedJobs != null ? qualifiedJobs.size() : 0);
            log.info("========================================");

            // Keep alive for Prometheus scrape (configurable)
            if (metricsWaitSeconds > 0) {
                log.info("Keeping alive for {} seconds (metrics scrape)...", metricsWaitSeconds);
                try {
                    Thread.sleep(metricsWaitSeconds * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Metrics wait interrupted");
                }
            }

            log.info("Job Scanner exiting...");
            System.exit(0);

        } catch (Exception e) {
            log.error("Job Scanner failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
