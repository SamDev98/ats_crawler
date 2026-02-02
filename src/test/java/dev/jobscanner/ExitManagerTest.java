package dev.jobscanner;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExitManagerTest {

  @Test
  void testExitInTestEnvironment() {
    ExitManager exitManager = new ExitManager();
    // In test environment, this should not call System.exit()
    exitManager.exit(0);
    exitManager.exit(1);
    assertTrue(exitManager.isTest());
  }
}
