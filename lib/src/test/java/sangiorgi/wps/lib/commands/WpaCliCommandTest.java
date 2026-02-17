package sangiorgi.wps.lib.commands;

import static org.junit.Assert.*;

import org.junit.Test;

public class WpaCliCommandTest {

  private static final String TEST_FILES_DIR = "/data/data/com.test/files";
  private static final String TEST_BSSID = "AA:BB:CC:DD:EE:FF";
  private static final String TEST_PIN = "12345670";

  @Test
  public void testCommandType() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, TEST_PIN, config, false);
    assertEquals(WpsCommand.CommandType.WPA_CLI, command.getCommandType());
  }

  @Test
  public void testBuildCommandContainsBssid() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, TEST_PIN, config, false);
    String built = command.buildCommand();
    assertTrue("Should contain BSSID", built.contains(TEST_BSSID));
  }

  @Test
  public void testBuildCommandContainsPin() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, TEST_PIN, config, false);
    String built = command.buildCommand();
    assertTrue("Should contain PIN", built.contains(TEST_PIN));
  }

  @Test
  public void testBuildCommandContainsTimeout() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 45);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, TEST_PIN, config, false);
    String built = command.buildCommand();
    assertTrue("Should contain timeout", built.contains("timeout 45"));
  }

  @Test
  public void testBuildCommandContainsBaseCommand() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, TEST_PIN, config, false);
    String built = command.buildCommand();
    assertTrue("Should contain cd to files dir", built.contains("cd " + TEST_FILES_DIR));
    assertTrue("Should contain LD_LIBRARY_PATH", built.contains("export LD_LIBRARY_PATH="));
  }

  @Test
  public void testBuildCommandContainsWpaCliNBinary() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, TEST_PIN, config, false);
    String built = command.buildCommand();
    assertTrue("Should contain wpa_cli_n", built.contains("wpa_cli_n"));
  }

  @Test
  public void testBuildCommand32BitWithIfname() {
    // Force 32-bit: os.arch won't contain "64" on 32-bit systems
    // On a 64-bit test JVM this may produce 64-bit format
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, TEST_PIN, config, true);
    String built = command.buildCommand();

    if (config.is64Bit()) {
      // 64-bit format uses --pin and --bssid flags
      assertTrue("64-bit should contain --pin", built.contains("--pin"));
      assertTrue("64-bit should contain --bssid", built.contains("--bssid"));
    } else {
      // 32-bit with IFNAME
      assertTrue("32-bit with IFNAME should contain IFNAME=wlan0", built.contains("IFNAME=wlan0"));
      assertTrue("32-bit should contain wps_reg", built.contains("wps_reg"));
    }
  }

  @Test
  public void testBuildCommand32BitWithoutIfname() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, TEST_PIN, config, false);
    String built = command.buildCommand();

    if (!config.is64Bit()) {
      assertFalse("32-bit without IFNAME should not contain IFNAME", built.contains("IFNAME="));
      assertTrue("32-bit should contain wps_reg", built.contains("wps_reg"));
    }
  }

  @Test
  public void testSanitizePinNormal() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, "12345670", config, false);
    String built = command.buildCommand();
    assertTrue("Should contain sanitized PIN", built.contains("12345670"));
  }

  @Test
  public void testSanitizePinNull() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, null, config, false);
    String built = command.buildCommand();
    assertTrue("Null PIN should be sanitized to empty quotes", built.contains("''"));
  }

  @Test
  public void testSanitizePinEmpty() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, "", config, false);
    String built = command.buildCommand();
    assertTrue("Empty PIN should be sanitized to empty quotes", built.contains("''"));
  }

  @Test
  public void testSanitizePinNullPin() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, "NULL_PIN", config, false);
    String built = command.buildCommand();
    assertTrue("NULL_PIN should be sanitized to empty quotes", built.contains("''"));
  }

  @Test
  public void testSanitizePinTooLong() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, true, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, "1234567890", config, false);
    String built = command.buildCommand();
    assertTrue("Long PIN should be truncated to 8 chars", built.contains("12345678"));
    assertFalse("Should not contain full 10-digit PIN", built.contains("1234567890"));
  }
}
