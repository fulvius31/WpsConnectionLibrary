package sangiorgi.wps.lib.handlers;

import android.content.Context;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import sangiorgi.wps.lib.ConnectionUpdateCallback;
import sangiorgi.wps.lib.R;
import sangiorgi.wps.lib.models.NetworkToTest;
import sangiorgi.wps.lib.services.*;

/**
 * Connection handler using CompletableFuture for cleaner async operations. Replaces complex
 * HandlerThread pattern with simpler, more maintainable code.
 */
public class ConnectionHandler {

  private static final String TAG = "ConnectionHandler";
  private static final int TIMEOUT_MS = 3000;
  private static final int MAX_TIMEOUT_ATTEMPTS = 2;

  private final Context context;
  private final NetworkToTest networkToTest;
  private final ConnectionUpdateCallback callback;
  private final ConnectionStateManager stateManager;
  private final WpsExecutor executor;
  private final PinValidationService pinValidator;

  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final AtomicBoolean isCancelled = new AtomicBoolean(false);
  private final AtomicInteger currentPinIndex = new AtomicInteger(0);
  private final AtomicInteger timeoutAttempts = new AtomicInteger(0);

  private boolean useOldMethod;

  /** Factory interface for creating ConnectionHandler instances. */
  public interface Factory {
    ConnectionHandler create(
        NetworkToTest networkToTest,
        ConnectionUpdateCallback callback,
        ConnectionStateManager stateManager,
        boolean useOldMethod);
  }

  public ConnectionHandler(
      Context context,
      WpsExecutor executor,
      PinValidationService pinValidator,
      NetworkToTest networkToTest,
      ConnectionUpdateCallback callback,
      ConnectionStateManager stateManager,
      boolean useOldMethod) {
    this.context = context;
    this.executor = executor;
    this.pinValidator = pinValidator;
    this.networkToTest = networkToTest;
    this.callback = callback;
    this.stateManager = stateManager;
    this.useOldMethod = useOldMethod;
  }

  /** Start the connection process */
  public void start() {
    if (isRunning.compareAndSet(false, true)) {
      isCancelled.set(false);
      initializeConnection();
      processNextPin();
    } else {
      Log.w(TAG, "Handler already running");
    }
  }

  /** Stop the connection process gracefully (sets flags, does not kill processes). */
  public void stop() {
    if (isRunning.compareAndSet(true, false)) {
      isCancelled.set(true);
      stateManager.setCancelled(true);
    }
  }

  /** Check if handler is running */
  public boolean isRunning() {
    return isRunning.get();
  }

  private void initializeConnection() {
    String title = context.getString(R.string.wps_lib_connecting) + " (Root)";
    String message = "Testing PIN: " + networkToTest.getPins()[0];

    runOnUiThread(
        () -> stateManager.createConnection(title, message, networkToTest.getPins().length));
  }

  private void processNextPin() {
    if (isCancelled.get() || !isRunning.get()) {
      Log.d(TAG, "Stopping pin processing - cancelled or stopped");
      return;
    }

    int pinIndex = currentPinIndex.get();
    if (pinIndex >= networkToTest.getPins().length) {
      handleAllPinsExhausted();
      return;
    }

    String currentPin = networkToTest.getPins()[pinIndex];
    stateManager.setCurrentConnection(networkToTest, currentPin);

    // Check if pin was already tested
    if (pinValidator.isPinAlreadyTested(networkToTest.getBssid(), currentPin)) {
      handleAlreadyTestedPin(currentPin);
      return;
    }

    // Execute WPS connection
    executeWpsConnection(currentPin);
  }

  private void executeWpsConnection(String pin) {
    executor
        .executeWpsConnection(networkToTest.getBssid(), pin, useOldMethod)
        .thenAccept(
            result -> {
              if (isCancelled.get()) {
                return;
              }

              // Wait for response
              try {
                Thread.sleep(TIMEOUT_MS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }

              handleWpsResult(result, pin);
            })
        .exceptionally(
            throwable -> {
              Log.e(TAG, "WPS connection failed", throwable);
              // Don't store the pin on exception - we don't know if it was tested
              handleUnknownResult(pin);
              return null;
            });
  }

  private void handleWpsResult(WpsResult result, String pin) {
    if (result.isSuccess()) {
      handleSuccessfulConnection(result);
    } else if (result.isLocked()) {
      // Check locked first - AP is blocking WPS attempts
      handleLocked(pin);
    } else if (result.isWrongPin()) {
      // PIN was wrong (config_error=18) - clear message and move to next
      handleWrongPin(pin);
    } else if (result.isPinRejected()) {
      // PIN was actually tested and rejected - store it
      if (result.isFirstHalfCorrect()) {
        handleFirstHalfCorrect(pin);
      } else {
        handlePinFailure(pin, result.getFailureReason());
      }
    } else if (result.isTimeout()) {
      // Check timeout after specific WPS errors - timeout is a generic fallback
      handleTimeout(pin);
    } else {
      // Unknown result - don't store, just move on
      handleUnknownResult(pin);
    }
  }

  private void handleSuccessfulConnection(WpsResult result) {
    // Extract password from wpa_supplicant output
    String password = result.getPassword();
    if (password != null && !password.isEmpty()) {
      networkToTest.setPassword(password);
      Log.i(TAG, "Password found: " + password);
    }
    runOnUiThread(() -> stateManager.handleSuccessfulConnection(password));
    stop();
  }

  private void handleTimeout(String pin) {
    int attempts = timeoutAttempts.incrementAndGet();
    // Store the pin as failed so it won't be retried
    pinValidator.storePinResult(networkToTest.getBssid(), pin, false);
    runOnUiThread(
        () ->
            stateManager.updateProgress(
                context.getString(R.string.wps_lib_timeout) + " " + pin));

    if (attempts > MAX_TIMEOUT_ATTEMPTS) {
      useOldMethod = true;
      timeoutAttempts.set(0);
    }

    moveToNextPin();
    processNextPin();
  }

  private void handleLocked(String pin) {
    // Store the pin as failed so it won't be retried
    pinValidator.storePinResult(networkToTest.getBssid(), pin, false);
    String message = "PIN " + pin + " - WPS locked or overlap detected";
    runOnUiThread(() -> stateManager.updateProgress(message));
    useOldMethod = true;
    moveToNextPin();
    processNextPin();
  }

  private void handleWrongPin(String pin) {
    // PIN was specifically identified as wrong (config_error=18)
    pinValidator.storePinResult(networkToTest.getBssid(), pin, false);
    runOnUiThread(
        () -> stateManager.updateProgress("PIN " + pin + " is wrong - trying next PIN..."));
    moveToNextPin();
    processNextPin();
  }

  private void handlePinFailure(String pin, String reason) {
    // Store the failed pin (full PIN was wrong)
    pinValidator.storePinResult(networkToTest.getBssid(), pin, false);
    runOnUiThread(() -> stateManager.updateProgress("PIN " + pin + " - " + reason));
    moveToNextPin();
    processNextPin();
  }

  private void handleFirstHalfCorrect(String pin) {
    // First 4 digits are correct - store with "last_three" suffix
    pinValidator.storeFirstHalfCorrect(networkToTest.getBssid(), pin);
    runOnUiThread(
        () ->
            stateManager.updateProgress(
                "PIN " + pin + " - first half correct, last 3 digits wrong"));
    moveToNextPin();
    processNextPin();
  }

  private void handleUnknownResult(String pin) {
    // Store the pin as failed so it won't be retried
    pinValidator.storePinResult(networkToTest.getBssid(), pin, false);
    runOnUiThread(() -> stateManager.updateProgress("PIN " + pin + " - no clear response"));
    moveToNextPin();
    processNextPin();
  }

  private void handleAlreadyTestedPin(String pin) {
    runOnUiThread(() -> stateManager.updateProgress("PIN " + pin + " - already tested, skipping"));
    moveToNextPin();
    processNextPin();
  }

  private void moveToNextPin() {
    int nextIndex = currentPinIndex.incrementAndGet();
    // Update the count (currentPinIndex is 0-based, so nextIndex represents completed pins)
    runOnUiThread(() -> stateManager.updateCount(1));

    if (nextIndex < networkToTest.getPins().length) {
      String nextPin = networkToTest.getPins()[nextIndex];
      runOnUiThread(() -> stateManager.updateProgress("Testing PIN: " + nextPin));
    }
  }

  private void handleAllPinsExhausted() {
    String errorMessage =
        context.getString(R.string.wps_lib_failed_to_connect) + " " + networkToTest.getSsid();
    runOnUiThread(() -> stateManager.handleFailedConnection(errorMessage, -1));
    stop();
  }

  private void runOnUiThread(Runnable runnable) {
    if (callback != null) {
      // Use Handler with main looper for API compatibility (getMainExecutor requires API 28)
      new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
    }
  }
}
