package sangiorgi.wps.lib;

/**
 * Configuration for the WPS Connection Library.
 * Replaces BuildConfig.DATA_DIR with a caller-provided data directory.
 *
 * <p>Example usage:
 * <pre>
 *   WpsLibConfig config = new WpsLibConfig("/data/data/com.example.app/");
 * </pre>
 */
public class WpsLibConfig {

  private final String dataDir;

  /**
   * Create a new library configuration.
   *
   * @param dataDir The application's data directory path, e.g. "/data/data/com.example.app/".
   *                Must end with a trailing slash.
   */
  public WpsLibConfig(String dataDir) {
    if (dataDir == null || dataDir.isEmpty()) {
      throw new IllegalArgumentException("dataDir must not be null or empty");
    }
    this.dataDir = dataDir.endsWith("/") ? dataDir : dataDir + "/";
  }

  /** Get the base data directory (e.g. "/data/data/com.example.app/") */
  public String getDataDir() {
    return dataDir;
  }

  /** Get the files directory (e.g. "/data/data/com.example.app/files") */
  public String getFilesDir() {
    return dataDir + "files";
  }

  /** Get the sessions directory (e.g. "/data/data/com.example.app/Sessions") */
  public String getSessionsDir() {
    return dataDir + "Sessions";
  }
}
