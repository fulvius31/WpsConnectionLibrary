package sangiorgi.wps.lib.commands;

/**
 * WPS command types for identification and logging.
 */
public final class WpsCommand {

  private WpsCommand() {}

  public enum CommandType {
    WPA_CLI,
    WPA_SUPPLICANT,
    GLOBAL_CONTROL,
    PIXIE_DUST
  }
}
