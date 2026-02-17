package sangiorgi.wps.lib.services;

import static org.junit.Assert.*;

import java.util.Collections;
import org.junit.Test;
import sangiorgi.wps.lib.commands.CommandResult;
import sangiorgi.wps.lib.commands.WpsCommand;

public class PixieDustResultTest {

  private static final String TEST_BSSID = "AA:BB:CC:DD:EE:FF";
  private static final String TEST_PIN = "12345670";

  @Test
  public void testSuccessfulResult() {
    PixieDustExecutor.PixieDustResult result =
        new PixieDustExecutor.PixieDustResult(
            true, TEST_BSSID, TEST_PIN, null, "PIN discovered: " + TEST_PIN);

    assertTrue(result.isSuccess());
    assertEquals(TEST_BSSID, result.getBssid());
    assertEquals(TEST_PIN, result.getPin());
    assertEquals("PIN discovered: " + TEST_PIN, result.getMessage());
    assertNull(result.getWpsResult());
    assertNull(result.getPassword());
  }

  @Test
  public void testFailedResult() {
    PixieDustExecutor.PixieDustResult result =
        new PixieDustExecutor.PixieDustResult(
            false, TEST_BSSID, null, null, "Attack failed");

    assertFalse(result.isSuccess());
    assertEquals(TEST_BSSID, result.getBssid());
    assertNull(result.getPin());
    assertEquals("Attack failed", result.getMessage());
  }

  @Test
  public void testResultWithWpsResult() {
    CommandResult cmdResult =
        new CommandResult(
            true,
            Collections.singletonList("WPS_SUCCESS"),
            null,
            WpsCommand.CommandType.WPA_CLI);
    WpsResult wpsResult = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    PixieDustExecutor.PixieDustResult result =
        new PixieDustExecutor.PixieDustResult(
            true, TEST_BSSID, TEST_PIN, wpsResult, "Success");

    assertNotNull(result.getWpsResult());
    assertEquals(wpsResult, result.getWpsResult());
  }

  @Test
  public void testGetPasswordFromWpsResult() {
    // Create a WpsResult with a password
    CommandResult cmdResult =
        new CommandResult(
            true,
            Collections.singletonList("Password: MyWiFiPass"),
            null,
            WpsCommand.CommandType.WPA_CLI);
    WpsResult wpsResult = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    PixieDustExecutor.PixieDustResult result =
        new PixieDustExecutor.PixieDustResult(
            true, TEST_BSSID, TEST_PIN, wpsResult, "Success");

    assertEquals("MyWiFiPass", result.getPassword());
  }

  @Test
  public void testGetPasswordNullWhenNoWpsResult() {
    PixieDustExecutor.PixieDustResult result =
        new PixieDustExecutor.PixieDustResult(
            true, TEST_BSSID, TEST_PIN, null, "Success");

    assertNull(result.getPassword());
  }

  @Test
  public void testGetPasswordNullWhenWpsResultHasNoPassword() {
    CommandResult cmdResult =
        new CommandResult(
            true,
            Collections.singletonList("WPS_SUCCESS"),
            null,
            WpsCommand.CommandType.WPA_CLI);
    WpsResult wpsResult = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    PixieDustExecutor.PixieDustResult result =
        new PixieDustExecutor.PixieDustResult(
            true, TEST_BSSID, TEST_PIN, wpsResult, "Success");

    assertNull(result.getPassword());
  }

  @Test
  public void testLockedResult() {
    PixieDustExecutor.PixieDustResult result =
        new PixieDustExecutor.PixieDustResult(
            false, TEST_BSSID, null, null, "WPS is locked on this router. Cannot perform attack.");

    assertFalse(result.isSuccess());
    assertTrue(result.getMessage().contains("locked"));
  }

  @Test
  public void testTimeoutResult() {
    PixieDustExecutor.PixieDustResult result =
        new PixieDustExecutor.PixieDustResult(
            false,
            TEST_BSSID,
            null,
            null,
            "Timeout waiting for WPS response. Router may not support WPS.");

    assertFalse(result.isSuccess());
    assertTrue(result.getMessage().contains("Timeout"));
  }
}
