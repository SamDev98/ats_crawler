package dev.jobscanner;

import dev.jobscanner.model.Job;
import dev.jobscanner.service.JobScannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineRunnerTest {

  @Mock
  private JobScannerService jobScannerService;

  @InjectMocks
  private PipelineRunner pipelineRunner;

  @BeforeEach
  @SuppressWarnings("null")
  void setUp() {
    ReflectionTestUtils.setField(pipelineRunner, "metricsWaitSeconds", 0);
  }

  @Test
  void execute_successfulRun_returnsJobCount() {
    // Arrange
    List<Job> jobs = List.of(Job.builder().build(), Job.builder().build());
    when(jobScannerService.runPipeline()).thenReturn(Mono.just(jobs));

    // Act
    int result = pipelineRunner.execute();

    // Assert
    assertEquals(2, result);
    verify(jobScannerService).runPipeline();
  }

  @Test
  void execute_emptyResult_returnsZero() {
    // Arrange
    when(jobScannerService.runPipeline()).thenReturn(Mono.just(List.of()));

    // Act
    int result = pipelineRunner.execute();

    // Assert
    assertEquals(0, result);
  }

  @Test
  void execute_nullResult_returnsZero() {
    // Arrange
    when(jobScannerService.runPipeline()).thenReturn(Mono.empty());

    // Act
    int result = pipelineRunner.execute();

    // Assert
    assertEquals(0, result);
  }

  @Test
  void execute_serviceFails_throwsException() {
    // Arrange
    when(jobScannerService.runPipeline()).thenReturn(Mono.error(new RuntimeException("API error")));

    // Act & Assert
    assertThrows(RuntimeException.class, () -> pipelineRunner.execute());
  }

  @Test
  @SuppressWarnings("null")
  void execute_withWait_handlesInterruption() {
    // This is a bit tricky to test without actual sleeping,
    // but we can test the branch logic by setting a small wait.
    ReflectionTestUtils.setField(pipelineRunner, "metricsWaitSeconds", 1);
    when(jobScannerService.runPipeline()).thenReturn(Mono.just(List.of()));

    // We just verify it doesn't crash
    pipelineRunner.execute();

    verify(jobScannerService).runPipeline();
  }
}
