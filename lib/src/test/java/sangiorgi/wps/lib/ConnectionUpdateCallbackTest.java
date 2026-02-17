package sangiorgi.wps.lib;

import static org.junit.Assert.*;

import org.junit.Test;
import sangiorgi.wps.lib.models.NetworkToTest;

public class ConnectionUpdateCallbackTest {

  @Test
  public void testConstantValues() {
    assertEquals(0, ConnectionUpdateCallback.TYPE_LOCKED);
    assertEquals(1, ConnectionUpdateCallback.TYPE_SELINUX);
    assertEquals(3, ConnectionUpdateCallback.TYPE_PIXIE_DUST_NOT_COMPATIBLE);
  }

  @Test
  public void testDefaultMethodsDoNotThrow() {
    // Verify default methods can be called without exception
    ConnectionUpdateCallback callback =
        new ConnectionUpdateCallback() {
          @Override
          public void create(String title, String message, int progress) {}

          @Override
          public void updateMessage(String message) {}

          @Override
          public void updateCount(int increment) {}

          @Override
          public void error(String message, int type) {}

          @Override
          public void success(NetworkToTest networkToTest, boolean isRoot) {}
        };

    // Default methods should not throw
    callback.onPixieDustSuccess("12345670", "password");
    callback.onPixieDustFailure("error");
  }
}
