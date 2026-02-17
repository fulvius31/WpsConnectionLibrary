package sangiorgi.wps.lib.assets;

import android.content.Context;
import java.io.File;

public class WpaToolsPaths {
  private final File filesDir;

  public WpaToolsPaths(Context context) {
    this.filesDir = context.getFilesDir();
  }

  public String getPinDatabasePath() {
    return new File(filesDir, "pin.db").getAbsolutePath();
  }
}
