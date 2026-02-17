package sangiorgi.wps.lib.commands;

import java.util.List;

/** Result of a WPS command execution. Immutable value object for command results. */
public class CommandResult {

  private final boolean success;
  private final List<String> output;
  private final List<String> errors;

  public CommandResult(
      boolean success,
      List<String> output,
      List<String> errors,
      WpsCommand.CommandType ignoredCommandType) {
    this.success = success;
    this.output = output;
    this.errors = errors;
  }

  public boolean isSuccess() {
    return success;
  }

  public List<String> getOutput() {
    return output;
  }

  public String getOutputAsString() {
    return output != null ? String.join("\n", output) : "";
  }

  public String getErrorsAsString() {
    return errors != null ? String.join("\n", errors) : "";
  }

  public boolean hasOutput() {
    return output != null && !output.isEmpty();
  }
}
