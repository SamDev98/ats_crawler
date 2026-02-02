package dev.jobscanner.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import dev.jobscanner.PipelineRunner;
import dev.jobscanner.ExitManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConfigPropertiesTest {

  @MockitoBean
  private PipelineRunner pipelineRunner;

  @MockitoBean
  private ExitManager exitManager;

  @Autowired
  private SourcesConfig sourcesConfig;

  @Autowired
  private RulesConfig rulesConfig;

  @Autowired
  private ScoringConfig scoringConfig;

  @Test
  void shouldLoadSourcesConfig() {
    assertThat(sourcesConfig).isNotNull();
    // Just verify one field to ensure it's not empty
    assertThat(sourcesConfig.getAshby()).isNotNull();
  }

  @Test
  void shouldLoadRulesConfig() {
    assertThat(rulesConfig).isNotNull();
    assertThat(rulesConfig.getJavaTerms()).isNotNull();
  }

  @Test
  void shouldLoadScoringConfig() {
    assertThat(scoringConfig).isNotNull();
    assertThat(scoringConfig.getThreshold()).isGreaterThan(0);
  }
}
