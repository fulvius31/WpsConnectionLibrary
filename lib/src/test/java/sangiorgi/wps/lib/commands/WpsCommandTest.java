package sangiorgi.wps.lib.commands;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for WpsCommand.CommandType enum.
 */
public class WpsCommandTest {

  @Test
  public void testCommandTypeEnum() {
    WpsCommand.CommandType[] types = WpsCommand.CommandType.values();
    assertEquals(4, types.length);
    assertNotNull(WpsCommand.CommandType.valueOf("WPA_CLI"));
    assertNotNull(WpsCommand.CommandType.valueOf("WPA_SUPPLICANT"));
    assertNotNull(WpsCommand.CommandType.valueOf("GLOBAL_CONTROL"));
    assertNotNull(WpsCommand.CommandType.valueOf("PIXIE_DUST"));
  }
}
