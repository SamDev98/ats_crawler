package dev.jobscanner.source.impl;

import dev.jobscanner.metrics.ScannerMetrics;
import dev.jobscanner.model.Job;
import dev.jobscanner.source.JobSource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
public abstract class AbstractJobSource implements JobSource {

    protected final WebClient webClient;
    protected final ScannerMetrics metrics;

    protected AbstractJobSource(WebClient.Builder webClientBuilder, ScannerMetrics metrics) {
        this.webClient = webClientBuilder
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.metrics = metrics;
    }

    /**
     * Get the list of companies to scan for this source.
     */
    protected abstract List<String> getCompanies();

    /**
     * Fetch jobs for a single company.
     */
    protected abstract Mono<List<Job>> fetchCompanyJobs(String company);

    @Override
    public Flux<Job> fetchJobs() {
        List<String> companies = getCompanies();
        log.info("Fetching jobs from {} ({} companies)", getName(), companies.size());

        return Flux.fromIterable(companies)
                .flatMap(company -> fetchCompanyJobs(company)
                        .doOnError(e -> {
                            log.debug("{} - {} failed: {}", getName(), company, e.getMessage());
                            metrics.incrementFetchFailures(getName());
                        })
                        .onErrorResume(e -> Mono.just(List.of()))
                        .flatMapMany(Flux::fromIterable), 20) // 20 concurrent requests
                .doOnNext(job -> metrics.incrementJobsDiscovered(getName()));
    }

    /**
     * Strip HTML tags from text.
     */
    protected String stripHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(html).text();
    }

    /**
     * Build a Job with common defaults.
     */
    protected Job.JobBuilder baseJob() {
        return Job.builder()
                .source(getName())
                .discoveredAt(Instant.now());
    }

    /**
     * Execute a timed GET request.
     */
    protected <T> Mono<T> timedGet(String url, Class<T> responseType) {
        long start = System.currentTimeMillis();
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(30))
                .doOnTerminate(() -> {
                    long latency = System.currentTimeMillis() - start;
                    metrics.recordFetchLatency(getName(), latency);
                });
    }

    /**
     * Execute a timed POST request.
     */
    protected <T, R> Mono<T> timedPost(String url, R body, Class<T> responseType) {
        long start = System.currentTimeMillis();
        return webClient.post()
                .uri(url)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(30))
                .doOnTerminate(() -> {
                    long latency = System.currentTimeMillis() - start;
                    metrics.recordFetchLatency(getName(), latency);
                });
    }
}
