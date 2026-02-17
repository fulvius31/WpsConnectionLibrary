package sangiorgi.wps.lib.services;

import android.util.Log;
import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.Shell;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import sangiorgi.wps.lib.commands.CommandConfig;
import sangiorgi.wps.lib.commands.CommandFactory;
import sangiorgi.wps.lib.commands.WpsCommand;

/**
 * Manages a wpa_supplicant session with real-time output capture. This class handles the common
 * pattern of starting wpa_supplicant on a separate Shell, executing wpa_cli commands, and
 * collecting output.
 */
public class WpaSupplicantSession implements AutoCloseable {
  private static final String TAG = "WpaSupplicantSession";
  private static final int READY_TIMEOUT_SECONDS = 5;

  private final CommandConfig config;
  private final List<String> output;
  private final CountDownLatch supplicantReady;
  private final CountDownLatch completionLatch;
  private final Predicate<String> completionCondition;

  private Shell supplicantShell;
  private volatile boolean wpsLocked = false;

  /**
   * Create a new session.
   *
   * @param config Command configuration
   * @param completionCondition Predicate to determine when the session is complete (return true to
   *     signal completion)
   */
  public WpaSupplicantSession(CommandConfig config, Predicate<String> completionCondition) {
    this.config = config;
    this.output = Collections.synchronizedList(new ArrayList<>());
    this.supplicantReady = new CountDownLatch(1);
    this.completionLatch = new CountDownLatch(1);
    this.completionCondition = completionCondition;
  }

  /** Start wpa_supplicant on a separate shell */
  public void start() throws Exception {
    supplicantShell = Shell.Builder.create().build();

    CallbackList<String> outputCallback =
        new CallbackList<>() {
          @Override
          public void onAddElement(String line) {
            output.add(line);
            Log.d(TAG, "wpa_supplicant: " + line);

            // Signal when wpa_supplicant is ready
            if (line.contains("ctrl_iface_init") || line.contains("CTRL-EVENT")) {
              supplicantReady.countDown();
            }

            // Check for WPS locked
            if (WpsResult.isWpsLockedIndicator(line)) {
              Log.w(TAG, "WPS locked detected: " + line);
              wpsLocked = true;
              completionLatch.countDown();
            }

            // Check completion condition
            if (completionCondition != null && completionCondition.test(line)) {
              Log.d(TAG, "Completion condition met: " + line);
              completionLatch.countDown();
            }
          }
        };

    String supplicantCmd =
        new sangiorgi.wps.lib.commands.WpaSupplicantCommand(config).buildCommand();
    Log.d(TAG, "Starting wpa_supplicant: " + supplicantCmd);

    supplicantShell
        .newJob()
        .add(supplicantCmd)
        .to(outputCallback, outputCallback)
        .submit(
            result -> {
              Log.d(TAG, "wpa_supplicant process ended, success: " + result.isSuccess());
              supplicantReady.countDown();
              completionLatch.countDown();
            });
  }

  /** Wait for wpa_supplicant to be ready */
  public void waitForReady() throws InterruptedException {
    boolean ready = supplicantReady.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (!ready) {
      Log.w(TAG, "wpa_supplicant ready signal not received, proceeding anyway...");
      Thread.sleep(2000);
    }
  }

  /** Execute wpa_cli commands */
  public void executeWpaCliCommands(String bssid, String pin) {
    Log.d(TAG, "Executing wpa_cli commands for BSSID: " + bssid + " PIN: " + pin);
    CommandFactory factory = new CommandFactory(config);
    List<WpsCommand> commands = factory.createNewMethodCommands(bssid, pin);

    for (WpsCommand command : commands) {
      try {
        command.execute().get(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        Log.w(TAG, "wpa_cli command failed: " + e.getMessage());
      }
    }
  }

  /** Wait for completion condition or timeout */
  public void waitForCompletion(int timeoutSeconds) throws InterruptedException {
    boolean completed = completionLatch.await(timeoutSeconds, TimeUnit.SECONDS);
    if (!completed) {
      Log.w(TAG, "Session timed out after " + timeoutSeconds + " seconds");
    }
    // Give a bit more time for final output
    Thread.sleep(1000);
  }

  /** Get collected output */
  public List<String> getOutput() {
    return new ArrayList<>(output);
  }

  /** Check if WPS is locked */
  public boolean isWpsLocked() {
    return wpsLocked;
  }

  @Override
  public void close() {
    if (supplicantShell != null) {
      try {
        supplicantShell.close();
      } catch (Exception e) {
        Log.w(TAG, "Error closing supplicant shell", e);
      }
    }
    // Kill any remaining wpa_supplicant processes
    try {
      Shell.cmd("pkill -f wpa_supplicant").exec();
    } catch (Exception e) {
      Log.w(TAG, "Error killing wpa_supplicant", e);
    }
  }
}
