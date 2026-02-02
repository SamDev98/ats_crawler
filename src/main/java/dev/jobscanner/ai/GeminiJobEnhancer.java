package dev.jobscanner.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jobscanner.model.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of JobEnhancer that uses Google AI Studio (Gemini) REST API.
 * Uses simple API key authentication - no service account required.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class GeminiJobEnhancer implements JobEnhancer {

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final WebClient webClient;
    private final String apiKey;

    public GeminiJobEnhancer(
            @Value("${app.ai.gemini.api-key}") String apiKey,
            @Value("${app.ai.gemini.model:gemini-1.5-flash}") String model) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl(Objects.requireNonNull(String.format(GEMINI_API_URL, model)))
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("Gemini AI Enhancement enabled with model: {}", model);
    }

    @Override
    public Mono<Job> enhance(Job job) {
        if (job.getDescription() == null || job.getDescription().isBlank()) {
            return Mono.just(job);
        }

        String prompt = buildPrompt(job);
        GeminiRequest request = buildRequest(prompt);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder.queryParam("key", apiKey).build())
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .bodyValue(Objects.requireNonNull(request))
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(this::isRetryableError))
                .map(response -> {
                    String analysis = extractContent(response);
                    if (analysis != null && !analysis.isBlank()) {
                        job.setAiAnalysis(analysis);
                        log.debug("AI analysis completed for: {}", job.getTitle());
                    }
                    return job;
                })
                .onErrorResume(e -> {
                    log.warn("AI enhancement failed for '{}': {}", job.getTitle(), e.getMessage());
                    return Mono.just(job);
                });
    }

    @Override
    public Mono<List<Job>> enhanceAll(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return Mono.just(jobs);
        }

        log.info("Starting AI enhancement for {} jobs...", jobs.size());

        // Process jobs sequentially with delay to respect rate limits
        return Mono.just(jobs)
                .flatMapIterable(list -> list)
                .delayElements(Duration.ofMillis(500)) // Rate limiting: ~2 req/sec
                .flatMap(this::enhance, 1) // Sequential processing
                .collectList()
                .doOnSuccess(enhanced -> log.info("AI enhancement completed for {} jobs", enhanced.size()));
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    private String buildPrompt(Job job) {
        String description = job.getDescription();
        if (description == null)
            description = "";

        // Truncate very long descriptions to stay within token limits
        if (description.length() > 8000) {
            description = description.substring(0, 8000) + "...";
        }

        return String.format("""
                Analise a seguinte vaga de emprego:

                Título: %s
                Empresa: %s
                Localização: %s

                Descrição:
                %s

                Responda em PORTUGUÊS de forma concisa (máximo 200 palavras):

                1. **Stack Principal**: Liste as tecnologias mencionadas (Java, Spring, Kafka, AWS, etc.)

                2. **Nível**: É uma vaga Sênior/Lead/Staff ou permite Mid-level?

                3. **Remote Status**: É realmente remoto global ou tem restrições geográficas?

                4. **Red Flags**: Algo preocupante? (muitas responsabilidades, salário baixo implícito, etc.)

                5. **Pontos Positivos**: Benefícios ou diferenciais mencionados?

                Seja direto e objetivo.
                """,
                job.getTitle() != null ? job.getTitle() : "Não informado",
                job.getCompany() != null ? job.getCompany() : "Não informada",
                job.getLocation() != null ? job.getLocation() : "Não informada",
                description);
    }

    private GeminiRequest buildRequest(String prompt) {
        return new GeminiRequest(List.of(
                new GeminiRequest.Content(List.of(
                        new GeminiRequest.Part(prompt)))),
                new GeminiRequest.GenerationConfig(0.3, 500));
    }

    private String extractContent(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return null;
        }
        var candidate = response.candidates().get(0);
        if (candidate.content() == null || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            return null;
        }
        return candidate.content().parts().get(0).text();
    }

    private boolean isRetryableError(Throwable e) {
        String message = e.getMessage();
        if (message == null)
            return false;
        // Retry on rate limits (429) or server errors (5xx)
        return message.contains("429") || message.contains("500") || message.contains("503");
    }

    // Request DTOs
    record GeminiRequest(
            List<Content> contents,
            @JsonProperty("generationConfig") GenerationConfig generationConfig) {
        record Content(List<Part> parts) {
        }

        record Part(String text) {
        }

        record GenerationConfig(double temperature, int maxOutputTokens) {
        }
    }

    // Response DTOs
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Candidate(Content content) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            record Content(List<Part> parts) {
                @JsonIgnoreProperties(ignoreUnknown = true)
                record Part(String text) {
                }
            }
        }
    }
}
