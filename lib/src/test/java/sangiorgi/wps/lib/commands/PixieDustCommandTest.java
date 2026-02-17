package sangiorgi.wps.lib.commands;

import static org.junit.Assert.*;

import org.junit.Test;
import sangiorgi.wps.lib.models.PixieDustParameters;

public class PixieDustCommandTest {

  private static final String TEST_FILES_DIR = "/data/data/com.test/files";
  private static final String TEST_BSSID = "AA:BB:CC:DD:EE:FF";

  private PixieDustParameters createValidParameters() {
    return new PixieDustParameters(
        "0123456789abcdef", // pke
        "fedcba9876543210", // pkr
        "aaaa1111bbbb2222", // ehash1
        "cccc3333dddd4444", // ehash2
        "abcdef0123456789", // authKey
        "1234abcd5678efab" // enonce
        );
  }

  @Test
  public void testCommandType() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    PixieDustCommand command = new PixieDustCommand(TEST_BSSID, createValidParameters(), config);
    assertEquals(WpsCommand.CommandType.PIXIE_DUST, command.getCommandType());
  }

  @Test
  public void testBuildCommandContainsPixiedustBinary() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    PixieDustCommand command = new PixieDustCommand(TEST_BSSID, createValidParameters(), config);
    String built = command.buildCommand();
    assertTrue(built.contains("./pixiedust"));
  }

  @Test
  public void testBuildCommandContainsTimeout() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    PixieDustCommand command = new PixieDustCommand(TEST_BSSID, createValidParameters(), config);
    String built = command.buildCommand();
    assertTrue(built.contains("timeout 60"));
  }

  @Test
  public void testBuildCommandContainsParameters() {
    PixieDustParameters params = createValidParameters();
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    PixieDustCommand command = new PixieDustCommand(TEST_BSSID, params, config);
    String built = command.buildCommand();

    assertTrue(built.contains("--pke " + params.getPke()));
    assertTrue(built.contains("--pkr " + params.getPkr()));
    assertTrue(built.contains("--e-hash1 " + params.getEhash1()));
    assertTrue(built.contains("--e-hash2 " + params.getEhash2()));
    assertTrue(built.contains("--authkey " + params.getAuthKey()));
    assertTrue(built.contains("--e-nonce " + params.getEnonce()));
  }

  @Test
  public void testBuildCommandContainsBaseCommand() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    PixieDustCommand command = new PixieDustCommand(TEST_BSSID, createValidParameters(), config);
    String built = command.buildCommand();
    assertTrue(built.contains("cd " + TEST_FILES_DIR));
    assertTrue(built.contains("export LD_LIBRARY_PATH="));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNullParametersThrows() {
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    new PixieDustCommand(TEST_BSSID, null, config);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidParametersThrows() {
    PixieDustParameters invalid =
        new PixieDustParameters("pke", null, "ehash1", "ehash2", "authKey", "enonce");
    CommandConfig config = new CommandConfig(TEST_FILES_DIR, false, 30);
    new PixieDustCommand(TEST_BSSID, invalid, config);
  }
}
