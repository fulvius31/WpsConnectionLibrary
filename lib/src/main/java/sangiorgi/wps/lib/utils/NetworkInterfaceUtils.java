package sangiorgi.wps.lib.utils;

import android.os.Build;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/** Utility class for network interface operations */
public final class NetworkInterfaceUtils {
  private static final String TAG = "NetworkInterfaceUtils";
  private static final int PROCESS_TIMEOUT_SECONDS = 5;

  private NetworkInterfaceUtils() {
    // Utility class - prevent instantiation
  }

  /** Enable or disable WiFi through system commands */
  public static void setWifiEnabled(boolean enabled) {
    Process process = null;
    try {
      process = Runtime.getRuntime().exec("su");
      try (OutputStream outputStream = process.getOutputStream()) {
        String command = "svc wifi " + (enabled ? "enable" : "disable") + "\n";
        outputStream.write(command.getBytes());
        outputStream.flush();
      }
      waitForProcess(process, PROCESS_TIMEOUT_SECONDS);
    } catch (IOException e) {
      Log.e(TAG, "Error setting WiFi state", e);
    } catch (InterruptedException e) {
      Log.e(TAG, "WiFi state change interrupted", e);
      Thread.currentThread().interrupt();
    } finally {
      if (process != null) {
        process.destroy();
      }
    }
  }

  /** Check if wlan0 interface is present */
  public static boolean isWlan0Present() {
    Process process = null;
    try {
      process = Runtime.getRuntime().exec("ip link");
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.contains("wlan0")) {
            return true;
          }
        }
      }
      waitForProcess(process, PROCESS_TIMEOUT_SECONDS);
    } catch (IOException e) {
      Log.e(TAG, "Error checking wlan0 presence", e);
    } catch (InterruptedException e) {
      Log.e(TAG, "wlan0 check interrupted", e);
      Thread.currentThread().interrupt();
    } finally {
      if (process != null) {
        process.destroy();
      }
    }
    return false;
  }

  /**
   * Wait for a process to complete with timeout. Handles API level differences for
   * Process.waitFor(timeout, unit) which requires API 26+.
   */
  private static void waitForProcess(Process process, int timeoutSeconds)
      throws InterruptedException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
      if (!completed) {
        Log.w(TAG, "Process timed out, destroying forcibly");
        process.destroyForcibly();
      }
    } else {
      // For API < 26, use a simple wait with manual timeout
      Thread waitThread =
          new Thread(
              () -> {
                try {
                  process.waitFor();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
      waitThread.start();
      waitThread.join(timeoutSeconds * 1000L);
      if (waitThread.isAlive()) {
        Log.w(TAG, "Process timed out, destroying");
        process.destroy();
        waitThread.interrupt();
      }
    }
  }
}
