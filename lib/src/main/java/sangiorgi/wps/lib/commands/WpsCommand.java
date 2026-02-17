package sangiorgi.wps.lib.commands;

import com.topjohnwu.superuser.Shell;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for WPS commands following Command pattern. Provides a clean abstraction for executing
 * shell commands with consistent error handling.
 */
public abstract class WpsCommand {

  protected final String bssid;
  protected final String pin;
  protected final CommandConfig config;

  public WpsCommand(String bssid, String pin, CommandConfig config) {
    this.bssid = bssid;
    this.pin = pin;
    this.config = config;
  }

  /** Execute the command asynchronously */
  public CompletableFuture<CommandResult> execute() {
    return CompletableFuture.supplyAsync(
        () -> {
          String command = buildCommand();
          Shell.Result result = Shell.cmd(command).exec();
          return new CommandResult(
              result.isSuccess(), result.getOut(), result.getErr(), getCommandType());
        });
  }

  /** Build the shell command string */
  public abstract String buildCommand();

  /** Get the command type for logging and identification */
  public abstract CommandType getCommandType();

  /** Sanitize the PIN input */
  protected String sanitizePin() {
    if (pin == null || pin.isEmpty() || "NULL_PIN".equals(pin)) {
      return "''";
    }
    return pin.length() > 8 ? pin.substring(0, 8) : pin;
  }

  /** Get base command with environment setup */
  protected String getBaseCommand() {
    return String.format(
        "cd %s && export LD_LIBRARY_PATH=%s", config.getFilesDir(), config.getFilesDir());
  }

  public enum CommandType {
    WPA_CLI,
    WPA_SUPPLICANT,
    GLOBAL_CONTROL,
    PIXIE_DUST
  }
}
