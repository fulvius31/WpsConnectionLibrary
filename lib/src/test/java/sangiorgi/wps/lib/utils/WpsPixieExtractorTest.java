package sangiorgi.wps.lib.utils;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import sangiorgi.wps.lib.models.PixieDustParameters;

public class WpsPixieExtractorTest {

  private static final String ENROLLEE_NONCE_LINE =
      "WPS: Enrollee Nonce (hexdump): a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4";
  private static final String DH_OWN_PUBLIC_LINE =
      "WPS: DH own Public Key (hexdump): 0123456789abcdef0123456789abcdef";
  private static final String DH_PEER_PUBLIC_LINE =
      "WPS: DH peer Public Key (hexdump): fedcba9876543210fedcba9876543210";
  private static final String AUTH_KEY_LINE =
      "WPS: AuthKey (hexdump): 1111222233334444555566667777888899990000aaaabbbbccccddddeeee";
  private static final String E_HASH1_LINE =
      "WPS: E-Hash1 (hexdump): aaaa1111bbbb2222ccccdddd";
  private static final String E_HASH2_LINE =
      "WPS: E-Hash2 (hexdump): 2222333344445555666677778888";

  private List<String> allParameterLines() {
    return Arrays.asList(
        "Starting WPS",
        ENROLLEE_NONCE_LINE,
        DH_OWN_PUBLIC_LINE,
        DH_PEER_PUBLIC_LINE,
        AUTH_KEY_LINE,
        E_HASH1_LINE,
        E_HASH2_LINE,
        "Done");
  }

  // ============ hasPixieParameters ============

  @Test
  public void testHasPixieParametersWithAllParams() {
    assertTrue(WpsPixieExtractor.hasPixieParameters(allParameterLines()));
  }

  @Test
  public void testHasPixieParametersMissingOne() {
    List<String> output =
        Arrays.asList(
            ENROLLEE_NONCE_LINE,
            DH_OWN_PUBLIC_LINE,
            DH_PEER_PUBLIC_LINE,
            AUTH_KEY_LINE,
            E_HASH1_LINE
            // Missing E_HASH2
            );

    assertFalse(WpsPixieExtractor.hasPixieParameters(output));
  }

  @Test
  public void testHasPixieParametersEmpty() {
    assertFalse(WpsPixieExtractor.hasPixieParameters(Collections.emptyList()));
  }

  @Test
  public void testHasPixieParametersNullLines() {
    List<String> output = Arrays.asList(null, "some text", null);
    assertFalse(WpsPixieExtractor.hasPixieParameters(output));
  }

  @Test
  public void testHasPixieParametersNoHexdump() {
    List<String> output =
        Arrays.asList("Enrollee Nonce: abc", "DH own Public Key: def");
    assertFalse(WpsPixieExtractor.hasPixieParameters(output));
  }

  @Test
  public void testHasPixieParametersWithSpacedHex() {
    List<String> output =
        Arrays.asList(
            "WPS: Enrollee Nonce (hexdump): a1 b2 c3 d4 e5 f6 a1 b2 c3 d4 e5 f6 a1 b2 c3 d4",
            "WPS: DH own Public Key (hexdump): 01 23 45 67 89 ab cd ef",
            "WPS: DH peer Public Key (hexdump): fe dc ba 98 76 54 32 10",
            "WPS: AuthKey (hexdump): 11 22 33 44 55 66 77 88",
            "WPS: E-Hash1 (hexdump): aa bb cc dd ee ff 00 11",
            "WPS: E-Hash2 (hexdump): 22 33 44 55 66 77 88 99");

    assertTrue(WpsPixieExtractor.hasPixieParameters(output));
  }

  // ============ extractParameters ============

  @Test
  public void testExtractParametersSuccess() {
    PixieDustParameters params = WpsPixieExtractor.extractParameters(allParameterLines());

    assertNotNull(params);
    assertTrue(params.isValid());

    // DH peer = PKE, DH own = PKR
    assertEquals("fedcba9876543210fedcba9876543210", params.getPke());
    assertEquals("0123456789abcdef0123456789abcdef", params.getPkr());
    assertEquals("aaaa1111bbbb2222ccccdddd", params.getEhash1());
    assertEquals("2222333344445555666677778888", params.getEhash2());
    assertEquals(
        "1111222233334444555566667777888899990000aaaabbbbccccddddeeee", params.getAuthKey());
    assertEquals("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4", params.getEnonce());
  }

  @Test
  public void testExtractParametersMissingOne() {
    List<String> output =
        Arrays.asList(
            ENROLLEE_NONCE_LINE,
            DH_OWN_PUBLIC_LINE,
            DH_PEER_PUBLIC_LINE,
            AUTH_KEY_LINE,
            E_HASH1_LINE);

    PixieDustParameters params = WpsPixieExtractor.extractParameters(output);
    assertNull("Should return null if any parameter is missing", params);
  }

  @Test
  public void testExtractParametersEmpty() {
    PixieDustParameters params =
        WpsPixieExtractor.extractParameters(Collections.emptyList());
    assertNull(params);
  }

  @Test
  public void testExtractParametersWithSpacedHex() {
    List<String> output =
        Arrays.asList(
            "WPS: Enrollee Nonce (hexdump): a1 b2 c3 d4",
            "WPS: DH own Public Key (hexdump): 01 23 45 67",
            "WPS: DH peer Public Key (hexdump): fe dc ba 98",
            "WPS: AuthKey (hexdump): 11 22 33 44",
            "WPS: E-Hash1 (hexdump): aa bb cc dd",
            "WPS: E-Hash2 (hexdump): 22 33 44 55");

    PixieDustParameters params = WpsPixieExtractor.extractParameters(output);

    assertNotNull(params);
    // Hex spaces should be removed
    assertEquals("fedcba98", params.getPke());
    assertEquals("01234567", params.getPkr());
    assertEquals("aabbccdd", params.getEhash1());
    assertEquals("22334455", params.getEhash2());
    assertEquals("11223344", params.getAuthKey());
    assertEquals("a1b2c3d4", params.getEnonce());
  }

  @Test
  public void testExtractParametersNullLineSkipped() {
    List<String> output =
        Arrays.asList(
            null,
            ENROLLEE_NONCE_LINE,
            null,
            DH_OWN_PUBLIC_LINE,
            DH_PEER_PUBLIC_LINE,
            AUTH_KEY_LINE,
            E_HASH1_LINE,
            E_HASH2_LINE);

    PixieDustParameters params = WpsPixieExtractor.extractParameters(output);
    assertNotNull("Should handle null lines gracefully", params);
    assertTrue(params.isValid());
  }

  // ============ Real World Output ============

  @Test
  public void testRealWorldOutput() {
    List<String> realOutput =
        Arrays.asList(
            "wlan0: WPS-REG-RECEIVED version=0x10",
            "WPS: Received M1",
            "WPS: Enrollee Nonce (hexdump): d7 e8 f9 a0 b1 c2 d3 e4 f5 06 17 28 39 4a 5b 6c",
            "WPS: UUID-E (hexdump): 12345678-1234-1234-1234-123456789abc",
            "WPS: Public Key (hexdump): ignored",
            "WPS: DH own Public Key (hexdump): ab cd ef 01 23 45 67 89",
            "WPS: Building Message M2",
            "WPS: DH peer Public Key (hexdump): 98 76 54 32 10 fe dc ba",
            "WPS: KDK (hexdump): ignored",
            "WPS: AuthKey (hexdump): aa bb cc dd ee ff 00 11 22 33 44 55",
            "WPS: KeyWrapKey (hexdump): ignored",
            "WPS: E-Hash1 (hexdump): 11 22 33 44 55 66 77 88",
            "WPS: E-Hash2 (hexdump): ff ee dd cc bb aa 99 88",
            "WPS: Received M3");

    assertTrue(WpsPixieExtractor.hasPixieParameters(realOutput));
    PixieDustParameters params = WpsPixieExtractor.extractParameters(realOutput);
    assertNotNull(params);
    assertTrue(params.isValid());
  }

  @Test
  public void testLineWithoutHexdumpKeyword() {
    // Lines without "hexdump" should be ignored
    List<String> output =
        Arrays.asList(
            "WPS: Enrollee Nonce: a1b2c3d4", // No "hexdump"
            "WPS: DH own Public Key: 01234567"); // No "hexdump"

    assertFalse(WpsPixieExtractor.hasPixieParameters(output));
  }
}
