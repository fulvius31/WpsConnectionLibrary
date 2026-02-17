package sangiorgi.wps.lib.models;

import static org.junit.Assert.*;

import org.junit.Test;

public class PixieDustParametersTest {

  private static final String PKE = "0123456789abcdef";
  private static final String PKR = "fedcba9876543210";
  private static final String EHASH1 = "aaaa1111bbbb2222";
  private static final String EHASH2 = "cccc3333dddd4444";
  private static final String AUTH_KEY = "abcdef0123456789";
  private static final String ENONCE = "1234abcd5678efab";

  private PixieDustParameters createValidParams() {
    return new PixieDustParameters(PKE, PKR, EHASH1, EHASH2, AUTH_KEY, ENONCE);
  }

  @Test
  public void testGetters() {
    PixieDustParameters params = createValidParams();
    assertEquals(PKE, params.getPke());
    assertEquals(PKR, params.getPkr());
    assertEquals(EHASH1, params.getEhash1());
    assertEquals(EHASH2, params.getEhash2());
    assertEquals(AUTH_KEY, params.getAuthKey());
    assertEquals(ENONCE, params.getEnonce());
  }

  @Test
  public void testIsValidWithAllFields() {
    PixieDustParameters params = createValidParams();
    assertTrue(params.isValid());
  }

  @Test
  public void testIsValidWithNullPke() {
    PixieDustParameters params = new PixieDustParameters(null, PKR, EHASH1, EHASH2, AUTH_KEY, ENONCE);
    assertFalse(params.isValid());
  }

  @Test
  public void testIsValidWithEmptyPke() {
    PixieDustParameters params = new PixieDustParameters("", PKR, EHASH1, EHASH2, AUTH_KEY, ENONCE);
    assertFalse(params.isValid());
  }

  @Test
  public void testIsValidWithNullPkr() {
    PixieDustParameters params = new PixieDustParameters(PKE, null, EHASH1, EHASH2, AUTH_KEY, ENONCE);
    assertFalse(params.isValid());
  }

  @Test
  public void testIsValidWithNullEhash1() {
    PixieDustParameters params = new PixieDustParameters(PKE, PKR, null, EHASH2, AUTH_KEY, ENONCE);
    assertFalse(params.isValid());
  }

  @Test
  public void testIsValidWithNullEhash2() {
    PixieDustParameters params = new PixieDustParameters(PKE, PKR, EHASH1, null, AUTH_KEY, ENONCE);
    assertFalse(params.isValid());
  }

  @Test
  public void testIsValidWithNullAuthKey() {
    PixieDustParameters params = new PixieDustParameters(PKE, PKR, EHASH1, EHASH2, null, ENONCE);
    assertFalse(params.isValid());
  }

  @Test
  public void testIsValidWithNullEnonce() {
    PixieDustParameters params = new PixieDustParameters(PKE, PKR, EHASH1, EHASH2, AUTH_KEY, null);
    assertFalse(params.isValid());
  }

  @Test
  public void testIsValidWithEmptyAuthKey() {
    PixieDustParameters params = new PixieDustParameters(PKE, PKR, EHASH1, EHASH2, "", ENONCE);
    assertFalse(params.isValid());
  }

  @Test
  public void testToStringFormat() {
    PixieDustParameters params = createValidParams();
    String str = params.toString();

    assertTrue(str.contains("--pke " + PKE));
    assertTrue(str.contains("--pkr " + PKR));
    assertTrue(str.contains("--e-hash1 " + EHASH1));
    assertTrue(str.contains("--e-hash2 " + EHASH2));
    assertTrue(str.contains("--authkey " + AUTH_KEY));
    assertTrue(str.contains("--e-nonce " + ENONCE));
  }

  @Test
  public void testToStringOrderedCorrectly() {
    PixieDustParameters params = createValidParams();
    String str = params.toString();

    // Verify the order: pke, pkr, e-hash1, e-hash2, authkey, e-nonce
    int pkeIdx = str.indexOf("--pke");
    int pkrIdx = str.indexOf("--pkr");
    int ehash1Idx = str.indexOf("--e-hash1");
    int ehash2Idx = str.indexOf("--e-hash2");
    int authkeyIdx = str.indexOf("--authkey");
    int enonceIdx = str.indexOf("--e-nonce");

    assertTrue(pkeIdx < pkrIdx);
    assertTrue(pkrIdx < ehash1Idx);
    assertTrue(ehash1Idx < ehash2Idx);
    assertTrue(ehash2Idx < authkeyIdx);
    assertTrue(authkeyIdx < enonceIdx);
  }
}
