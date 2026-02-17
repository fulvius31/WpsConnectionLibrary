package sangiorgi.wps.lib.commands;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class CommandFactoryTest {

  private static final String TEST_FILES_DIR = "/data/data/com.test/files";
  private static final String TEST_BSSID = "AA:BB:CC:DD:EE:FF";
  private static final String TEST_PIN = "12345670";

  private CommandConfig config;
  private CommandFactory factory;

  @Before
  public void setUp() {
    config = new CommandConfig(TEST_FILES_DIR, false, 30);
    factory = new CommandFactory(config);
  }

  @Test
  public void testCreateOldMethodCommandsReturnsTwoCommands() {
    List<WpsCommand> commands = factory.createOldMethodCommands(TEST_BSSID, TEST_PIN);
    assertEquals(2, commands.size());
  }

  @Test
  public void testCreateOldMethodCommandsAreWpaCli() {
    List<WpsCommand> commands = factory.createOldMethodCommands(TEST_BSSID, TEST_PIN);
    for (WpsCommand command : commands) {
      assertTrue(command instanceof WpaCliCommand);
      assertEquals(WpsCommand.CommandType.WPA_CLI, command.getCommandType());
    }
  }

  @Test
  public void testCreateNewMethodCommandsReturnsTwoCommands() {
    List<WpsCommand> commands = factory.createNewMethodCommands(TEST_BSSID, TEST_PIN);
    assertEquals(2, commands.size());
  }

  @Test
  public void testCreateNewMethodCommandsAreGlobalControl() {
    List<WpsCommand> commands = factory.createNewMethodCommands(TEST_BSSID, TEST_PIN);
    for (WpsCommand command : commands) {
      assertTrue(command instanceof GlobalControlCommand);
      assertEquals(WpsCommand.CommandType.GLOBAL_CONTROL, command.getCommandType());
    }
  }

  @Test
  public void testOldMethodCommandsBuildSuccessfully() {
    List<WpsCommand> commands = factory.createOldMethodCommands(TEST_BSSID, TEST_PIN);
    for (WpsCommand command : commands) {
      String built = command.buildCommand();
      assertNotNull(built);
      assertFalse(built.isEmpty());
    }
  }

  @Test
  public void testNewMethodCommandsBuildSuccessfully() {
    List<WpsCommand> commands = factory.createNewMethodCommands(TEST_BSSID, TEST_PIN);
    for (WpsCommand command : commands) {
      String built = command.buildCommand();
      assertNotNull(built);
      assertFalse(built.isEmpty());
    }
  }
}
