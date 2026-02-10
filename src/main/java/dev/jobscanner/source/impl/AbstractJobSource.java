package dev.jobscanner.source.impl;

import dev.jobscanner.metrics.ScannerMetrics;
import dev.jobscanner.model.Job;
import dev.jobscanner.source.JobSource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class AbstractJobSource implements JobSource {

    protected final WebClient webClient;
    protected final ScannerMetrics metrics;

    // Track companies that need investigation (static to share across instances if
    // needed, or per source)
    protected final Set<String> deadCompanies = Collections.newSetFromMap(new ConcurrentHashMap<>());
    protected final Set<String> emptyCompanies = Collections.newSetFromMap(new ConcurrentHashMap<>());

    protected AbstractJobSource(WebClient.Builder webClientBuilder, ScannerMetrics metrics) {
        HttpClient httpClient = HttpClient.create()
                .httpResponseDecoder(spec -> spec.maxHeaderSize(32768));

        this.webClient = webClientBuilder
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .clientConnector(new ReactorClientHttpConnector(Objects.requireNonNull(httpClient)))
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "application/json, text/plain, */*")
                .defaultHeader("Accept-Language", "en-US,en;q=0.9")
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
                .delayElements(Duration.ofMillis(300)) // Increase delay to 300ms per company
                .flatMap(company -> fetchCompanyJobs(company)
                        .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                                .filter(e -> e.getMessage() != null && e.getMessage().contains("429")))
                        .doOnNext(jobs -> {
                            if (jobs.isEmpty()) {
                                emptyCompanies.add(company);
                            }
                        })
                        .doOnError(e -> {
                            // Avoid log noise for 404s (companies that likely moved ATS)
                            if (e.getMessage() != null && e.getMessage().contains("404")) {
                                deadCompanies.add(company);
                                log.debug("{} - {} likely moved or changed ATS: {}", getName(), company,
                                        e.getMessage());
                            } else {
                                log.warn("{} - {} failed: {}", getName(), company, e.getMessage());
                            }
                            metrics.incrementFetchFailures(getName());
                        })
                        .onErrorResume(e -> Mono.just(List.of()))
                        .flatMapMany(Flux::fromIterable), 3) // Reduce concurrency to 3
                .doOnTerminate(this::logCleanupReport)
                .doOnNext(job -> metrics.incrementJobsDiscovered(getName()));
    }

    private void logCleanupReport() {
        if (!deadCompanies.isEmpty()) {
            log.info("--- CLEANUP RECOMMENDATION (DEAD) for {}: {} ---", getName(), String.join(", ", deadCompanies));
        }
        if (!emptyCompanies.isEmpty()) {
            log.info("--- EMPTY COMPANIES for {} (0 jobs): {} ---", getName(), String.join(", ", emptyCompanies));
        }
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
     * Standard company name formatting from slug (e.g. "nubank-brazil" -> "Nubank
     * brazil").
     */
    protected String formatCompanyName(String company) {
        if (company == null || company.isBlank())
            return "";
        String spaced = company.replace("-", " ");
        if (spaced.length() < 2)
            return spaced.toUpperCase();
        return spaced.substring(0, 1).toUpperCase() + spaced.substring(1);
    }

    /**
     * Execute a timed GET request.
     */
    @SuppressWarnings("null")
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
    @SuppressWarnings("null")
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
