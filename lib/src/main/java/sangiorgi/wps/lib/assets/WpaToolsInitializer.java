package sangiorgi.wps.lib.assets;

import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Initializes WPA tools assets.
 * With the NDK migration, only the PIN database needs extraction from assets.
 * Executables (wpa_supplicant, wpa_cli, pixiewps) and libraries (OpenSSL, libnl)
 * are now built via NDK and bundled as native libraries in the APK.
 */
public class WpaToolsInitializer {
  private static final String TAG = "WpaToolsInitializer";

  private static final String PIN_DATABASE = "pin.db";

  private static final AtomicBoolean initialized = new AtomicBoolean(false);
  private static final CountDownLatch initializationLatch = new CountDownLatch(1);

  private final Context context;
  private final AssetExtractor extractor;

  public WpaToolsInitializer(Context context) {
    this.context = context.getApplicationContext();
    this.extractor = new AssetExtractor(this.context);
  }

  private static final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

  /**
   * Initialize WPA tools asynchronously on a background thread.
   * With NDK migration, this only extracts the PIN database.
   *
   * @param context Application context
   * @return CompletableFuture that completes when initialization is done
   */
  public static CompletableFuture<Boolean> initializeAsync(Context context) {
    if (initialized.get()) {
      return CompletableFuture.completedFuture(true);
    }

    return CompletableFuture.supplyAsync(
        () -> {
          synchronized (WpaToolsInitializer.class) {
            if (initialized.get()) {
              return true;
            }

            try {
              Log.d(TAG, "Starting async initialization (pin.db only)");
              WpaToolsInitializer initializer = new WpaToolsInitializer(context);
              boolean success = initializer.initialize();

              if (!success) {
                Log.e(TAG, "Failed to initialize WPA tools");
                initializationLatch.countDown();
                return false;
              }

              initialized.set(true);
              initializationLatch.countDown();
              Log.i(TAG, "WPA tools async initialization completed successfully");
              return true;
            } catch (Exception e) {
              Log.e(TAG, "Critical error during WPA tools async initialization", e);
              initializationLatch.countDown();
              return false;
            }
          }
        },
        backgroundExecutor);
  }

  private boolean initialize() {
    try {
      List<BinaryAsset> requiredAssets = getRequiredAssets();

      Log.d(TAG, "Checking " + requiredAssets.size() + " required assets");

      if (extractor.allAssetsExist(requiredAssets.toArray(new BinaryAsset[0]))) {
        Log.d(TAG, "All assets already present in " + context.getFilesDir().getAbsolutePath());
        return true;
      }

      Log.d(TAG, "Extracting missing assets to " + context.getFilesDir().getAbsolutePath());

      for (BinaryAsset asset : requiredAssets) {
        if (!extractor.extractAsset(asset)) {
          Log.e(TAG, "Failed to extract: " + asset.getAssetName());
          return false;
        }
        Log.d(
            TAG,
            "Successfully extracted: " + asset.getAssetName() + " -> " + asset.getTargetName());
      }

      return true;
    } catch (Exception e) {
      Log.e(TAG, "Failed to initialize tools", e);
      return false;
    }
  }

  private List<BinaryAsset> getRequiredAssets() {
    List<BinaryAsset> assets = new ArrayList<>();

    // Only the PIN database needs extraction from assets
    assets.add(
        new BinaryAsset.Builder()
            .assetName(PIN_DATABASE)
            .targetName(PIN_DATABASE)
            .type(BinaryAsset.AssetType.DATABASE)
            .build());

    return assets;
  }
}
