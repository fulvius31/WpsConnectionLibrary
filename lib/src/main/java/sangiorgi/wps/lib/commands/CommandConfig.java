package sangiorgi.wps.lib.commands;

import android.os.Build;
import android.util.Log;

/** Configuration for WPS commands. Centralizes all configuration logic in one place. */
public class CommandConfig {

  private static final String TAG = "CommandConfig";
  private final String filesDir;
  private final boolean useOldMethod;
  private final int timeout;
  private final boolean is64Bit;

  public CommandConfig(String filesDir, boolean useOldMethod, int timeout) {
    this.filesDir = filesDir;
    this.useOldMethod = useOldMethod;
    this.timeout = timeout;
    this.is64Bit = is64BitArchitecture();
  }

  private boolean is64BitArchitecture() {
    String arch = System.getProperty("os.arch");
    boolean is64 = arch != null && arch.contains("64");
    Log.d(TAG, "Architecture: " + arch + " (is64Bit: " + is64 + ")");
    return is64;
  }

  public String getFilesDir() {
    return filesDir;
  }

  public String getWpaSupplicantOutputPath() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ? "-K -O/data/vendor/wifi/wpa/wpswpatester/"
        : "-K -O/data/misc/wifi/wpswpatester/";
  }

  public String getControlPath() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ? "-g/data/vendor/wifi/wpa/wpswpatester/wlan0"
        : "-g/data/misc/wifi/wpswpatester/wlan0";
  }

  public boolean isUseOldMethod() {
    return useOldMethod;
  }

  public int getTimeout() {
    return timeout;
  }

  public boolean is64Bit() {
    return is64Bit;
  }
}
