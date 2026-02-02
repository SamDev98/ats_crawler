package dev.jobscanner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
@RequiredArgsConstructor
public class JobScannerApplication implements CommandLineRunner {

    private final PipelineRunner pipelineRunner;
    private final ExitManager exitManager;

    public static void main(String[] args) {
        SpringApplication.run(JobScannerApplication.class, args);
    }

    @Override
    public void run(String... args) {
        try {
            pipelineRunner.execute();
            exitManager.exit(0);
        } catch (Exception e) {
            log.error("Fatal error during execution: {}", e.getMessage());
            exitManager.exit(1);
        }
    }
}
