package sangiorgi.wps.lib.commands;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for the abstract WpsCommand class via its concrete implementations.
 * Tests sanitizePin() and getBaseCommand() behavior.
 */
public class WpsCommandTest {

  private static final String TEST_FILES_DIR = "/data/data/com.test/files";
  private static final String TEST_BSSID = "AA:BB:CC:DD:EE:FF";

  @Test
  public void testCommandTypeEnum() {
    WpsCommand.CommandType[] types = WpsCommand.CommandType.values();
    assertEquals(4, types.length);
    assertNotNull(WpsCommand.CommandType.valueOf("WPA_CLI"));
    assertNotNull(WpsCommand.CommandType.valueOf("WPA_SUPPLICANT"));
    assertNotNull(WpsCommand.CommandType.valueOf("GLOBAL_CONTROL"));
    assertNotNull(WpsCommand.CommandType.valueOf("PIXIE_DUST"));
  }

  @Test
  public void testBaseCommandFormat() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, "12345670", config, false);
    String built = command.buildCommand();

    // Base command should contain "cd <dir> && export LD_LIBRARY_PATH=<dir>"
    assertTrue(built.startsWith("cd " + TEST_FILES_DIR));
    assertTrue(built.contains("export LD_LIBRARY_PATH=" + TEST_FILES_DIR));
  }

  @Test
  public void testSanitizePinNormal8Digits() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, "12345670", config, false);
    String built = command.buildCommand();
    assertTrue(built.contains("12345670"));
  }

  @Test
  public void testSanitizePinTruncatesLongPin() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, "123456789999", config, false);
    String built = command.buildCommand();
    assertTrue(built.contains("12345678"));
    assertFalse(built.contains("123456789999"));
  }

  @Test
  public void testSanitizePinNullPinReturnsEmptyQuotes() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, null, config, false);
    String built = command.buildCommand();
    assertTrue(built.contains("''"));
  }

  @Test
  public void testSanitizePinEmptyPinReturnsEmptyQuotes() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, "", config, false);
    String built = command.buildCommand();
    assertTrue(built.contains("''"));
  }

  @Test
  public void testSanitizePinNullPinSpecialValue() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, "NULL_PIN", config, false);
    String built = command.buildCommand();
    assertTrue(built.contains("''"));
  }

  @Test
  public void testSanitizePinExactly8Digits() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    WpaCliCommand command = new WpaCliCommand(TEST_BSSID, "99999999", config, false);
    String built = command.buildCommand();
    assertTrue(built.contains("99999999"));
  }
}
