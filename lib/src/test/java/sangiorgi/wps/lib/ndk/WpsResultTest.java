package sangiorgi.wps.lib.ndk;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for the NDK WpsResult class.
 * Tests the native result type that is constructed from JNI.
 */
public class WpsResultTest {

    @Test
    public void testSuccessStatus() {
        WpsResult result = new WpsResult(0, "MyPassword123", "Network Key: ...", null);
        assertEquals(WpsResult.Status.SUCCESS, result.getStatus());
        assertTrue(result.isSuccess());
        assertEquals("MyPassword123", result.getNetworkKey());
        assertEquals("Network Key: ...", result.getRawLine());
    }

    @Test
    public void testFourFailStatus() {
        WpsResult result = new WpsResult(1, null, "WPS-FAIL msg=8 config_error=18", null);
        assertEquals(WpsResult.Status.FOUR_FAIL, result.getStatus());
        assertFalse(result.isSuccess());
        assertNull(result.getNetworkKey());
    }

    @Test
    public void testThreeFailStatus() {
        WpsResult result = new WpsResult(2, null, "WPS-FAIL msg=8", null);
        assertEquals(WpsResult.Status.THREE_FAIL, result.getStatus());
        assertFalse(result.isSuccess());
    }

    @Test
    public void testLockedStatus() {
        WpsResult result = new WpsResult(3, null, "WPS-FAIL config_error=15", null);
        assertEquals(WpsResult.Status.LOCKED, result.getStatus());
        assertFalse(result.isSuccess());
    }

    @Test
    public void testCrcFailStatus() {
        WpsResult result = new WpsResult(4, null, "CRC failure", null);
        assertEquals(WpsResult.Status.CRC_FAIL, result.getStatus());
        assertFalse(result.isSuccess());
    }

    @Test
    public void testSelinuxStatus() {
        WpsResult result = new WpsResult(5, null, "SELinux denied", null);
        assertEquals(WpsResult.Status.SELINUX, result.getStatus());
        assertFalse(result.isSuccess());
    }

    @Test
    public void testTimeoutStatus() {
        WpsResult result = new WpsResult(6, null, null, null);
        assertEquals(WpsResult.Status.TIMEOUT, result.getStatus());
        assertFalse(result.isSuccess());
        assertNull(result.getNetworkKey());
        assertNull(result.getRawLine());
    }

    @Test
    public void testErrorStatus() {
        WpsResult result = new WpsResult(7, null, "Unknown error", null);
        assertEquals(WpsResult.Status.ERROR, result.getStatus());
        assertFalse(result.isSuccess());
    }

    @Test
    public void testUnknownStatusCodeMapsToError() {
        WpsResult result = new WpsResult(99, null, null, null);
        assertEquals(WpsResult.Status.ERROR, result.getStatus());
        assertFalse(result.isSuccess());
    }

    @Test
    public void testNegativeStatusCodeMapsToError() {
        WpsResult result = new WpsResult(-1, null, null, null);
        assertEquals(WpsResult.Status.ERROR, result.getStatus());
    }

    @Test
    public void testSuccessWithNullNetworkKey() {
        // Success status but no key extracted yet
        WpsResult result = new WpsResult(0, null, "WPS-SUCCESS", null);
        assertTrue(result.isSuccess());
        assertNull(result.getNetworkKey());
    }

    @Test
    public void testStatusFromCodeAllValues() {
        assertEquals(WpsResult.Status.SUCCESS, WpsResult.Status.fromCode(0));
        assertEquals(WpsResult.Status.FOUR_FAIL, WpsResult.Status.fromCode(1));
        assertEquals(WpsResult.Status.THREE_FAIL, WpsResult.Status.fromCode(2));
        assertEquals(WpsResult.Status.LOCKED, WpsResult.Status.fromCode(3));
        assertEquals(WpsResult.Status.CRC_FAIL, WpsResult.Status.fromCode(4));
        assertEquals(WpsResult.Status.SELINUX, WpsResult.Status.fromCode(5));
        assertEquals(WpsResult.Status.TIMEOUT, WpsResult.Status.fromCode(6));
        assertEquals(WpsResult.Status.ERROR, WpsResult.Status.fromCode(7));
        assertEquals(WpsResult.Status.ERROR, WpsResult.Status.fromCode(100));
    }

    @Test
    public void testToString() {
        WpsResult result = new WpsResult(0, "pass123", "raw line", null);
        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("SUCCESS"));
        assertTrue(str.contains("[present]")); // networkKey is present
        assertTrue(str.contains("raw line"));
    }

    @Test
    public void testExchangeLog() {
        WpsResult result = new WpsResult(0, "pass", "raw", "WPS: Enrollee Nonce - hexdump");
        assertEquals("WPS: Enrollee Nonce - hexdump", result.getExchangeLog());
    }

    @Test
    public void testExchangeLogNull() {
        WpsResult result = new WpsResult(0, "pass", "raw", null);
        assertNull(result.getExchangeLog());
    }

    @Test
    public void testToStringNullKey() {
        WpsResult result = new WpsResult(6, null, null, null);
        String str = result.toString();
        assertTrue(str.contains("TIMEOUT"));
        assertTrue(str.contains("null"));
    }
}
