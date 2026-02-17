package sangiorgi.wps.lib.commands;

import java.util.ArrayList;
import java.util.List;

/** Factory for creating WPS commands. Centralizes command creation logic. */
public class CommandFactory {

  private final CommandConfig config;

  public CommandFactory(CommandConfig config) {
    this.config = config;
  }

  /** Create commands for old method (direct wpa_cli) */
  public List<WpsCommand> createOldMethodCommands(String bssid, String pin) {
    List<WpsCommand> commands = new ArrayList<>();
    commands.add(new WpaCliCommand(bssid, pin, config, true));
    commands.add(new WpaCliCommand(bssid, pin, config, false));
    return commands;
  }

  /** Create commands for new method (wpa_supplicant + global control) */
  public List<WpsCommand> createNewMethodCommands(String bssid, String pin) {
    List<WpsCommand> commands = new ArrayList<>();
    commands.add(new GlobalControlCommand(bssid, pin, config, true));
    commands.add(new GlobalControlCommand(bssid, pin, config, false));
    return commands;
  }
}
