package dev.jobscanner.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * Implementation of JobEnhancer that uses Groq Cloud (Llama 3/Mixtral) API.
 * Groq is known for its extreme speed and generous free tier.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "groq")
public class GroqJobEnhancer implements JobEnhancer {

  private static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1";
  private static final String CHAT_PATH = "/chat/completions";

  private final WebClient webClient;
  private final String apiKey;
  private final String model;

  public GroqJobEnhancer(
      @Value("${app.ai.groq.api-key}") String apiKey,
      @Value("${app.ai.groq.model:llama-3.3-70b-versatile}") String model) {

    this.apiKey = apiKey;
    this.model = model;
    this.webClient = WebClient.builder()
        .baseUrl(GROQ_BASE_URL)
        .defaultHeader("Authorization", "Bearer " + apiKey)
        .defaultHeader("Content-Type", "application/json")
        .build();

    if (apiKey == null || apiKey.isBlank()) {
      log.warn("Groq API Key is missing! Groq AI enhancement will fail.");
    } else {
      log.info("Groq AI Enhancement enabled with model: {}", this.model);
    }
  }

  @Override
  public Mono<Job> enhance(Job job) {
    if (job.getDescription() == null || job.getDescription().isBlank() || apiKey == null || apiKey.isBlank()) {
      return Mono.just(job);
    }

    String prompt = buildPrompt(job);
    GroqRequest request = buildRequest(prompt);

    return webClient.post()
        .uri(CHAT_PATH)
        .bodyValue(request)
        .retrieve()
        .bodyToMono(GroqResponse.class)
        .timeout(Duration.ofSeconds(30))
        .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)).filter(this::isRetryableError))
        .map(response -> {
          String analysis = extractContent(response);
          if (analysis != null && !analysis.isBlank()) {
            job.setAiAnalysis(analysis);
          }
          return job;
        })
        .onErrorResume(e -> {
          log.warn("Groq AI failure for '{}': {}", job.getTitle(), e.getMessage());
          return Mono.just(job);
        });
  }

  @Override
  public Mono<List<Job>> enhanceAll(List<Job> jobs) {
    if (jobs.isEmpty())
      return Mono.just(jobs);

    log.info("Starting Groq AI enhancement for {} jobs...", jobs.size());

    // Limits for llama-3.3-70b-versatile (Free Tier): 
    // RPM=30, RPD=1K, TPM=12K, TPD=100K.
    // Batch size reduced to 10 to stay well within TPM (Tokens Per Minute) 
    // as job descriptions can be large.
    int batchSize = 10;

    return Flux.fromIterable(partitionList(jobs, batchSize))
        .delayElements(Duration.ofSeconds(3)) // 3s delay to respect both RPM and TPM safely
        .flatMap(this::enhanceBatch, 1)
        .flatMapIterable(list -> list)
        .collectList();
  }

  private Mono<List<Job>> enhanceBatch(List<Job> batch) {
    if (batch.isEmpty())
      return Mono.just(batch);

    log.info("Groq processing batch of {} jobs...", batch.size());

    StringBuilder compositePrompt = new StringBuilder();
    compositePrompt.append("Você é um recrutador técnico Java. Analise as vagas e responda para cada uma.\n\n");

    for (int i = 0; i < batch.size(); i++) {
      Job job = batch.get(i);
      compositePrompt.append(String.format("###RESPOSTA ID: %d###\n", i));
      compositePrompt.append(String.format("Vaga: %s @ %s\nDescrição: %s\n\n",
          job.getTitle(), job.getCompany(), truncateDescription(job.getDescription())));
    }

    compositePrompt.append("""
        Para CADA VAGA, use EXATAMENTE este formato:
        ###RESPOSTA ID: [ID]###
        Veredito: [EXCELENTE / BOM / POUCO RELEVANTE / DESCARTAR]
        Score IA: [0-100]
        Stack: [Tags]
        Match: [2 frases curtas]
        """);

    GroqRequest request = buildRequest(compositePrompt.toString());

    return webClient.post()
        .uri(CHAT_PATH)
        .bodyValue(request)
        .retrieve()
        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
            clientResponse -> clientResponse.bodyToMono(String.class)
                .flatMap(errorBody -> {
                  log.error("Groq API Error Body: {}", errorBody);
                  return Mono.error(new RuntimeException("Groq API error: " + errorBody));
                }))
        .bodyToMono(GroqResponse.class)
        .map(response -> {
          String content = extractContent(response);
          if (content != null) {
            parseBatchResponse(batch, content);
          }
          return batch;
        })
        .onErrorResume(e -> {
          log.error("Groq batch failed: {}", e.getMessage());
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
    return String.format("Analise esta vaga de Java: %s na %s. Descrição: %s. Responda com Veredito, Score e Match.",
        job.getTitle(), job.getCompany(), truncateDescription(job.getDescription()));
  }

  private GroqRequest buildRequest(String prompt) {
    // max_tokens is for the RESPONSE. Reducing it to leave more room for input.
    return new GroqRequest(model, List.of(new GroqRequest.Message("user", prompt)), 0.2, 2048);
  }

  private String extractContent(GroqResponse response) {
    if (response != null && response.choices() != null && !response.choices().isEmpty()) {
      return response.choices().get(0).message().content();
    }
    return null;
  }

  private String truncateDescription(String desc) {
    if (desc == null)
      return "";
    // Truncating to 1000 chars to ensure a batch of 8 fits in 8k context
    return desc.length() > 1000 ? desc.substring(0, 1000) + "..." : desc;
  }

  private boolean isRetryableError(Throwable e) {
    String msg = e.getMessage();
    return msg != null && (msg.contains("429") || msg.contains("500") || msg.contains("503"));
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

  // DTOs
  record GroqRequest(String model, List<Message> messages, double temperature, int max_tokens) {
    record Message(String role, String content) {
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record GroqResponse(List<Choice> choices) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
      @JsonIgnoreProperties(ignoreUnknown = true)
      record Message(String content) {
      }
    }
  }
}
