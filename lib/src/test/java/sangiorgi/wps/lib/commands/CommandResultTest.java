package sangiorgi.wps.lib.commands;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class CommandResultTest {

  @Test
  public void testSuccessResult() {
    List<String> output = Arrays.asList("line1", "line2");
    CommandResult result = new CommandResult(true, output, null, WpsCommand.CommandType.WPA_CLI);

    assertTrue(result.isSuccess());
    assertEquals(output, result.getOutput());
    assertTrue(result.hasOutput());
  }

  @Test
  public void testFailureResult() {
    List<String> errors = Arrays.asList("error1", "error2");
    CommandResult result =
        new CommandResult(false, Collections.emptyList(), errors, WpsCommand.CommandType.WPA_CLI);

    assertFalse(result.isSuccess());
    assertFalse(result.hasOutput());
  }

  @Test
  public void testGetOutputAsString() {
    List<String> output = Arrays.asList("line1", "line2", "line3");
    CommandResult result = new CommandResult(true, output, null, WpsCommand.CommandType.WPA_CLI);

    assertEquals("line1\nline2\nline3", result.getOutputAsString());
  }

  @Test
  public void testGetOutputAsStringNullOutput() {
    CommandResult result = new CommandResult(true, null, null, WpsCommand.CommandType.WPA_CLI);

    assertEquals("", result.getOutputAsString());
  }

  @Test
  public void testGetErrorsAsString() {
    List<String> errors = Arrays.asList("err1", "err2");
    CommandResult result = new CommandResult(false, null, errors, WpsCommand.CommandType.WPA_CLI);

    assertEquals("err1\nerr2", result.getErrorsAsString());
  }

  @Test
  public void testGetErrorsAsStringNullErrors() {
    CommandResult result = new CommandResult(false, null, null, WpsCommand.CommandType.WPA_CLI);

    assertEquals("", result.getErrorsAsString());
  }

  @Test
  public void testHasOutputWithNullOutput() {
    CommandResult result = new CommandResult(true, null, null, WpsCommand.CommandType.WPA_CLI);

    assertFalse(result.hasOutput());
  }

  @Test
  public void testHasOutputWithEmptyList() {
    CommandResult result =
        new CommandResult(true, Collections.emptyList(), null, WpsCommand.CommandType.WPA_CLI);

    assertFalse(result.hasOutput());
  }

  @Test
  public void testHasOutputWithContent() {
    CommandResult result =
        new CommandResult(
            true, Collections.singletonList("output"), null, WpsCommand.CommandType.WPA_CLI);

    assertTrue(result.hasOutput());
  }

  @Test
  public void testCommandTypeIgnored() {
    // CommandType is accepted but ignored in the constructor
    CommandResult result1 =
        new CommandResult(true, null, null, WpsCommand.CommandType.WPA_CLI);
    CommandResult result2 =
        new CommandResult(true, null, null, WpsCommand.CommandType.WPA_SUPPLICANT);
    CommandResult result3 =
        new CommandResult(true, null, null, WpsCommand.CommandType.GLOBAL_CONTROL);
    CommandResult result4 =
        new CommandResult(true, null, null, WpsCommand.CommandType.PIXIE_DUST);

    // All should work regardless of command type
    assertTrue(result1.isSuccess());
    assertTrue(result2.isSuccess());
    assertTrue(result3.isSuccess());
    assertTrue(result4.isSuccess());
  }
}
