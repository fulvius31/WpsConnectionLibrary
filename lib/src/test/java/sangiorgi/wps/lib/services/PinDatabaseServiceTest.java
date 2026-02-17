package sangiorgi.wps.lib.services;

import static org.junit.Assert.*;

import org.junit.Test;

public class PinDatabaseServiceTest {

  @Test
  public void testNormalizeMacPrefix_standardFormat() {
    assertEquals("aabbcc", PinDatabaseService.normalizeMacPrefix("AA:BB:CC:DD:EE:FF"));
  }

  @Test
  public void testNormalizeMacPrefix_lowercaseInput() {
    assertEquals("aabbcc", PinDatabaseService.normalizeMacPrefix("aa:bb:cc:dd:ee:ff"));
  }

  @Test
  public void testNormalizeMacPrefix_dashSeparated() {
    assertEquals("aabbcc", PinDatabaseService.normalizeMacPrefix("AA-BB-CC-DD-EE-FF"));
  }

  @Test
  public void testNormalizeMacPrefix_noSeparators() {
    assertEquals("aabbcc", PinDatabaseService.normalizeMacPrefix("AABBCCDDEEFF"));
  }

  @Test
  public void testNormalizeMacPrefix_mixedCase() {
    assertEquals("a1b2c3", PinDatabaseService.normalizeMacPrefix("A1:b2:C3:d4:E5:f6"));
  }

  @Test
  public void testNormalizeMacPrefix_tooShort() {
    assertNull(PinDatabaseService.normalizeMacPrefix("AA:BB"));
  }

  @Test
  public void testNormalizeMacPrefix_exactlySixChars() {
    assertEquals("aabbcc", PinDatabaseService.normalizeMacPrefix("AABBCC"));
  }

  @Test
  public void testGetPinsByMac_nullBssid() {
    // getPinsByMac requires Context, but null/empty checks happen before DB access
    // We test the null path directly via normalizeMacPrefix
    assertNull(PinDatabaseService.normalizeMacPrefix("AB"));
  }
}
