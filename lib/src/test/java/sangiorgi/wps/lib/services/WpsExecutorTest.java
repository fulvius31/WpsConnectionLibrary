package sangiorgi.wps.lib.services;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for WpsExecutor.
 *
 * <p>Note: WpsExecutor now requires an Android Context for WpsNative initialization.
 * Full testing requires an instrumented test environment (Android device with root).
 * Basic tests that don't require Context are kept here.
 */
public class WpsExecutorTest {

  @Test
  public void testWpsResultTypes() {
    // Verify WpsResult status analysis with known outputs
    // This tests the result parsing logic which doesn't need a live executor
    java.util.List<String> successOutput = java.util.Arrays.asList("WPS-SUCCESS", "CTRL-EVENT-CONNECTED");
    sangiorgi.wps.lib.commands.CommandResult cmdResult =
        new sangiorgi.wps.lib.commands.CommandResult(
            true, successOutput, null,
            sangiorgi.wps.lib.commands.WpsCommand.CommandType.WPA_SUPPLICANT);
    WpsResult result = new WpsResult("AA:BB:CC:DD:EE:FF", "12345670",
        java.util.Collections.singletonList(cmdResult));

    assertTrue("Should detect success", result.isSuccess());
    assertFalse("Should not be locked", result.isLocked());
    assertFalse("Should not be timeout", result.isTimeout());
  }

  @Test
  public void testWpsResultLocked() {
    java.util.List<String> lockedOutput = java.util.Arrays.asList("WPS-FAIL config_error=15", "setup locked");
    sangiorgi.wps.lib.commands.CommandResult cmdResult =
        new sangiorgi.wps.lib.commands.CommandResult(
            false, lockedOutput, null,
            sangiorgi.wps.lib.commands.WpsCommand.CommandType.WPA_SUPPLICANT);
    WpsResult result = new WpsResult("AA:BB:CC:DD:EE:FF", "12345670",
        java.util.Collections.singletonList(cmdResult));

    assertFalse("Should not be success", result.isSuccess());
    assertTrue("Should detect locked", result.isLocked());
  }
}
