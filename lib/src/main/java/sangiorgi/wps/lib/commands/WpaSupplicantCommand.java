package sangiorgi.wps.lib.commands;

import java.util.Locale;

/** WPA Supplicant command implementation. Handles starting wpa_supplicant daemon. */
public class WpaSupplicantCommand extends WpsCommand {

  public WpaSupplicantCommand(CommandConfig config) {
    super(null, null, config);
  }

  @Override
  public String buildCommand() {
    String configPath = "-c" + config.getFilesDir() + "/wpa_supplicant.conf";
    String outputPath = config.getWpaSupplicantOutputPath();

    // wpa_supplicant runs in foreground - we use a separate Shell instance for it
    // This allows real-time output capture while wpa_cli runs on another shell
    return String.format(
        Locale.ROOT,
        "%s && ./wpa_supplicant -d -Dnl80211,wext,hostapd,wired -i wlan0 %s %s",
        getBaseCommand(),
        configPath,
        outputPath);
  }

  @Override
  public CommandType getCommandType() {
    return CommandType.WPA_SUPPLICANT;
  }
}
