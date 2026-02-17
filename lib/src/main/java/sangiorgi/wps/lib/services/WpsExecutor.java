package sangiorgi.wps.lib.services;

import android.os.Build;
import android.util.Log;
import com.topjohnwu.superuser.Shell;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import sangiorgi.wps.lib.WpsLibConfig;
import sangiorgi.wps.lib.commands.*;

/**
 * WPS command executor using Command pattern. Provides async operations, error handling, and
 * supports both standard WPS and Pixie Dust attacks.
 */
public class WpsExecutor implements AutoCloseable {

  private static final String TAG = "WpsExecutor";
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int PIXIE_DUST_TIMEOUT_SECONDS = 120;

  private final WpsLibConfig libConfig;
  private final ExecutorService executorService;
  private final CountDownLatch environmentReady = new CountDownLatch(1);
  private volatile boolean isCleanedUp = false;

  public WpsExecutor(WpsLibConfig libConfig) {
    this.libConfig = libConfig;
    // Use a fixed thread pool bounded by CPU cores to prevent OOM from unbounded thread creation
    int coreCount = Runtime.getRuntime().availableProcessors();
    this.executorService = Executors.newFixedThreadPool(Math.max(2, coreCount));
    initializeEnvironment();
  }

  private void initializeEnvironment() {
    setupWpsEnvironment();
  }

  private void setupWpsEnvironment() {
    String filesDir = libConfig.getFilesDir();
    String dataDir = libConfig.getDataDir();
    executorService.execute(
        () -> {
          try {
            Shell.Result result =
                Shell.cmd(
                        "chmod 755 " + filesDir + "/*",
                        "mkdir -p " + dataDir + "Sessions",
                        "chmod 755 " + dataDir + "Sessions")
                    .exec();

            if (!result.isSuccess()) {
              Log.e(TAG, "Failed to setup WPS environment: " + String.join("\n", result.getErr()));
            }
          } catch (Exception e) {
            Log.e(TAG, "Error setting up WPS environment", e);
          } finally {
            environmentReady.countDown();
          }
        });
  }

  /**
   * Wait for the environment setup to complete.
   * Call this before executing any WPS operations to avoid race conditions.
   *
   * @param timeout Maximum time to wait
   * @param unit Time unit
   * @return true if the environment is ready, false if timed out
   */
  public boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
    return environmentReady.await(timeout, unit);
  }

  /** Check if the environment setup has completed. */
  public boolean isReady() {
    return environmentReady.getCount() == 0;
  }

  /**
   * Execute WPS connection with automatic method selection
   *
   * @param bssid Target BSSID
   * @param pin WPS PIN
   * @return CompletableFuture with WpsResult
   */
  public CompletableFuture<WpsResult> executeWpsConnection(String bssid, String pin) {
    return executeWpsConnection(bssid, pin, shouldUseOldMethod());
  }

  /**
   * Execute WPS connection with specified method
   *
   * @param bssid Target BSSID
   * @param pin WPS PIN
   * @param useOldMethod Force old method (wpa_cli)
   * @return CompletableFuture with WpsResult
   */
  public CompletableFuture<WpsResult> executeWpsConnection(
      String bssid, String pin, boolean useOldMethod) {

    return CompletableFuture.supplyAsync(
        () -> {
          // Ensure environment is ready before executing
          try {
            environmentReady.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return createErrorResult(bssid, pin, "Interrupted waiting for environment setup");
          }

          if (useOldMethod) {
            CommandConfig config =
                new CommandConfig(libConfig.getFilesDir(), true, DEFAULT_TIMEOUT_SECONDS);
            CommandFactory factory = new CommandFactory(config);
            return executeOldMethodSync(factory, bssid, pin);
          } else {
            return executeNewMethodSync(bssid, pin);
          }
        },
        executorService);
  }

  private WpsResult executeOldMethodSync(CommandFactory factory, String bssid, String pin) {
    List<WpsCommand> commands = factory.createOldMethodCommands(bssid, pin);
    List<CommandResult> results = executeCommandsSync(commands);
    return new WpsResult(bssid, pin, results);
  }

  private WpsResult executeNewMethodSync(String bssid, String pin) {
    CommandConfig config =
        new CommandConfig(libConfig.getFilesDir(), false, DEFAULT_TIMEOUT_SECONDS);

    try (WpaSupplicantSession session = new WpaSupplicantSession(config, this::isWpsComplete)) {

      session.start();
      session.waitForReady();

      Log.d(TAG, "wpa_supplicant initialized, executing wpa_cli commands...");

      // Execute wpa_cli commands on the main shell while wpa_supplicant runs
      session.executeWpaCliCommands(bssid, pin);

      // Wait for WPS exchange to complete
      Log.d(TAG, "Waiting for WPS exchange to complete...");
      session.waitForCompletion(DEFAULT_TIMEOUT_SECONDS);

      // Check if the session output indicates success
      List<String> outputLines = session.getOutput();
      boolean hasSuccessIndicator = checkForSuccessIndicators(outputLines);

      // Build the supplicant result with collected output
      // Only mark as success if we found actual WPS success indicators
      CommandResult supplicantResult =
          new CommandResult(
              hasSuccessIndicator, outputLines, null, WpsCommand.CommandType.WPA_SUPPLICANT);

      List<CommandResult> results = new ArrayList<>();
      results.add(supplicantResult);

      return new WpsResult(bssid, pin, results);

    } catch (InterruptedException e) {
      Log.e(TAG, "Command interrupted", e);
      Thread.currentThread().interrupt();
      return createErrorResult(bssid, pin, "Command interrupted: " + e.getMessage());
    } catch (Exception e) {
      Log.e(TAG, "Command execution failed", e);
      return createErrorResult(bssid, pin, "Execution failed: " + e.getMessage());
    }
  }

  /**
   * Check if WPS exchange is complete based on wpa_supplicant output. Detects success (connected,
   * credentials received) or failure (NACK, timeout, fail).
   */
  private boolean isWpsComplete(String line) {
    String lower = line.toLowerCase(Locale.ROOT);

    // Success indicators
    if (lower.contains("wps-success")
        || lower.contains("wps_success")
        || lower.contains("network key")
        || lower.contains("ctrl-event-connected")
        || lower.contains("key negotiation completed")) {
      return true;
    }

    // Failure indicators (M4/M6 NACK, timeout, etc.)
    return lower.contains("wps-fail")
        || lower.contains("wsc_nack")
        || lower.contains("wps_nack")
        || lower.contains("eap failure")
        || lower.contains("wps-timeout")
        || lower.contains("wps_timeout")
        || lower.contains("m2d")
        || lower.contains("authentication failed")
        || lower.contains("4-way handshake failed");
  }

  /**
   * Check if the output contains WPS success indicators.
   *
   * @param outputLines Lines of output from wpa_supplicant
   * @return true if success indicators are found
   */
  private boolean checkForSuccessIndicators(List<String> outputLines) {
    if (outputLines == null || outputLines.isEmpty()) {
      return false;
    }

    for (String line : outputLines) {
      if (line == null) continue;
      String lower = line.toLowerCase(Locale.ROOT);

      // Check for specific WPS success indicators
      if (lower.contains("wps-success")
          || lower.contains("wps_success")
          || lower.contains("ctrl-event-connected")
          || lower.contains("key negotiation completed")
          || lower.contains("network key")) {
        Log.d(TAG, "Found success indicator in: " + line);
        return true;
      }
    }
    return false;
  }

  /**
   * Execute Pixie Dust attack
   *
   * @param bssid Target BSSID
   * @return CompletableFuture with WpsResult containing PIN and password if successful
   */
  public CompletableFuture<WpsResult> executePixieDust(String bssid) {
    return CompletableFuture.supplyAsync(
        () -> {
          // Ensure environment is ready before executing
          try {
            environmentReady.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return createErrorResult(bssid, "N/A", "Interrupted waiting for environment setup");
          }

          PixieDustExecutor pixieExecutor = new PixieDustExecutor(libConfig, this);

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

  private List<CommandResult> executeCommandsSync(List<WpsCommand> commands) {
    List<CommandResult> results = new ArrayList<>();

    for (WpsCommand command : commands) {
      try {
        CommandResult result = command.execute().get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        results.add(result);
      } catch (Exception e) {
        Log.e(TAG, "Command execution failed: " + command.getCommandType(), e);
        results.add(
            new CommandResult(
                false,
                null,
                List.of("Execution failed: " + e.getMessage()),
                command.getCommandType()));
      }
    }

    return results;
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
      Shell.cmd("pkill -f wpa_supplicant", "pkill -f wpa_cli", "pkill -f pixiedust").exec();
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

  /**
   * Determine if old method should be used based on Android version
   *
   * @return true if Android version is below P (API 28)
   */
  private static boolean shouldUseOldMethod() {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.P;
  }
}
