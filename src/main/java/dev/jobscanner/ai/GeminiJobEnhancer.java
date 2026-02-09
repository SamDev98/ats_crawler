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
import reactor.core.publisher.Flux;
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
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini")
public class GeminiJobEnhancer implements JobEnhancer {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final String geminiPath;

    public GeminiJobEnhancer(
            @Value("${app.ai.gemini.api-key}") String apiKey,
            @Value("${app.ai.gemini.model:gemini-flash-latest}") String model,
            @Value("${app.ai.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${app.ai.gemini.path:/v1beta/models/%s:generateContent}") String geminiPath) {
        this.apiKey = apiKey;
        // Fix for gemini-1.5-flash which is deprecated/404 in some regions in 2026
        this.model = "gemini-1.5-flash".equals(model) ? "gemini-flash-latest" : model;
        this.geminiPath = Objects.requireNonNull(geminiPath);

        this.webClient = WebClient.builder()
                .baseUrl(Objects.requireNonNull(baseUrl))
                .defaultHeader("Content-Type", "application/json")
                .build();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API Key is missing! AI enhancement will fail.");
        } else {
            log.info("Gemini AI Enhancement enabled with model: {} (Key present)", this.model);
        }
    }

    @Override
    public Mono<Job> enhance(Job job) {
        if (job.getDescription() == null || job.getDescription().isBlank() || apiKey == null || apiKey.isBlank()) {
            return Mono.just(job);
        }

        String prompt = buildPrompt(job);
        GeminiRequest request = buildRequest(prompt);

        String uri = String.format(geminiPath, model) + "?key=" + apiKey;

        return webClient.post()
                .uri(uri)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .bodyValue(Objects.requireNonNull(request))
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .timeout(Duration.ofSeconds(60))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(this::isRetryableError)
                        .doBeforeRetry(retrySignal -> log.info("Retrying AI enhancement for '{}' (Attempt {})",
                                job.getTitle(), retrySignal.totalRetries() + 1)))
                .map(response -> {
                    String analysis = extractContent(response);
                    if (analysis != null && !analysis.isBlank()) {
                        job.setAiAnalysis(analysis);
                        log.debug("AI analysis completed for: {}", job.getTitle());
                    }
                    return job;
                })
                .onErrorResume(e -> {
                    log.warn("AI enhancement permanent failure for '{}': {}", job.getTitle(), e.getMessage());
                    return Mono.just(job);
                });
    }

    @Override
    public Mono<List<Job>> enhanceAll(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return Mono.just(jobs);
        }

        log.info("Starting AI enhancement for {} jobs in a single batch...", jobs.size());

        // Increase batch size to process all jobs at once (up to 20)
        int batchSize = 25;

        return Flux.fromIterable(partitionList(jobs, batchSize))
                .flatMap(this::enhanceBatch, 1) // Sequential batch processing
                .flatMapIterable(list -> list)
                .collectList()
                .doOnSuccess(enhanced -> log.info("AI enhancement completed for {} jobs", enhanced.size()));
    }

    private Mono<List<Job>> enhanceBatch(List<Job> batch) {
        if (batch.isEmpty())
            return Mono.just(batch);
        if (batch.size() == 1)
            return enhance(batch.get(0)).map(List::of);

        log.info("Processing batch of {} jobs...", batch.size());

        StringBuilder compositePrompt = new StringBuilder();
        compositePrompt.append(
                "Você é um recrutador técnico especialista em Java. Analise as vagas abaixo e forneça um parecer CONCISO para cada uma.\n\n");

        for (int i = 0; i < batch.size(); i++) {
            Job job = batch.get(i);
            compositePrompt.append(String.format("--- VAGA ID: %d ---%n", i));
            compositePrompt.append(String.format("Título: %s%nEmpresa: %s%nLocalização: %s%nDescrição:%n%s%n%n",
                    job.getTitle(), job.getCompany(), job.getLocation(),
                    truncateDescription(job.getDescription())));
        }

        compositePrompt
                .append("""
                        Para CADA VAGA acima, responda EXATAMENTE neste formato, mantendo o ID da vaga (Siga o formato abaixo rigorosamente e sem formatação markdown no ID):

                        ###RESPOSTA ID: [ID]###
                        Veredito: [EXCELENTE / BOM / POUCO RELEVANTE / DESCARTAR]
                        Score IA: [0-100]
                        Stack: [Lista curta de tecnologias principais]
                        Nível: [Senior/Mid/Lead/Staff]
                        Match: [Explique em 2 frases curtas por que esta vaga encaixa ou não no perfil de Java Developer]
                        Red Flags: [Qualquer ponto impeditivo ou negativo]
                        """);

        GeminiRequest request = buildRequest(compositePrompt.toString());
        String uri = String.format(geminiPath, model) + "?key=" + apiKey;

        return webClient.post()
                .uri(uri)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .bodyValue(Objects.requireNonNull(request))
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .timeout(Duration.ofSeconds(180))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(5)).filter(this::isRetryableError))
                .map(response -> {
                    String fullContent = extractContent(response);
                    if (fullContent != null) {
                        parseBatchResponse(batch, fullContent);
                    }
                    return batch;
                })
                .onErrorResume(e -> {
                    log.error("Batch AI enhancement failed: {}", e.getMessage());
                    return Mono.just(batch); // Continue with original jobs on error
                });
    }

    private void parseBatchResponse(List<Job> batch, String fullContent) {
        for (int i = 0; i < batch.size(); i++) {
            String marker = String.format("###RESPOSTA ID: %d###", i);
            int start = fullContent.indexOf(marker);
            if (start != -1) {
                int contentStart = start + marker.length();
                int nextMarker = fullContent.indexOf("###RESPOSTA ID:", contentStart);
                String analysis = (nextMarker != -1)
                        ? fullContent.substring(contentStart, nextMarker).trim()
                        : fullContent.substring(contentStart).trim();

                batch.get(i).setAiAnalysis(analysis);
                log.info("AI Analysis for '{}': {}", batch.get(i).getTitle(),
                        analysis.substring(0, Math.min(analysis.length(), 100)).replace("\n", " ") + "...");

                // Special check for removal filter in batch mode
                if (analysis.contains("Veredito: DESCARTAR")) {
                    batch.get(i).setAiAnalysis("REMOVE_JOB_IA_FILTER");
                }
            }
        }
    }

    private String truncateDescription(String description) {
        if (description == null)
            return "";
        return description.length() > 3000 ? description.substring(0, 3000) + "..." : description;
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        java.util.List<java.util.List<T>> partitions = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    private String buildPrompt(Job job) {
        String description = job.getDescription();
        if (description == null)
            description = "";

        // Truncate more aggressively to stay within token limits and reduce latency
        if (description.length() > 4000) {
            description = description.substring(0, 4000) + "...";
        }

        return String.format(
                """
                        Você é um recrutador técnico especialista em Java. Analise a vaga abaixo e determine se ela é um bom "match" para um desenvolvedor Java/Backend que busca vagas remotas ou B2B.

                        Título: %s
                        Empresa: %s
                        Localização: %s

                        Descrição:
                        %s

                        Responda estritamente no formato abaixo em PORTUGUÊS:

                        Veredito: [EXCELENTE / BOM / POUCO RELEVANTE / DESCARTAR]
                        Score IA: [0-100]
                        Stack: [Lista curta de tecnologias principais]
                        Nível: [Senior/Mid/Lead/Staff]
                        Match: [Explique em 2 frases por que esta vaga encaixa ou não no perfil de Java Developer]
                        Red Flags: [Qualquer ponto impeditivo ou negativo]
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
                new GeminiRequest.GenerationConfig(0.3, 8192));
    }

    private String extractContent(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            log.warn("Gemini returned no candidates or null response");
            return null;
        }

        var candidate = response.candidates().get(0);

        if (candidate.finishReason() != null && !candidate.finishReason().equals("STOP")) {
            log.warn("Gemini finish reason: {}", candidate.finishReason());
        }

        if (candidate.content() == null || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            log.warn("Gemini candidate has no content parts. Finish reason: {}. Full response: {}",
                    candidate.finishReason(), response);
            return null;
        }

        String text = candidate.content().parts().get(0).text();

        // Se a IA indicar para descartar, retornamos um marcador especial para o
        // service filtrar
        if (text != null && text.contains("Veredito: DESCARTAR")) {
            log.info("Job filtered out by AI Verdict");
            return "REMOVE_JOB_IA_FILTER";
        }

        return text;
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
        record Candidate(
                Content content,
                String finishReason) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            record Content(List<Part> parts) {
                @JsonIgnoreProperties(ignoreUnknown = true)
                record Part(String text) {
                }
            }
        }
    }
}
