package dev.jobscanner.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jobscanner.model.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of JobEnhancer that uses OpenRouter API.
 * OpenRouter allows access to many models (Gemini 2.0, Claude, Llama) via an
 * OpenAI-compatible API.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openrouter")
public class OpenRouterJobEnhancer implements JobEnhancer {

  private final WebClient webClient;
  private final String apiKey;
  private final String model;

  public OpenRouterJobEnhancer(
      @Value("${app.ai.openrouter.api-key}") String apiKey,
      @Value("${app.ai.openrouter.model:google/gemini-2.0-flash-001}") String model,
      @Value("${app.ai.openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl) {

    this.apiKey = apiKey;
    this.model = model;
    this.webClient = WebClient.builder()
        .baseUrl(Objects.requireNonNull(baseUrl))
        .defaultHeader("Authorization", "Bearer " + apiKey)
        .defaultHeader("Content-Type", "application/json")
        .defaultHeader("HTTP-Referer", "https://github.com/samdev/ats_scrapper") // Required by some models on
                                                                                 // OpenRouter
        .defaultHeader("X-Title", "Job Scanner AI")
        .build();

    if (apiKey == null || apiKey.isBlank()) {
      log.warn("OpenRouter API Key is missing! AI enhancement will fail.");
    } else {
      log.info("OpenRouter AI Enhancement enabled with model: {}", this.model);
    }
  }

  @Override
  public Mono<Job> enhance(Job job) {
    if (job.getDescription() == null || job.getDescription().isBlank() || apiKey == null || apiKey.isBlank()) {
      return Mono.just(job);
    }

    String prompt = buildPrompt(job);
    OpenRouterRequest request = buildRequest(prompt);

    return webClient.post()
        .uri("/chat/completions")
        .bodyValue(Objects.requireNonNull(request))
        .retrieve()
        .bodyToMono(OpenRouterResponse.class)
        .timeout(Duration.ofSeconds(60))
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)).filter(this::isRetryableError))
        .map(response -> {
          String analysis = extractContent(response);
          if (analysis != null && !analysis.isBlank()) {
            job.setAiAnalysis(analysis);
          }
          return job;
        })
        .onErrorResume(e -> {
          log.warn("OpenRouter AI failure for '{}': {}", job.getTitle(), e.getMessage());
          return Mono.just(job);
        });
  }

  @Override
  public Mono<List<Job>> enhanceAll(List<Job> jobs) {
    if (jobs.isEmpty())
      return Mono.just(jobs);

    log.info("Starting OpenRouter AI enhancement for {} jobs...", jobs.size());

    // OpenRouter usually has better limits than Groq Free, but we still batch
    int batchSize = 15;

    return Flux.fromIterable(partitionList(jobs, batchSize))
        .delayElements(Duration.ofSeconds(1)) // Small delay to be safe
        .flatMap(this::enhanceBatch, 1)
        .flatMapIterable(list -> list)
        .collectList();
  }

  private Mono<List<Job>> enhanceBatch(List<Job> batch) {
    if (batch.isEmpty())
      return Mono.just(batch);

    log.info("OpenRouter processing batch of {} jobs...", batch.size());

    StringBuilder compositePrompt = new StringBuilder();
    compositePrompt.append(
        """
            Você é um recrutador técnico especializado em encontrar vagas de Java para desenvolvedores brasileiros e da América Latina (LATAM).
            Sua missão é analisar se a vaga é realmente para desenvolvedores Java (JVM) e se aceita candidatos remotos do Brasil ou LATAM.

            Critérios de Descarte (Veredito: DESCARTAR):
            1. Vagas que NÃO são de Java/JVM (ex: apenas Node, Go, Ruby, etc).
            2. Vagas que exigem residência obrigatória em países específicos (ex: 'Must live in USA', 'Only UK candidates').
            3. Vagas de 'Senior' que na verdade pedem apenas 2-3 anos de experiência.

            Critérios de Sucesso:
            - Vagas 'Anywhere', 'Remote Global', ou que mencionam 'Brazil'/'LATAM' devem receber Score IA alto.

            """);

    for (int i = 0; i < batch.size(); i++) {
      Job job = batch.get(i);
      compositePrompt.append(String.format("###RESPOSTA ID: %d###%n", i));
      compositePrompt.append(String.format("Vaga: %s @ %s%nDescrição: %s%n%n",
          job.getTitle(), job.getCompany(), truncateDescription(job.getDescription())));
    }

    compositePrompt.append("""
        Para CADA VAGA, use EXATAMENTE este formato:
        ###RESPOSTA ID: [ID]###
        Veredito: [EXCELENTE / BOM / POUCO RELEVANTE / DESCARTAR]
        Score IA: [0-100]
        Stack: [Principais tecnologias]
        Match: [Explique em 1 frase por que brasileiros/LATAM podem ou não se candidatar]
        """);

    OpenRouterRequest request = buildRequest(compositePrompt.toString());

    return webClient.post()
        .uri("/chat/completions")
        .bodyValue(Objects.requireNonNull(request))
        .retrieve()
        .onStatus(HttpStatusCode::isError,
            clientResponse -> clientResponse.bodyToMono(String.class)
                .flatMap(errorBody -> {
                  log.error("OpenRouter API Error: {}", errorBody);
                  return Mono.error(new RuntimeException("OpenRouter error: " + errorBody));
                }))
        .bodyToMono(OpenRouterResponse.class)
        .map(response -> {
          String content = extractContent(response);
          if (content != null) {
            parseBatchResponse(batch, content);
          }
          return batch;
        })
        .onErrorResume(e -> {
          log.error("OpenRouter batch failed: {}", e.getMessage());
          return Mono.just(batch);
        });
  }

  private void parseBatchResponse(List<Job> batch, String fullContent) {
    for (int i = 0; i < batch.size(); i++) {
      String marker = String.format("###RESPOSTA ID: %d###", i);
      int start = fullContent.indexOf(marker);
      if (start != -1) {
        int contentStart = start + marker.length();
        int nextIndex = fullContent.indexOf("###RESPOSTA ID:", contentStart);
        String analysis = (nextIndex != -1)
            ? fullContent.substring(contentStart, nextIndex).trim()
            : fullContent.substring(contentStart).trim();

        batch.get(i).setAiAnalysis(analysis);
        if (analysis.contains("Veredito: DESCARTAR")) {
          batch.get(i).setAiAnalysis("REMOVE_JOB_IA_FILTER");
        }
      }
    }
  }

  private String buildPrompt(Job job) {
    return String.format(
        "Analise esta vaga de Java: %s na %s. Descrição: %s. Responda em Português com Veredito, Score e Match.",
        job.getTitle(), job.getCompany(), truncateDescription(job.getDescription()));
  }

  private OpenRouterRequest buildRequest(String prompt) {
    return new OpenRouterRequest(model, List.of(new Message("user", prompt)));
  }

  private String extractContent(OpenRouterResponse response) {
    if (response != null && response.choices() != null && !response.choices().isEmpty()) {
      return response.choices().get(0).message().content();
    }
    return null;
  }

  private String truncateDescription(String description) {
    if (description == null)
      return "";
    return description.length() > 4000 ? description.substring(0, 4000) + "..." : description;
  }

  private <T> List<List<T>> partitionList(List<T> list, int size) {
    java.util.List<java.util.List<T>> partitions = new java.util.ArrayList<>();
    for (int i = 0; i < list.size(); i += size) {
      partitions.add(list.subList(i, Math.min(i + size, list.size())));
    }
    return partitions;
  }

  private boolean isRetryableError(Throwable e) {
    String message = e.getMessage();
    return message != null && (message.contains("429") || message.contains("500") || message.contains("503"));
  }

  @Override
  public boolean isEnabled() {
    return apiKey != null && !apiKey.isBlank();
  }

  // OpenAI Compatible DTOs
  record OpenRouterRequest(String model, List<Message> messages) {
  }

  record Message(String role, String content) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record OpenRouterResponse(List<Choice> choices) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }
  }
}
