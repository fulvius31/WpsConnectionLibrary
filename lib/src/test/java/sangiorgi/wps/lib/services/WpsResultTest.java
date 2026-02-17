package sangiorgi.wps.lib.services;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import sangiorgi.wps.lib.commands.CommandResult;
import sangiorgi.wps.lib.commands.WpsCommand;

public class WpsResultTest {

  private static final String TEST_BSSID = "AA:BB:CC:DD:EE:FF";
  private static final String TEST_PIN = "12345670";

  private CommandResult makeResult(boolean success, List<String> output, List<String> errors) {
    return new CommandResult(success, output, errors, WpsCommand.CommandType.WPA_CLI);
  }

  // ============ Success Detection ============

  @Test
  public void testSuccessWithWpsSuccess() {
    CommandResult cmdResult =
        makeResult(true, Arrays.asList("WPS: Processing", "WPS_SUCCESS", "Done"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isSuccess());
    assertFalse(result.isTimeout());
    assertFalse(result.isLocked());
  }

  @Test
  public void testSuccessWithWpsHyphenSuccess() {
    CommandResult cmdResult =
        makeResult(true, Arrays.asList("WPS-SUCCESS"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isSuccess());
  }

  @Test
  public void testSuccessWithConnected() {
    CommandResult cmdResult =
        makeResult(true, Arrays.asList("CTRL-EVENT-CONNECTED"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isSuccess());
  }

  @Test
  public void testSuccessWithKeyNegotiationCompleted() {
    CommandResult cmdResult =
        makeResult(true, Arrays.asList("key negotiation completed"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isSuccess());
  }

  @Test
  public void testSuccessWithNetworkKey() {
    CommandResult cmdResult =
        makeResult(true, Arrays.asList("Network Key received"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isSuccess());
  }

  @Test
  public void testSuccessRequiresCommandSuccess() {
    // Even with success indicator, command must report success
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WPS_SUCCESS"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertFalse(result.isSuccess());
  }

  @Test
  public void testCaseInsensitiveSuccessDetection() {
    CommandResult cmdResult =
        makeResult(true, Arrays.asList("wps_success", "ctrl-event-connected"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isSuccess());
  }

  // ============ Timeout Detection ============

  @Test
  public void testTimeoutInOutput() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WPS: timeout waiting for response"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isTimeout());
  }

  @Test
  public void testTimeoutInErrors() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("Starting"), Arrays.asList("Operation timeout"));
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isTimeout());
  }

  @Test
  public void testTimeoutNotReportedWhenWpsFail() {
    // If WPS-FAIL is present, timeout should not be reported even if timeout text is there
    CommandResult cmdResult =
        makeResult(
            false,
            Arrays.asList("WPS-FAIL msg=8 config_error=18", "timeout in some context"),
            null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertFalse("Timeout should not be reported when WPS-FAIL is present", result.isTimeout());
  }

  // ============ Locked Detection ============

  @Test
  public void testLockedWithOverlap() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WPS-PBC-OVERLAP detected"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isLocked());
  }

  @Test
  public void testLockedWithSetupLocked() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("Setup Locked on this router"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isLocked());
  }

  @Test
  public void testLockedWithConfigError15() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WPS-FAIL msg=4 config_error=15"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isLocked());
  }

  @Test
  public void testLockedWithWpsOverlap() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WPS_OVERLAP detected"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isLocked());
  }

  // ============ Static Locked Indicator ============

  @Test
  public void testIsWpsLockedIndicatorNull() {
    assertFalse(WpsResult.isWpsLockedIndicator(null));
  }

  @Test
  public void testIsWpsLockedIndicatorOverlap() {
    assertTrue(WpsResult.isWpsLockedIndicator("WPS-PBC-OVERLAP"));
  }

  @Test
  public void testIsWpsLockedIndicatorSetupLocked() {
    assertTrue(WpsResult.isWpsLockedIndicator("Setup Locked"));
  }

  @Test
  public void testIsWpsLockedIndicatorConfigError15() {
    assertTrue(WpsResult.isWpsLockedIndicator("WPS-FAIL config_error=15"));
  }

  @Test
  public void testIsWpsLockedIndicatorNormalLine() {
    assertFalse(WpsResult.isWpsLockedIndicator("WPS: Received M2"));
  }

  // ============ First Half Correct ============

  @Test
  public void testFirstHalfCorrectWithM6() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WSC_NACK after M6"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isFirstHalfCorrect());
  }

  @Test
  public void testFirstHalfNotCorrectWithM4() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WSC_NACK after M4"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertFalse(result.isFirstHalfCorrect());
  }

  @Test
  public void testFirstHalfCorrectDefault() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("Some other error"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertFalse(result.isFirstHalfCorrect());
  }

  // ============ Wrong PIN ============

  @Test
  public void testWrongPinWithConfigError18() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WPS-FAIL config_error=18"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isWrongPin());
  }

  @Test
  public void testWrongPinWithMsg8() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WPS-FAIL msg=8"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isWrongPin());
  }

  // ============ PIN Rejected ============

  @Test
  public void testPinRejectedWithWpsFail() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WPS-FAIL msg=4"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isPinRejected());
  }

  @Test
  public void testPinRejectedWithNack() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WSC_NACK received"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isPinRejected());
  }

  @Test
  public void testPinRejectedWithM2D() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("Received M2D"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isPinRejected());
  }

  @Test
  public void testPinRejectedWithEapFailure() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("EAP failure received"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue(result.isPinRejected());
  }

  // ============ Failure Reason ============

  @Test
  public void testFailureReasonLocked() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WPS-PBC-OVERLAP"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertEquals("WPS is locked on this router", result.getFailureReason());
  }

  @Test
  public void testFailureReasonWrongPin() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WPS-FAIL config_error=18"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertEquals("Wrong PIN", result.getFailureReason());
  }

  @Test
  public void testFailureReasonTimeout() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("Connection timeout"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertEquals("Connection timed out", result.getFailureReason());
  }

  @Test
  public void testFailureReasonPinRejected() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("WSC_NACK received"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertEquals("PIN rejected", result.getFailureReason());
  }

  @Test
  public void testFailureReasonDefault() {
    CommandResult cmdResult =
        makeResult(false, Arrays.asList("Unknown error"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertEquals("Connection failed", result.getFailureReason());
  }

  // ============ Password Extraction ============

  @Test
  public void testPasswordExtractionWithPasswordColon() {
    CommandResult cmdResult =
        makeResult(true, Arrays.asList("WPS_SUCCESS", "Password: MySecretPass"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertEquals("MySecretPass", result.getPassword());
  }

  @Test
  public void testPasswordExtractionWithPsk() {
    CommandResult cmdResult =
        makeResult(true, Arrays.asList("WPS_SUCCESS", "wpa_psk=NetworkPass456"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertEquals("NetworkPass456", result.getPassword());
  }

  @Test
  public void testPasswordExtractionWithPSKUppercase() {
    CommandResult cmdResult =
        makeResult(true, Arrays.asList("WPS_SUCCESS", "PSK: MyPSKPassword"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertEquals("MyPSKPassword", result.getPassword());
  }

  @Test
  public void testPasswordExtractionWithNetworkKeyHexdump() {
    // Hex for "Password" = 50 61 73 73 77 6f 72 64
    // Production code looks for "Network Key" AND "hexdump(" with "): " as separator
    CommandResult cmdResult =
        makeResult(
            true,
            Arrays.asList(
                "WPS_SUCCESS",
                "WPS: Network Key hexdump(len=8): 50617373776f7264"),
            null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertEquals("Password", result.getPassword());
  }

  @Test
  public void testNoPassword() {
    CommandResult cmdResult =
        makeResult(true, Arrays.asList("WPS_SUCCESS", "Connected"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertNull(result.getPassword());
  }

  @Test
  public void testSetPassword() {
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.emptyList());
    assertNull(result.getPassword());

    result.setPassword("ManualPassword");
    assertEquals("ManualPassword", result.getPassword());
  }

  @Test
  public void testPasswordExtractionSkipsNullLines() {
    CommandResult cmdResult =
        makeResult(true, Arrays.asList("WPS_SUCCESS", null, "Password: Secret"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertEquals("Secret", result.getPassword());
  }

  // ============ Getters ============

  @Test
  public void testGetBssid() {
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.emptyList());
    assertEquals(TEST_BSSID, result.getBssid());
  }

  @Test
  public void testGetPin() {
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.emptyList());
    assertEquals(TEST_PIN, result.getPin());
  }

  @Test
  public void testGetCommandResults() {
    CommandResult cmdResult =
        makeResult(true, Arrays.asList("output"), null);
    List<CommandResult> results = Collections.singletonList(cmdResult);
    WpsResult wpsResult = new WpsResult(TEST_BSSID, TEST_PIN, results);

    assertEquals(results, wpsResult.getCommandResults());
    assertEquals(results, wpsResult.getResults());
  }

  // ============ All Output ============

  @Test
  public void testGetAllOutput() {
    CommandResult cmd1 =
        new CommandResult(
            true, Arrays.asList("Line 1", "Line 2"), null, WpsCommand.CommandType.WPA_SUPPLICANT);
    CommandResult cmd2 =
        new CommandResult(
            true, Arrays.asList("Line 3", "Line 4"), null, WpsCommand.CommandType.WPA_CLI);

    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Arrays.asList(cmd1, cmd2));
    String allOutput = result.getAllOutput();

    assertTrue(allOutput.contains("Line 1"));
    assertTrue(allOutput.contains("Line 2"));
    assertTrue(allOutput.contains("Line 3"));
    assertTrue(allOutput.contains("Line 4"));
  }

  @Test
  public void testGetOutputAsStringSameAsGetAllOutput() {
    CommandResult cmdResult = makeResult(true, Arrays.asList("test output"), null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertEquals(result.getAllOutput(), result.getOutputAsString());
  }

  // ============ Edge Cases ============

  @Test
  public void testEmptyResults() {
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.emptyList());

    assertFalse(result.isSuccess());
    assertFalse(result.isTimeout());
    assertFalse(result.isLocked());
    assertFalse(result.isWrongPin());
    assertFalse(result.isPinRejected());
    assertNull(result.getPassword());
  }

  @Test(expected = NullPointerException.class)
  public void testNullResultsThrowsNPE() {
    // extractPassword() iterates over commandResults without null check
    new WpsResult(TEST_BSSID, TEST_PIN, null);
  }

  @Test
  public void testMultipleCommandResultsSuccess() {
    CommandResult cmd1 =
        new CommandResult(
            true, Arrays.asList("Starting WPS"), null, WpsCommand.CommandType.WPA_SUPPLICANT);
    CommandResult cmd2 =
        new CommandResult(
            true, Arrays.asList("WPS_SUCCESS"), null, WpsCommand.CommandType.WPA_CLI);

    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Arrays.asList(cmd1, cmd2));

    assertTrue("Should find success in second command", result.isSuccess());
  }

  @Test
  public void testMixedSuccessAndFailure() {
    CommandResult cmdResult =
        makeResult(
            true,
            Arrays.asList("WPS: timeout on first attempt", "WPS: Retry", "WPS_SUCCESS"),
            null);
    WpsResult result = new WpsResult(TEST_BSSID, TEST_PIN, Collections.singletonList(cmdResult));

    assertTrue("Success should be detected", result.isSuccess());
  }
}
