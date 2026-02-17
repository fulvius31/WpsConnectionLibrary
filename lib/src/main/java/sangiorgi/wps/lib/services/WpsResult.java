package sangiorgi.wps.lib.services;

import java.util.List;
import java.util.Locale;
import sangiorgi.wps.lib.commands.CommandResult;

/** Result of a complete WPS connection attempt. Aggregates results from multiple commands. */
public class WpsResult {

  /**
   * Check if a line of WPS output indicates that WPS is locked.
   *
   * @param line The output line to check (will be converted to lowercase)
   * @return true if the line indicates WPS is locked
   */
  public static boolean isWpsLockedIndicator(String line) {
    if (line == null) return false;
    String lower = line.toLowerCase(Locale.ROOT);
    return lower.contains("wps-pbc-overlap")
        || lower.contains("wps_overlap")
        || lower.contains("setup locked")
        || (lower.contains("wps-fail") && lower.contains("config_error=15"));
  }

  private final String bssid;
  private final String pin;
  private final List<CommandResult> commandResults;
  private final boolean success;
  private String password;

  public WpsResult(String bssid, String pin, List<CommandResult> commandResults) {
    this.bssid = bssid;
    this.pin = pin;
    this.commandResults = commandResults;
    this.success = determineSuccess();
    this.password = extractPassword();
  }

  private boolean determineSuccess() {
    if (commandResults == null || commandResults.isEmpty()) {
      return false;
    }

    // Check if any command succeeded and has expected output
    for (CommandResult result : commandResults) {
      if (result.isSuccess() && hasSuccessIndicator(result)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasSuccessIndicator(CommandResult result) {
    String output = result.getOutputAsString().toLowerCase(Locale.ROOT);
    // Check for specific WPS success indicators
    // Avoid generic terms like "completed" which can appear in non-success contexts
    return output.contains("wps-success")
        || output.contains("wps_success")
        || output.contains("ctrl-event-connected")
        || output.contains("key negotiation completed")
        || output.contains("network key");
  }

  public boolean isSuccess() {
    return success;
  }

  public boolean isTimeout() {
    // Don't report timeout if we have a more specific WPS error
    if (hasWpsFailMessage()) {
      return false;
    }

    for (CommandResult result : commandResults) {
      String output = result.getOutputAsString().toLowerCase(Locale.ROOT);
      String errors = result.getErrorsAsString().toLowerCase(Locale.ROOT);
      if (output.contains("timeout") || errors.contains("timeout")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check if any WPS-FAIL message is present in the output. Used to distinguish between a real
   * timeout and a WPS failure that might also contain "timeout" in the output.
   */
  private boolean hasWpsFailMessage() {
    for (CommandResult result : commandResults) {
      String output = result.getOutputAsString().toLowerCase(Locale.ROOT);
      if (output.contains("wps-fail") || output.contains("wps_fail")) {
        return true;
      }
    }
    return false;
  }

  public boolean isLocked() {
    for (CommandResult result : commandResults) {
      if (result.hasOutput()) {
        for (String line : result.getOutput()) {
          if (isWpsLockedIndicator(line)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Check if the first half of the PIN (first 4 digits) was correct. In WPS protocol, M4 failure
   * means first half wrong, M6 failure means first half correct but second half wrong.
   *
   * @return true if first 4 digits are correct (M6 failure or later)
   */
  public boolean isFirstHalfCorrect() {
    for (CommandResult result : commandResults) {
      String output = result.getOutputAsString().toLowerCase(Locale.ROOT);
      // M6 failure indicates first half was correct
      if (output.contains("m6") || output.contains("wsc_nack after m6")) {
        return true;
      }
      // M4 failure indicates first half was wrong
      if (output.contains("m4") || output.contains("wsc_nack after m4")) {
        return false;
      }
    }
    return false;
  }

  /**
   * Check if this was a definitive PIN failure (not timeout/locked).
   *
   * @return true if the PIN was actually tried and rejected
   */
  public boolean isPinRejected() {
    for (CommandResult result : commandResults) {
      String output = result.getOutputAsString().toLowerCase(Locale.ROOT);
      // These indicate the PIN was actually tested
      if (output.contains("wps-fail")
          || output.contains("wsc_nack")
          || output.contains("m2d")
          || output.contains("eap failure")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check if the PIN was wrong (config_error=18 means "No suitable credentials").
   *
   * @return true if the PIN was specifically rejected as wrong
   */
  public boolean isWrongPin() {
    for (CommandResult result : commandResults) {
      String output = result.getOutputAsString().toLowerCase(Locale.ROOT);
      // config_error=18 = "No suitable credentials" = wrong PIN
      // msg=8 typically accompanies this error
      if (output.contains("config_error=18")
          || (output.contains("wps-fail") && output.contains("msg=8"))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get a human-readable error message based on the WPS failure type.
   *
   * @return Error message describing why connection failed
   */
  public String getFailureReason() {
    if (isLocked()) {
      return "WPS is locked on this router";
    }
    if (isWrongPin()) {
      return "Wrong PIN";
    }
    if (isTimeout()) {
      return "Connection timed out";
    }
    if (isPinRejected()) {
      return "PIN rejected";
    }
    return "Connection failed";
  }

  public String getBssid() {
    return bssid;
  }

  public String getPin() {
    return pin;
  }

  public List<CommandResult> getCommandResults() {
    return commandResults;
  }

  public String getAllOutput() {
    StringBuilder sb = new StringBuilder();
    for (CommandResult result : commandResults) {
      if (result.hasOutput()) {
        sb.append(result.getOutputAsString()).append("\n");
      }
    }
    return sb.toString();
  }

  public String getOutputAsString() {
    return getAllOutput();
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public List<CommandResult> getResults() {
    return commandResults;
  }

  private String extractPassword() {
    // Try to extract password from command outputs
    for (CommandResult result : commandResults) {
      if (result.hasOutput()) {
        for (String line : result.getOutput()) {
          if (line == null) continue;

          // Look for wpa_supplicant Network Key hexdump format
          // Format: "Network Key ... hexdump(): XX XX XX ..."
          if (line.contains("Network Key") && line.contains("hexdump(")) {
            try {
              int startIdx = line.indexOf("):") + 3;
              if (startIdx > 2 && startIdx < line.length()) {
                String hexString = line.substring(startIdx).replaceAll("\\s+", "");
                String decoded = convertHexToString(hexString);
                if (decoded != null && !decoded.equals("No") && !decoded.isEmpty()) {
                  return decoded;
                }
              }
            } catch (Exception e) {
              // Continue searching for other patterns
            }
          }

          // Look for password patterns
          if (line.contains("Password:")
              || line.contains("password:")
              || line.contains("PSK:")
              || line.contains("psk:")) {
            String[] parts = line.split(":");
            if (parts.length > 1) {
              return parts[1].trim();
            }
          }

          // Look for WPA PSK pattern
          if (line.contains("wpa_psk=") || line.contains("WPA_PSK=")) {
            String[] parts = line.split("=");
            if (parts.length > 1) {
              return parts[1].trim();
            }
          }
        }
      }
    }
    return null;
  }

  private static String convertHexToString(String hex) {
    if (hex == null || hex.isEmpty()) {
      return null;
    }

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < hex.length() - 1; i += 2) {
      try {
        String output = hex.substring(i, i + 2);
        int decimal = Integer.parseInt(output, 16);
        sb.append((char) decimal);
      } catch (StringIndexOutOfBoundsException | NumberFormatException e) {
        return "No";
      }
    }
    return sb.toString();
  }
}
