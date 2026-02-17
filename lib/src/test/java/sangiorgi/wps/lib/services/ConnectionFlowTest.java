package sangiorgi.wps.lib.services;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import sangiorgi.wps.lib.commands.CommandResult;
import sangiorgi.wps.lib.commands.WpsCommand;

/**
 * Integration tests for WPS connection flow. Tests the complete flow from PIN validation through
 * result parsing. Ported from the original app's WpsConnectionFlowTest.
 */
public class ConnectionFlowTest {

  private static final String TEST_BSSID = "AA:BB:CC:DD:EE:FF";

  // ============ PIN Generation Tests ============

  @Test
  public void testStandardPinFormat() {
    String[] standardPins = {"12345670", "00000000", "11111111"};

    for (String pin : standardPins) {
      assertEquals("PIN should be 8 digits: " + pin, 8, pin.length());
      assertTrue("PIN should be numeric: " + pin, pin.matches("\\d{8}"));
    }
  }

  @Test
  public void testBelkinPinGeneration() {
    String mac = "AA:BB:CC:DD:EE:FF";
    String pin = generateBelkinPin(mac);

    assertEquals("Belkin PIN should be 8 digits", 8, pin.length());
    assertTrue("Belkin PIN should be numeric", pin.matches("\\d{8}"));
  }

  private String generateBelkinPin(String bssid) {
    String mac = bssid.replace(":", "");
    int seed = Integer.parseInt(mac.substring(6), 16);
    return String.format(Locale.ROOT, "%08d", seed % 100000000);
  }

  // ============ Connection Result Flow Tests ============

  @Test
  public void testSuccessfulConnectionFlow() {
    String pin = "12345670";

    List<String> wpsOutput =
        Arrays.asList(
            "WPS: Starting registration protocol",
            "WPS: Received M2",
            "WPS: Received M4",
            "WPS: Received M6",
            "WPS: Received M8",
            "WPS_SUCCESS",
            "WPA: Key negotiation completed",
            "CTRL-EVENT-CONNECTED",
            "Network connected",
            "Password: MyNetworkPassword123");
    CommandResult wpsResult =
        new CommandResult(true, wpsOutput, null, WpsCommand.CommandType.WPA_CLI);

    WpsResult result = new WpsResult(TEST_BSSID, pin, Collections.singletonList(wpsResult));

    assertTrue("Connection should be successful", result.isSuccess());
    assertFalse("Should not be timeout", result.isTimeout());
    assertFalse("Should not be locked", result.isLocked());
    assertEquals("PIN should match", pin, result.getPin());
    assertEquals("BSSID should match", TEST_BSSID, result.getBssid());
    assertEquals("Should extract password", "MyNetworkPassword123", result.getPassword());
  }

  @Test
  public void testTimeoutConnectionFlow() {
    String pin = "12345670";

    List<String> wpsOutput =
        Arrays.asList(
            "WPS: Starting registration protocol",
            "WPS: Received M2",
            "WPS: Timeout waiting for M4");
    CommandResult wpsResult =
        new CommandResult(false, wpsOutput, null, WpsCommand.CommandType.WPA_CLI);

    WpsResult result = new WpsResult(TEST_BSSID, pin, Collections.singletonList(wpsResult));

    assertFalse("Connection should not be successful", result.isSuccess());
    assertTrue("Should detect timeout", result.isTimeout());
  }

  @Test
  public void testLockedConnectionFlow() {
    String pin = "99999999";

    List<String> wpsOutput =
        Arrays.asList(
            "WPS: Starting registration protocol",
            "WPS-PBC-OVERLAP detected",
            "WPS: AP is locked due to too many failed attempts");
    CommandResult wpsResult =
        new CommandResult(false, wpsOutput, null, WpsCommand.CommandType.WPA_CLI);

    WpsResult result = new WpsResult(TEST_BSSID, pin, Collections.singletonList(wpsResult));

    assertFalse("Connection should not be successful", result.isSuccess());
    assertTrue("Should detect locked state", result.isLocked());
  }

  @Test
  public void testWrongPinConnectionFlow() {
    String pin = "00000000";

    List<String> wpsOutput =
        Arrays.asList(
            "WPS: Starting registration protocol",
            "WPS: Received M2",
            "WPS-FAIL msg=8 config_error=18");
    CommandResult wpsResult =
        new CommandResult(false, wpsOutput, null, WpsCommand.CommandType.WPA_CLI);

    WpsResult result = new WpsResult(TEST_BSSID, pin, Collections.singletonList(wpsResult));

    assertFalse("Wrong PIN should fail", result.isSuccess());
    assertTrue("Should detect wrong PIN", result.isWrongPin());
  }

  // ============ Multiple PIN Attempts Flow ============

  @Test
  public void testMultiplePinAttemptFlow() {
    String[] pinsToTry = {"00000000", "12345670", "11111111"};
    WpsResult successResult = null;

    for (int i = 0; i < pinsToTry.length; i++) {
      String pin = pinsToTry[i];
      WpsResult result = simulatePinAttempt(pin, i == 1);

      if (result.isSuccess()) {
        successResult = result;
        break;
      }
    }

    assertNotNull("Should find successful PIN", successResult);
    assertEquals("Successful PIN should be 12345670", "12345670", successResult.getPin());
  }

  private WpsResult simulatePinAttempt(String pin, boolean shouldSucceed) {
    List<String> output;
    if (shouldSucceed) {
      output = Arrays.asList("WPS_SUCCESS", "CTRL-EVENT-CONNECTED", "Password: TestPass123");
    } else {
      output = Arrays.asList("WPS-FAIL msg=8 config_error=18");
    }

    CommandResult cmdResult =
        new CommandResult(shouldSucceed, output, null, WpsCommand.CommandType.WPA_CLI);
    return new WpsResult(TEST_BSSID, pin, Collections.singletonList(cmdResult));
  }

  // ============ Edge Cases ============

  @Test
  public void testEmptyOutputHandling() {
    String pin = "12345670";
    CommandResult emptyResult =
        new CommandResult(false, Collections.emptyList(), null, WpsCommand.CommandType.WPA_CLI);

    WpsResult result = new WpsResult(TEST_BSSID, pin, Collections.singletonList(emptyResult));

    assertFalse("Empty output should not be success", result.isSuccess());
    assertFalse("Empty output should not be timeout", result.isTimeout());
    assertFalse("Empty output should not be locked", result.isLocked());
  }

  @Test
  public void testNullLinesInOutput() {
    String pin = "12345670";
    List<String> output = Arrays.asList("Line 1", null, "WPS_SUCCESS", null);
    CommandResult cmdResult = new CommandResult(true, output, null, WpsCommand.CommandType.WPA_CLI);

    WpsResult result = new WpsResult(TEST_BSSID, pin, Collections.singletonList(cmdResult));

    assertTrue("Should still detect success despite null lines", result.isSuccess());
  }

  @Test
  public void testMixedSuccessAndFailureIndicators() {
    String pin = "12345670";
    List<String> output =
        Arrays.asList(
            "WPS: Warning - weak signal",
            "WPS: Retry...",
            "WPS_SUCCESS",
            "CTRL-EVENT-CONNECTED");
    CommandResult cmdResult = new CommandResult(true, output, null, WpsCommand.CommandType.WPA_CLI);

    WpsResult result = new WpsResult(TEST_BSSID, pin, Collections.singletonList(cmdResult));

    assertTrue("Success should be detected", result.isSuccess());
  }

  // ============ First Half Correct Flow ============

  @Test
  public void testM4FailureIndicatesFirstHalfWrong() {
    List<String> output =
        Arrays.asList("WPS: Received M2", "WSC_NACK after M4", "WPS-FAIL");
    CommandResult cmdResult =
        new CommandResult(false, output, null, WpsCommand.CommandType.WPA_CLI);
    WpsResult result = new WpsResult(TEST_BSSID, "12345670", Collections.singletonList(cmdResult));

    assertFalse("M4 failure means first half wrong", result.isFirstHalfCorrect());
    assertTrue("Should be PIN rejected", result.isPinRejected());
  }

  @Test
  public void testM6FailureIndicatesFirstHalfCorrect() {
    List<String> output =
        Arrays.asList("WPS: Received M4", "WSC_NACK after M6", "WPS-FAIL");
    CommandResult cmdResult =
        new CommandResult(false, output, null, WpsCommand.CommandType.WPA_CLI);
    WpsResult result = new WpsResult(TEST_BSSID, "12345670", Collections.singletonList(cmdResult));

    assertTrue("M6 failure means first half correct", result.isFirstHalfCorrect());
  }
}
