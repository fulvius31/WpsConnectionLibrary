package sangiorgi.wps.lib.commands;

import sangiorgi.wps.lib.models.PixieDustParameters;

/** Pixie Dust attack command implementation. */
public class PixieDustCommand extends WpsCommand {

  private final PixieDustParameters parameters;

  // Constructor with extracted parameters (required)
  public PixieDustCommand(String bssid, PixieDustParameters parameters, CommandConfig config) {
    super(bssid, null, config);
    if (parameters == null || !parameters.isValid()) {
      throw new IllegalArgumentException("Valid PixieDustParameters are required");
    }
    this.parameters = parameters;
  }

  @Override
  public String buildCommand() {
    return String.format("%s && timeout 60 ./pixiedust %s", getBaseCommand(), parameters);
  }

  @Override
  public CommandType getCommandType() {
    return CommandType.PIXIE_DUST;
  }
}
