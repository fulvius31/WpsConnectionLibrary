package sangiorgi.wps.lib.assets;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import java.io.File;
import org.junit.Test;

public class WpaToolsPathsTest {

  @Test
  public void testGetPinDatabasePath() {
    Context mockContext = mock(Context.class);
    File mockFilesDir = new File("/data/data/com.test/files");
    when(mockContext.getFilesDir()).thenReturn(mockFilesDir);

    WpaToolsPaths paths = new WpaToolsPaths(mockContext);
    String pinPath = paths.getPinDatabasePath();

    assertNotNull(pinPath);
    assertTrue(pinPath.endsWith("pin.db"));
    assertTrue(pinPath.contains("files"));
  }
}
