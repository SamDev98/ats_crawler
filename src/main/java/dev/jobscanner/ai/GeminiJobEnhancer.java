package dev.jobscanner.ai;

import dev.jobscanner.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of JobEnhancer that uses Google Gemini via Spring AI.
 */
@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class GeminiJobEnhancer implements JobEnhancer {

  private static final Logger logger = LoggerFactory.getLogger(GeminiJobEnhancer.class);
  private final ChatClient chatClient;

  public GeminiJobEnhancer(ChatClient.Builder chatClientBuilder) {
    this.chatClient = chatClientBuilder
        .defaultSystem("Você é um especialista em recrutamento técnico para desenvolvedores Java. " +
            "Sua tarefa é analisar descrições de vagas e extrair informações cruciais para um desenvolvedor sênior.")
        .build();
  }

  @Override
  public Mono<Job> enhance(Job job) {
    if (job.getDescription() == null || job.getDescription().isBlank()) {
      return Mono.just(job);
    }

    String prompt = String.format(
        "Analise a seguinte vaga: \n\nTítulo: %s\nDescrição: %s\n\n" +
            "Responda em PORTUGUÊS com um resumo de 3 pontos: \n" +
            "1. Principais tecnologias (Stack).\n" +
            "2. É uma vaga sênior/especialista? (Sim/Não).\n" +
            "3. Algum 'red flag' ou benefício excepcional mencionado?\n" +
            "Seja conciso.",
        job.getTitle(), job.getDescription());

    return Mono.fromCallable(() -> {
      try {
        logger.info("Enviando vaga para análise do Gemini: {}", job.getTitle());
        String analysis = chatClient.prompt(prompt).call().content();
        job.setAiAnalysis(analysis);
        return job;
      } catch (Exception e) {
        logger.error("Erro ao chamar o Gemini para a vaga {}: {}", job.getTitle(), e.getMessage());
        return job;
      }
    });
  }

  @Override
  public Mono<List<Job>> enhanceAll(List<Job> jobs) {
    List<Mono<Job>> enhancedMonos = jobs.stream()
        .map(this::enhance)
        .collect(Collectors.toList());

    return Mono.zip(enhancedMonos, objects -> List.of(objects).stream()
        .map(obj -> (Job) obj)
        .collect(Collectors.toList()));
  }
}
