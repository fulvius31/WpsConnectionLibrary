package sangiorgi.wps.lib;

import sangiorgi.wps.lib.models.NetworkToTest;

public interface ConnectionUpdateCallback {

  int TYPE_LOCKED = 0;
  int TYPE_SELINUX = 1;
  int TYPE_PIXIE_DUST_NOT_COMPATIBLE = 3;

  void create(String title, String message, int progress);

  void updateMessage(String message);

  void updateCount(int increment);

  void error(String message, int type);

  void success(NetworkToTest networkToTest, boolean isRoot);

  // Additional callbacks for improved pattern
  default void onPixieDustSuccess(String pin, String password) {}

  default void onPixieDustFailure(String error) {}
}
