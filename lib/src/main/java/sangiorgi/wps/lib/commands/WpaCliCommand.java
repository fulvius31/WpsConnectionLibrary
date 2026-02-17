package sangiorgi.wps.lib.commands;

import java.util.Locale;

/** WPA CLI command implementation. Handles wpa_cli based WPS connections. */
public class WpaCliCommand extends WpsCommand {

  private final boolean useIfname;

  public WpaCliCommand(String bssid, String pin, CommandConfig config, boolean useIfname) {
    super(bssid, pin, config);
    this.useIfname = useIfname;
  }

  @Override
  public String buildCommand() {
    if (config.is64Bit()) {
      // 64-bit uses simplified command format: wpa_cli_n --pin <PIN> --bssid <BSSID>
      return String.format(
          Locale.ROOT,
          "%s && timeout %d ./wpa_cli_n --pin %s --bssid %s",
          getBaseCommand(),
          config.getTimeout(),
          sanitizePin(),
          bssid);
    } else {
      // 32-bit uses the old format with wps_reg
      String baseCommand =
          String.format(
              Locale.ROOT, "%s && timeout %d ./wpa_cli_n", getBaseCommand(), config.getTimeout());

      if (useIfname) {
        return baseCommand + " IFNAME=wlan0 wps_reg " + bssid + " " + sanitizePin();
      } else {
        return baseCommand + " wps_reg " + bssid + " " + sanitizePin();
      }
    }
  }

  @Override
  public CommandType getCommandType() {
    return CommandType.WPA_CLI;
  }
}
