package sangiorgi.wps.lib.assets;

import static org.junit.Assert.*;

import org.junit.Test;

public class BinaryAssetTest {

  @Test
  public void testBuilderBasic() {
    BinaryAsset asset =
        new BinaryAsset.Builder()
            .assetName("wpa_cli")
            .targetName("wpa_cli_n")
            .build();

    assertEquals("wpa_cli", asset.getAssetName());
    assertEquals("wpa_cli_n", asset.getTargetName());
    assertFalse(asset.isExecutable());
    assertEquals(BinaryAsset.AssetType.LIBRARY, asset.getType());
  }

  @Test
  public void testBuilderExecutable() {
    BinaryAsset asset =
        new BinaryAsset.Builder()
            .assetName("pixiedust")
            .targetName("pixiedust")
            .executable()
            .build();

    assertTrue(asset.isExecutable());
  }

  @Test
  public void testBuilderWithType() {
    BinaryAsset asset =
        new BinaryAsset.Builder()
            .assetName("vendor.db")
            .targetName("vendor.db")
            .type(BinaryAsset.AssetType.DATABASE)
            .build();

    assertEquals(BinaryAsset.AssetType.DATABASE, asset.getType());
  }

  @Test
  public void testBuilderTargetNameDefaultsToAssetName() {
    BinaryAsset asset =
        new BinaryAsset.Builder()
            .assetName("libssl.so.3")
            .build();

    assertEquals("libssl.so.3", asset.getTargetName());
  }

  @Test
  public void testBuilderBinaryType() {
    BinaryAsset asset =
        new BinaryAsset.Builder()
            .assetName("wpa_supplicant")
            .type(BinaryAsset.AssetType.BINARY)
            .executable()
            .build();

    assertEquals(BinaryAsset.AssetType.BINARY, asset.getType());
    assertTrue(asset.isExecutable());
  }

  @Test
  public void testBuilderConfigType() {
    BinaryAsset asset =
        new BinaryAsset.Builder()
            .assetName("wpa_supplicant.conf")
            .type(BinaryAsset.AssetType.CONFIG)
            .build();

    assertEquals(BinaryAsset.AssetType.CONFIG, asset.getType());
    assertFalse(asset.isExecutable());
  }

  @Test
  public void testBuilderLibraryType() {
    BinaryAsset asset =
        new BinaryAsset.Builder()
            .assetName("libnl-3.so")
            .type(BinaryAsset.AssetType.LIBRARY)
            .build();

    assertEquals(BinaryAsset.AssetType.LIBRARY, asset.getType());
  }

  @Test
  public void testAssetTypeValues() {
    BinaryAsset.AssetType[] types = BinaryAsset.AssetType.values();
    assertEquals(4, types.length);
    assertNotNull(BinaryAsset.AssetType.valueOf("BINARY"));
    assertNotNull(BinaryAsset.AssetType.valueOf("LIBRARY"));
    assertNotNull(BinaryAsset.AssetType.valueOf("CONFIG"));
    assertNotNull(BinaryAsset.AssetType.valueOf("DATABASE"));
  }

  @Test
  public void testBuilderDifferentAssetAndTargetNames() {
    BinaryAsset asset =
        new BinaryAsset.Builder()
            .assetName("wpa_cli-32")
            .targetName("wpa_cli_n")
            .type(BinaryAsset.AssetType.BINARY)
            .executable()
            .build();

    assertEquals("wpa_cli-32", asset.getAssetName());
    assertEquals("wpa_cli_n", asset.getTargetName());
  }

  @Test
  public void testDefaultTypeIsLibrary() {
    BinaryAsset asset =
        new BinaryAsset.Builder()
            .assetName("test")
            .build();

    assertEquals(BinaryAsset.AssetType.LIBRARY, asset.getType());
  }

  @Test
  public void testDefaultNotExecutable() {
    BinaryAsset asset =
        new BinaryAsset.Builder()
            .assetName("test")
            .build();

    assertFalse(asset.isExecutable());
  }
}
