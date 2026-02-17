package sangiorgi.wps.lib.assets;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class WpaToolsInitializer {
  private static final String TAG = "WpaToolsInitializer";

  private static final String WPA_CLI = "wpa_cli";
  private static final String WPA_SUPPLICANT = "wpa_supplicant";
  private static final String WPA_SUPPLICANT_CONF = "wpa_supplicant.conf";
  private static final String PIXIEDUST = "pixiedust";
  private static final String IW_TOOLS = "iw";

  private static final String LIBSSL = "libssl.so.1.1";
  private static final String LIBSSL3 = "libssl.so.3";
  private static final String LIBCRYPTO = "libcrypto.so.1.1";
  private static final String LIBCRYPTO3 = "libcrypto.so.3";
  private static final String LIBNL = "libnl-3.so";
  private static final String LIBNL_GENL = "libnl-genl-3.so";
  private static final String LIBNL_ROUTE = "libnl-route-3.so";

  private static final String PIN_DATABASE = "pin.db";

  private static final String SUFFIX_32 = "-32";

  private static final AtomicBoolean initialized = new AtomicBoolean(false);
  private static final CountDownLatch initializationLatch = new CountDownLatch(1);

  private final Context context;
  private final AssetExtractor extractor;
  private final boolean is64Bit;
  private final int androidVersion;

  public WpaToolsInitializer(Context context) {
    this.context = context.getApplicationContext();
    this.extractor = new AssetExtractor(this.context);
    this.is64Bit = is64BitArchitecture();
    this.androidVersion = Build.VERSION.SDK_INT;
  }

  public static void waitForInitialization() {
    try {
      initializationLatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while waiting for WPA tools initialization", e);
    }
  }

  private static final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

  /**
   * Initialize WPA tools asynchronously on a background thread. This should be called from
   * Application.onCreate() to avoid blocking the main thread.
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
              Log.d(TAG, "Starting async initialization of WPA tools");
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
        Log.d(TAG, "All tools already present in " + context.getFilesDir().getAbsolutePath());
        return verifyExecutablePermissions(requiredAssets);
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

  private boolean verifyExecutablePermissions(List<BinaryAsset> assets) {
    boolean allGood = true;
    for (BinaryAsset asset : assets) {
      if (asset.isExecutable()) {
        boolean success = extractor.getAssetFile(asset.getTargetName()).setExecutable(true);
        if (!success) {
          Log.w(TAG, "Failed to verify executable permission for: " + asset.getTargetName());
          allGood = false;
        }
      }
    }
    return allGood;
  }

  private List<BinaryAsset> getRequiredAssets() {
    List<BinaryAsset> assets = new ArrayList<>();

    // Add databases
    assets.add(
        new BinaryAsset.Builder()
            .assetName(PIN_DATABASE)
            .targetName(PIN_DATABASE)
            .type(BinaryAsset.AssetType.DATABASE)
            .build());

    assets.add(
        new BinaryAsset.Builder()
            .assetName(getAssetNameWithArch(WPA_CLI))
            .targetName(WPA_CLI + "_n")
            .type(BinaryAsset.AssetType.BINARY)
            .executable()
            .build());

    assets.add(
        new BinaryAsset.Builder()
            .assetName(getAssetNameWithArch(WPA_SUPPLICANT))
            .targetName(WPA_SUPPLICANT)
            .type(BinaryAsset.AssetType.BINARY)
            .executable()
            .build());

    assets.add(
        new BinaryAsset.Builder()
            .assetName(WPA_SUPPLICANT_CONF)
            .targetName(WPA_SUPPLICANT_CONF)
            .type(BinaryAsset.AssetType.CONFIG)
            .build());

    if (androidVersion > Build.VERSION_CODES.KITKAT) {
      assets.addAll(getAdvancedToolsAssets());
    }

    return assets;
  }

  private List<BinaryAsset> getAdvancedToolsAssets() {
    List<BinaryAsset> assets = new ArrayList<>();

    assets.add(
        new BinaryAsset.Builder()
            .assetName(getAssetNameWithArch(IW_TOOLS))
            .targetName(IW_TOOLS)
            .type(BinaryAsset.AssetType.BINARY)
            .executable()
            .build());

    assets.add(
        new BinaryAsset.Builder()
            .assetName(getAssetNameWithArch(PIXIEDUST))
            .targetName(PIXIEDUST)
            .type(BinaryAsset.AssetType.BINARY)
            .executable()
            .build());

    if (is64Bit) {
      assets.add(
          new BinaryAsset.Builder()
              .assetName(LIBSSL3)
              .targetName(LIBSSL3)
              .type(BinaryAsset.AssetType.LIBRARY)
              .build());

      assets.add(
          new BinaryAsset.Builder()
              .assetName(LIBCRYPTO3)
              .targetName(LIBCRYPTO3)
              .type(BinaryAsset.AssetType.LIBRARY)
              .build());
    } else {
      assets.add(
          new BinaryAsset.Builder()
              .assetName(LIBSSL)
              .targetName(LIBSSL)
              .type(BinaryAsset.AssetType.LIBRARY)
              .build());

      assets.add(
          new BinaryAsset.Builder()
              .assetName(LIBCRYPTO)
              .targetName(LIBCRYPTO)
              .type(BinaryAsset.AssetType.LIBRARY)
              .build());
    }

    assets.add(
        new BinaryAsset.Builder()
            .assetName(getAssetNameWithArch(LIBNL))
            .targetName(LIBNL)
            .type(BinaryAsset.AssetType.LIBRARY)
            .build());

    assets.add(
        new BinaryAsset.Builder()
            .assetName(getAssetNameWithArch(LIBNL_GENL))
            .targetName(LIBNL_GENL)
            .type(BinaryAsset.AssetType.LIBRARY)
            .build());

    assets.add(
        new BinaryAsset.Builder()
            .assetName(getAssetNameWithArch(LIBNL_ROUTE))
            .targetName(LIBNL_ROUTE)
            .type(BinaryAsset.AssetType.LIBRARY)
            .build());

    return assets;
  }

  private String getAssetNameWithArch(String baseName) {
    return is64Bit ? baseName : baseName + SUFFIX_32;
  }

  private boolean is64BitArchitecture() {
    String arch = System.getProperty("os.arch");
    boolean is64 = arch != null && arch.contains("64");
    Log.d(TAG, "Architecture detection: " + (is64 ? "64-bit" : "32-bit") + " (ABIs: " + arch + ")");
    return is64;
  }
}
