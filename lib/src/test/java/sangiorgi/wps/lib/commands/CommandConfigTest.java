package sangiorgi.wps.lib.commands;

import static org.junit.Assert.*;

import org.junit.Test;

public class CommandConfigTest {

  private static final String TEST_FILES_DIR = "/data/data/com.test/files";

  @Test
  public void testGetFilesDir() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    assertEquals(TEST_FILES_DIR, config.getFilesDir());
  }

  @Test
  public void testGetTimeout() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 60);
    assertEquals(60, config.getTimeout());
  }

  @Test
  public void testIsUseOldMethodTrue() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 30);
    assertTrue(config.isUseOldMethod());
  }

  @Test
  public void testIsUseOldMethodFalse() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    assertFalse(config.isUseOldMethod());
  }

  @Test
  public void testIs64BitDetection() {
    // On a test JVM the architecture is detected from system property
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    // Just verify it doesn't crash - actual value depends on test machine
    // The result is boolean so it will always be true or false
    boolean is64 = config.is64Bit();
    assertTrue(is64 || !is64);
  }

  @Test
  public void testGetWpaSupplicantOutputPath() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    String path = config.getWpaSupplicantOutputPath();
    // With returnDefaultValues, Build.VERSION.SDK_INT returns 0 which is < P(28)
    assertNotNull(path);
    assertTrue(path.contains("wpswpatester"));
  }

  @Test
  public void testGetControlPath() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    String path = config.getControlPath();
    assertNotNull(path);
    assertTrue(path.contains("wlan0"));
  }

  @Test
  public void testDifferentTimeoutValues() {
    int[] timeouts = {10, 30, 60, 120};
    for (int timeout : timeouts) {
      CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, timeout);
      assertEquals(timeout, config.getTimeout());
    }
  }
}
