package sangiorgi.wps.lib.assets;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AssetExtractor {
  private static final String TAG = "AssetExtractor";
  private static final int BUFFER_SIZE = 8192;

  private final Context context;
  private final File targetDirectory;

  public AssetExtractor(Context context) {
    this.context = context;
    this.targetDirectory = context.getFilesDir();
  }

  public boolean extractAsset(BinaryAsset asset) {
    File targetFile = new File(targetDirectory, asset.getTargetName());

    if (targetFile.exists() && targetFile.length() > 0) {
      Log.d(TAG, "Asset already exists: " + asset.getTargetName());
      return ensureExecutable(targetFile, asset.isExecutable());
    }

    try {
      copyAssetToFile(asset.getAssetName(), targetFile);
      return ensureExecutable(targetFile, asset.isExecutable());
    } catch (IOException e) {
      Log.e(TAG, "Failed to extract asset: " + asset.getAssetName(), e);
      return false;
    }
  }

  public boolean assetExists(String targetName) {
    File targetFile = new File(targetDirectory, targetName);
    return targetFile.exists() && targetFile.length() > 0;
  }

  public boolean allAssetsExist(BinaryAsset... assets) {
    for (BinaryAsset asset : assets) {
      if (!assetExists(asset.getTargetName())) {
        return false;
      }
    }
    return true;
  }

  public File getAssetFile(String targetName) {
    return new File(targetDirectory, targetName);
  }

  private void copyAssetToFile(String assetName, File targetFile) throws IOException {
    AssetManager assetManager = context.getAssets();

    try (InputStream input = assetManager.open(assetName);
        OutputStream output = new FileOutputStream(targetFile)) {

      byte[] buffer = new byte[BUFFER_SIZE];
      int bytesRead;

      while ((bytesRead = input.read(buffer)) != -1) {
        output.write(buffer, 0, bytesRead);
      }

      output.flush();
      Log.d(TAG, "Successfully extracted: " + assetName + " to " + targetFile.getAbsolutePath());
    }
  }

  private boolean ensureExecutable(File file, boolean shouldBeExecutable) {
    if (shouldBeExecutable) {
      boolean success = file.setExecutable(true);
      if (!success) {
        Log.w(TAG, "Failed to set executable permission for: " + file.getName());
        return false;
      }
    }
    return true;
  }
}
