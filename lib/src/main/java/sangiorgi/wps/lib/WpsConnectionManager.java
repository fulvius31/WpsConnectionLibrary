package sangiorgi.wps.lib;

import android.content.Context;
import android.util.Log;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import sangiorgi.wps.lib.assets.WpaToolsInitializer;
import sangiorgi.wps.lib.handlers.ConnectionHandler;
import sangiorgi.wps.lib.models.NetworkToTest;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import sangiorgi.wps.lib.services.ConnectionService;
import sangiorgi.wps.lib.services.PinDatabaseService;
import sangiorgi.wps.lib.services.PinValidationService;
import sangiorgi.wps.lib.services.WpsExecutor;
import sangiorgi.wps.lib.utils.PinUtils;

/**
 * Main entry point for the WPS Connection Library.
 *
 * <p>For best performance, create this once (e.g. as a singleton) and reuse it across connections.
 * The internal executor maintains a warm thread pool and pre-configured environment.
 *
 * <p>Usage:
 * <pre>
 *   WpsLibConfig config = new WpsLibConfig("/data/data/com.example.app/");
 *   WpsConnectionManager manager = new WpsConnectionManager(context, config);
 *   manager.initialize();
 *   manager.testPins("AA:BB:CC:DD:EE:FF", "MyNetwork", pins, callback);
 * </pre>
 */
public class WpsConnectionManager {

  private static final String TAG = "WpsConnectionManager";

  private final Context context;
  private final WpsLibConfig libConfig;
  private final WpsExecutor executor;
  private final PinValidationService pinValidator;
  private final PinDatabaseService pinDatabaseService;
  private final ConnectionHandler.Factory connectionHandlerFactory;
  private final ConnectionService.Factory connectionServiceFactory;

  private ConnectionService activeService;

  /**
   * Create a new WpsConnectionManager.
   *
   * @param context Application context
   * @param libConfig Library configuration with data directory
   */
  public WpsConnectionManager(Context context, WpsLibConfig libConfig) {
    this.context = context.getApplicationContext();
    this.libConfig = libConfig;
    this.executor = new WpsExecutor(libConfig);

    PinUtils pinUtils = new PinUtils(this.context);
    this.pinValidator = new PinValidationService(pinUtils);
    this.pinDatabaseService = new PinDatabaseService(this.context);

    this.connectionHandlerFactory =
        (networkToTest, callback, stateManager, useOldMethod) ->
            new ConnectionHandler(
                WpsConnectionManager.this.context,
                executor,
                pinValidator,
                networkToTest,
                callback,
                stateManager,
                useOldMethod);

    this.connectionServiceFactory =
        (networkToTest, callback) ->
            new ConnectionService(
                WpsConnectionManager.this.context,
                executor,
                connectionHandlerFactory,
                networkToTest,
                callback);
  }

  /**
   * Initialize the library by extracting binary assets.
   * Should be called once before using any connection methods.
   *
   * @return CompletableFuture that completes when initialization is done
   */
  public CompletableFuture<Boolean> initialize() {
    return WpaToolsInitializer.initializeAsync(context);
  }

  /**
   * Wait for the executor environment to be ready.
   * This is automatically called before each operation, but can be called
   * eagerly to pre-warm the executor (e.g. during app startup).
   */
  public void awaitReady() {
    try {
      executor.awaitReady(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Log.w(TAG, "Interrupted while waiting for executor to be ready");
    }
  }

  /**
   * Lightweight stop of the previous active service.
   * Only stops the handler and cancels state — does NOT kill processes.
   * Sessions handle their own process cleanup via AutoCloseable.
   */
  private void stopActive() {
    if (activeService != null) {
      activeService.cleanup();
      activeService = null;
    }
  }

  /**
   * Test a list of WPS PINs against a network.
   *
   * @param bssid Target BSSID (MAC address)
   * @param ssid Target SSID (network name)
   * @param pins Array of PINs to test
   * @param callback Callback for connection updates
   * @return CompletableFuture that completes when testing starts
   */
  public CompletableFuture<Void> testPins(
      String bssid, String ssid, String[] pins, ConnectionUpdateCallback callback) {
    stopActive();

    // Merge database PINs with provided PINs (database PINs first, deduplicated)
    List<String> dbPins = pinDatabaseService.getPinsByMac(bssid);
    LinkedHashSet<String> mergedSet = new LinkedHashSet<>(dbPins);
    mergedSet.addAll(Arrays.asList(pins));
    String[] mergedPins = mergedSet.toArray(new String[0]);

    NetworkToTest network = new NetworkToTest(bssid, ssid, mergedPins);
    activeService = connectionServiceFactory.create(network, callback);
    return activeService.startConnection(false);
  }

  /**
   * Test a Belkin-specific WPS PIN.
   *
   * @param bssid Target BSSID
   * @param ssid Target SSID
   * @param callback Callback for connection updates
   * @return CompletableFuture that completes when testing starts
   */
  public CompletableFuture<Void> testBelkinPin(
      String bssid, String ssid, ConnectionUpdateCallback callback) {
    stopActive();
    NetworkToTest network = new NetworkToTest(bssid, ssid, new String[] {});
    activeService = connectionServiceFactory.create(network, callback);
    return activeService.startBelkinConnection();
  }

  /**
   * Brute force all WPS PINs against a network.
   *
   * @param bssid Target BSSID
   * @param ssid Target SSID
   * @param delayMs Delay in milliseconds between attempts
   * @param callback Callback for connection updates
   * @return CompletableFuture that completes when brute force starts
   */
  public CompletableFuture<Void> bruteForce(
      String bssid, String ssid, int delayMs, ConnectionUpdateCallback callback) {
    stopActive();
    NetworkToTest network = new NetworkToTest(bssid, ssid, new String[] {});
    activeService = connectionServiceFactory.create(network, callback);
    return activeService.startBruteforceConnection(delayMs);
  }

  /**
   * Execute a Pixie Dust attack against a network.
   *
   * @param bssid Target BSSID
   * @param ssid Target SSID
   * @param callback Callback for connection updates
   * @return CompletableFuture that completes when attack starts
   */
  public CompletableFuture<Void> pixieDust(
      String bssid, String ssid, ConnectionUpdateCallback callback) {
    stopActive();
    NetworkToTest network = new NetworkToTest(bssid, ssid, new String[] {});
    activeService = connectionServiceFactory.create(network, callback);
    return activeService.startPixieDustAttack();
  }

  /** Cancel all running operations. Kills WPS processes immediately. */
  public void cancel() {
    if (activeService != null) {
      activeService.cancel();
    }
    executor.cancel();
  }

  /** Cleanup the active service without killing processes. */
  public void cleanup() {
    stopActive();
  }

  /** Full shutdown - cleanup and close the executor. Only call when the manager is no longer needed. */
  public void shutdown() {
    stopActive();
    executor.cleanup();
  }
}
