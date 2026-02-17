package sangiorgi.wps.lib;

import static org.junit.Assert.*;

import org.junit.Test;

public class WpsLibConfigTest {

  @Test
  public void testGetDataDirWithTrailingSlash() {
    WpsLibConfig config = new WpsLibConfig("/data/data/com.example.app/");
    assertEquals("/data/data/com.example.app/", config.getDataDir());
  }

  @Test
  public void testGetDataDirWithoutTrailingSlash() {
    WpsLibConfig config = new WpsLibConfig("/data/data/com.example.app");
    assertEquals("/data/data/com.example.app/", config.getDataDir());
  }

  @Test
  public void testGetFilesDir() {
    WpsLibConfig config = new WpsLibConfig("/data/data/com.example.app/");
    assertEquals("/data/data/com.example.app/files", config.getFilesDir());
  }

  @Test
  public void testGetSessionsDir() {
    WpsLibConfig config = new WpsLibConfig("/data/data/com.example.app/");
    assertEquals("/data/data/com.example.app/Sessions", config.getSessionsDir());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNullDataDirThrows() {
    new WpsLibConfig(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptyDataDirThrows() {
    new WpsLibConfig("");
  }

  @Test
  public void testTrailingSlashIdempotent() {
    WpsLibConfig config1 = new WpsLibConfig("/data/");
    WpsLibConfig config2 = new WpsLibConfig("/data");
    assertEquals(config1.getDataDir(), config2.getDataDir());
    assertEquals(config1.getFilesDir(), config2.getFilesDir());
    assertEquals(config1.getSessionsDir(), config2.getSessionsDir());
  }
}
