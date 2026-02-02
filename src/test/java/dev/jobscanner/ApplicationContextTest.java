package dev.jobscanner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

  @MockitoBean
  private PipelineRunner pipelineRunner;

  @MockitoBean
  private ExitManager exitManager;

  @Test
  void contextLoads() {
  }
}
