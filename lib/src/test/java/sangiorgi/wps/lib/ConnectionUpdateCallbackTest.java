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
    callback.onPixieDustSuccess("12345670", "password", "exchange log");
    callback.onPixieDustFailure("error");
  }

  @Test
  public void testThreeArgPixieDustSuccessDelegatesToTwoArg() {
    final boolean[] called = {false};
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
          @Override
          public void onPixieDustSuccess(String pin, String password) {
            called[0] = true;
            assertEquals("12345670", pin);
            assertEquals("password", password);
          }
        };

    callback.onPixieDustSuccess("12345670", "password", "log data");
    assertTrue("3-arg overload should delegate to 2-arg", called[0]);
  }
}
