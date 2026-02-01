package dev.jobscanner.ai;

import dev.jobscanner.model.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * No-op implementation of JobEnhancer.
 * Used when Spring AI is not enabled.
 */
@Slf4j
@Service
public class NoOpJobEnhancer implements JobEnhancer {

    public NoOpJobEnhancer() {
        log.info("AI Enhancement disabled - using no-op enhancer");
    }

    @Override
    public Mono<Job> enhance(Job job) {
        return Mono.just(job);
    }

    @Override
    public Mono<List<Job>> enhanceAll(List<Job> jobs) {
        return Mono.just(jobs);
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
