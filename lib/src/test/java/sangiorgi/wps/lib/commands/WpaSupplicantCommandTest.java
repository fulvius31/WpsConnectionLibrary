package sangiorgi.wps.lib.commands;

import static org.junit.Assert.*;

import org.junit.Test;

public class WpaSupplicantCommandTest {

  private static final String TEST_FILES_DIR = "/data/data/com.test/files";

  @Test
  public void testCommandType() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaSupplicantCommand command = new WpaSupplicantCommand(config);
    assertEquals(WpsCommand.CommandType.WPA_SUPPLICANT, command.getCommandType());
  }

  @Test
  public void testBuildCommandContainsWpaSupplicantBinary() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaSupplicantCommand command = new WpaSupplicantCommand(config);
    String built = command.buildCommand();
    assertTrue(built.contains("./wpa_supplicant"));
  }

  @Test
  public void testBuildCommandContainsDebugFlag() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaSupplicantCommand command = new WpaSupplicantCommand(config);
    String built = command.buildCommand();
    assertTrue(built.contains("-d"));
  }

  @Test
  public void testBuildCommandContainsDrivers() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaSupplicantCommand command = new WpaSupplicantCommand(config);
    String built = command.buildCommand();
    assertTrue(built.contains("-Dnl80211,wext,hostapd,wired"));
  }

  @Test
  public void testBuildCommandContainsInterface() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaSupplicantCommand command = new WpaSupplicantCommand(config);
    String built = command.buildCommand();
    assertTrue(built.contains("-i wlan0"));
  }

  @Test
  public void testBuildCommandContainsConfigPath() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaSupplicantCommand command = new WpaSupplicantCommand(config);
    String built = command.buildCommand();
    assertTrue(built.contains("-c" + TEST_FILES_DIR + "/wpa_supplicant.conf"));
  }

  @Test
  public void testBuildCommandContainsOutputPath() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaSupplicantCommand command = new WpaSupplicantCommand(config);
    String built = command.buildCommand();
    assertTrue(built.contains("wpswpatester"));
  }

  @Test
  public void testBuildCommandContainsBaseCommand() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaSupplicantCommand command = new WpaSupplicantCommand(config);
    String built = command.buildCommand();
    assertTrue(built.contains("cd " + TEST_FILES_DIR));
    assertTrue(built.contains("export LD_LIBRARY_PATH="));
  }

  @Test
  public void testNullBssidAndPinAccepted() {
    // WpaSupplicantCommand passes null for both bssid and pin
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaSupplicantCommand command = new WpaSupplicantCommand(config);
    // Should not throw even with null bssid/pin
    String built = command.buildCommand();
    assertNotNull(built);
  }
}
