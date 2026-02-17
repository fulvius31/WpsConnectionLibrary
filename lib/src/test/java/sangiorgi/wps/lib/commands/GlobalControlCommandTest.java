package sangiorgi.wps.lib.commands;

import static org.junit.Assert.*;

import org.junit.Test;

public class GlobalControlCommandTest {

  private static final String TEST_FILES_DIR = "/data/data/com.test/files";
  private static final String TEST_BSSID = "AA:BB:CC:DD:EE:FF";
  private static final String TEST_PIN = "12345670";

  @Test
  public void testCommandType() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    GlobalControlCommand command = new GlobalControlCommand(TEST_BSSID, TEST_PIN, config, false);
    assertEquals(WpsCommand.CommandType.GLOBAL_CONTROL, command.getCommandType());
  }

  @Test
  public void testBuildCommandContainsBssid() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    GlobalControlCommand command = new GlobalControlCommand(TEST_BSSID, TEST_PIN, config, false);
    String built = command.buildCommand();
    assertTrue(built.contains(TEST_BSSID));
  }

  @Test
  public void testBuildCommandContainsPin() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    GlobalControlCommand command = new GlobalControlCommand(TEST_BSSID, TEST_PIN, config, false);
    String built = command.buildCommand();
    assertTrue(built.contains(TEST_PIN));
  }

  @Test
  public void testBuildCommandContainsTimeout() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 60);
    GlobalControlCommand command = new GlobalControlCommand(TEST_BSSID, TEST_PIN, config, false);
    String built = command.buildCommand();
    assertTrue(built.contains("timeout 60"));
  }

  @Test
  public void testBuildCommandContainsBaseCommand() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    GlobalControlCommand command = new GlobalControlCommand(TEST_BSSID, TEST_PIN, config, false);
    String built = command.buildCommand();
    assertTrue(built.contains("cd " + TEST_FILES_DIR));
    assertTrue(built.contains("export LD_LIBRARY_PATH="));
  }

  @Test
  public void testBuildCommand32BitWithIfname() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    GlobalControlCommand command = new GlobalControlCommand(TEST_BSSID, TEST_PIN, config, true);
    String built = command.buildCommand();

    if (!config.is64Bit()) {
      assertTrue(built.contains("IFNAME=wlan0"));
      assertTrue(built.contains("wps_reg"));
      assertTrue(built.contains(config.getControlPath()));
    }
  }

  @Test
  public void testBuildCommand32BitWithoutIfname() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    GlobalControlCommand command = new GlobalControlCommand(TEST_BSSID, TEST_PIN, config, false);
    String built = command.buildCommand();

    if (!config.is64Bit()) {
      assertFalse(built.contains("IFNAME="));
      assertTrue(built.contains("wps_reg"));
    }
  }

  @Test
  public void testBuildCommand64BitFormat() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    GlobalControlCommand command = new GlobalControlCommand(TEST_BSSID, TEST_PIN, config, true);
    String built = command.buildCommand();

    if (config.is64Bit()) {
      assertTrue(built.contains("--pin"));
      assertTrue(built.contains("--bssid"));
    }
  }

  @Test
  public void testSanitizePinWithNullPin() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    GlobalControlCommand command = new GlobalControlCommand(TEST_BSSID, null, config, false);
    String built = command.buildCommand();
    assertTrue(built.contains("''"));
  }
}
