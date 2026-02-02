package dev.jobscanner;

import org.springframework.stereotype.Component;

/**
 * Manages application exit.
 * Separated to allow mocking in tests and avoid killing the test runner.
 */
@Component
public class ExitManager {
  public void exit(int status) {
    if (!isTest()) {
      System.exit(status);
    }
  }

  protected boolean isTest() {
    String cp = System.getProperty("java.class.path", "");
    return cp.contains("junit") || cp.contains("surefire") || cp.contains("intellij");
  }
}
