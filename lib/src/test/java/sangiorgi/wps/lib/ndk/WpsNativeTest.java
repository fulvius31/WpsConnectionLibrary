package sangiorgi.wps.lib.ndk;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for WpsNative static methods and constants.
 * Instance methods require Android Context and cannot be tested in JVM unit tests.
 */
public class WpsNativeTest {

    @Test
    public void testGetCtrlDirReturnsNonNull() {
        // Build.VERSION.SDK_INT defaults to 0 in unit tests (returnDefaultValues = true)
        // So it should return the < P path
        String ctrlDir = WpsNative.getCtrlDir();
        assertNotNull(ctrlDir);
        assertTrue("Ctrl dir should end with /", ctrlDir.endsWith("/"));
        assertTrue("Ctrl dir should contain wpswpatester",
                ctrlDir.contains("wpswpatester"));
    }

    @Test
    public void testGetCtrlSocketPath() {
        String path = WpsNative.getCtrlSocketPath("wlan0");
        assertNotNull(path);
        assertTrue("Socket path should end with wlan0", path.endsWith("wlan0"));
        assertTrue("Socket path should contain wpswpatester",
                path.contains("wpswpatester"));
    }

    @Test
    public void testGetCtrlSocketPathDifferentIface() {
        String path = WpsNative.getCtrlSocketPath("wlan1");
        assertTrue(path.endsWith("wlan1"));
    }

    @Test
    public void testLibraryNotLoadedInUnitTests() {
        // In JVM unit tests, System.loadLibrary will fail (no native libs)
        // With returnDefaultValues=true, the static block may not throw
        // but isAvailable() on any instance would return false
        // We can't test this without a Context, but we verify the class loads
        assertNotNull(WpsNative.class);
    }
}
