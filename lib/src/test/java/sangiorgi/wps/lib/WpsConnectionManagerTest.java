package sangiorgi.wps.lib;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for WpsConnectionManager.
 *
 * <p>Note: WpsConnectionManager requires Android Context and Shell, so only
 * construction and basic state tests can be run in unit tests. Full integration
 * tests require an instrumented test environment.
 */
public class WpsConnectionManagerTest {

  @Test
  public void testManagerRequiresNonNullContext() {
    try {
      WpsLibConfig config = new WpsLibConfig("/data/data/test/");
      new WpsConnectionManager(null, config);
      fail("Should throw when context is null");
    } catch (NullPointerException e) {
      // Expected - context.getApplicationContext() throws NPE
    }
  }

  @Test
  public void testWpsLibConfigIntegration() {
    // Verify config creates correct paths
    WpsLibConfig config = new WpsLibConfig("/data/data/com.example/");
    assertEquals("/data/data/com.example/files", config.getFilesDir());
    assertEquals("/data/data/com.example/Sessions", config.getSessionsDir());
  }

  @Test
  public void testWpsLibConfigWithVariousDataDirs() {
    String[] dataDirs = {
      "/data/data/com.example.app/",
      "/data/data/com.example.app",
      "/data/user/0/com.test.app/",
      "/sdcard/Android/data/com.example/"
    };

    for (String dir : dataDirs) {
      WpsLibConfig config = new WpsLibConfig(dir);
      assertNotNull(config.getDataDir());
      assertNotNull(config.getFilesDir());
      assertNotNull(config.getSessionsDir());
      assertTrue(config.getDataDir().endsWith("/"));
    }
  }
}
