package sangiorgi.wps.lib.commands;

import java.util.Locale;

/** Global control command implementation. Handles WPS commands through global control interface. */
public class GlobalControlCommand extends WpsCommand {

  private final boolean useIfname;

  public GlobalControlCommand(String bssid, String pin, CommandConfig config, boolean useIfname) {
    super(bssid, pin, config);
    this.useIfname = useIfname;
  }

  @Override
  public String buildCommand() {
    if (config.is64Bit()) {
      // 64-bit uses simplified command format: wpa_cli_n --pin <PIN> --bssid <BSSID>
      return String.format(
          Locale.getDefault(),
          "%s && timeout %d ./wpa_cli_n --pin %s --bssid %s",
          getBaseCommand(),
          config.getTimeout(),
          sanitizePin(),
          bssid);
    } else {
      // 32-bit uses the old format with control path and wps_reg
      String baseCommand =
          String.format(
              Locale.getDefault(),
              "%s && timeout %d ./wpa_cli_n %s",
              getBaseCommand(),
              config.getTimeout(),
              config.getControlPath());

      if (useIfname) {
        return baseCommand + " IFNAME=wlan0 wps_reg " + bssid + " " + sanitizePin();
      } else {
        return baseCommand + " wps_reg " + bssid + " " + sanitizePin();
      }
    }
  }

  @Override
  public CommandType getCommandType() {
    return CommandType.GLOBAL_CONTROL;
  }
}
