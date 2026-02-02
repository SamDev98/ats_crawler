package dev.jobscanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobScannerApplicationTests {

  @Mock
  private PipelineRunner pipelineRunner;

  @Mock
  private ExitManager exitManager;

  @Test
  void shouldRunPipelineAndExitSuccessfully() {
    JobScannerApplication app = new JobScannerApplication(pipelineRunner, exitManager);

    when(pipelineRunner.execute()).thenReturn(1);

    app.run();

    verify(pipelineRunner).execute();
    verify(exitManager).exit(0);
  }

  @Test
  void shouldHandleExceptionAndExitWithError() {
    JobScannerApplication app = new JobScannerApplication(pipelineRunner, exitManager);

    when(pipelineRunner.execute()).thenThrow(new RuntimeException("Fatal"));

    app.run();

    verify(exitManager).exit(1);
  }
}
