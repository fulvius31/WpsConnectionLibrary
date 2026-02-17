package sangiorgi.wps.lib.assets;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;

public class AssetExtractorTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private Context mockContext;

  @Before
  public void setUp() {
    mockContext = mock(Context.class);
    when(mockContext.getFilesDir()).thenReturn(tempFolder.getRoot());
  }

  @Test
  public void testAssetExistsReturnsFalseForMissingFile() {
    AssetExtractor extractor = new AssetExtractor(mockContext);
    assertFalse(extractor.assetExists("nonexistent_file"));
  }

  @Test
  public void testAssetExistsReturnsTrueForExistingFile() throws Exception {
    File testFile = tempFolder.newFile("test_asset");
    // Write some content so length > 0
    java.io.FileWriter writer = new java.io.FileWriter(testFile);
    writer.write("content");
    writer.close();

    AssetExtractor extractor = new AssetExtractor(mockContext);
    assertTrue(extractor.assetExists("test_asset"));
  }

  @Test
  public void testAssetExistsReturnsFalseForEmptyFile() throws Exception {
    tempFolder.newFile("empty_asset");
    // File exists but has zero length

    AssetExtractor extractor = new AssetExtractor(mockContext);
    assertFalse(extractor.assetExists("empty_asset"));
  }

  @Test
  public void testAllAssetsExistReturnsTrueWhenAllPresent() throws Exception {
    // Create two files with content
    for (String name : new String[] {"asset1", "asset2"}) {
      File f = tempFolder.newFile(name);
      java.io.FileWriter w = new java.io.FileWriter(f);
      w.write("content");
      w.close();
    }

    BinaryAsset a1 = new BinaryAsset.Builder().assetName("asset1").build();
    BinaryAsset a2 = new BinaryAsset.Builder().assetName("asset2").build();

    AssetExtractor extractor = new AssetExtractor(mockContext);
    assertTrue(extractor.allAssetsExist(a1, a2));
  }

  @Test
  public void testAllAssetsExistReturnsFalseWhenOneMissing() throws Exception {
    File f = tempFolder.newFile("asset1");
    java.io.FileWriter w = new java.io.FileWriter(f);
    w.write("content");
    w.close();

    BinaryAsset a1 = new BinaryAsset.Builder().assetName("asset1").build();
    BinaryAsset a2 = new BinaryAsset.Builder().assetName("missing_asset").build();

    AssetExtractor extractor = new AssetExtractor(mockContext);
    assertFalse(extractor.allAssetsExist(a1, a2));
  }

  @Test
  public void testGetAssetFile() {
    AssetExtractor extractor = new AssetExtractor(mockContext);
    File file = extractor.getAssetFile("test_target");

    assertNotNull(file);
    assertEquals("test_target", file.getName());
    assertEquals(tempFolder.getRoot(), file.getParentFile());
  }

  @Test
  public void testExtractAssetSkipsExisting() throws Exception {
    // Create file with content
    File existing = tempFolder.newFile("existing_binary");
    java.io.FileWriter w = new java.io.FileWriter(existing);
    w.write("binary content");
    w.close();

    BinaryAsset asset =
        new BinaryAsset.Builder()
            .assetName("existing_binary")
            .targetName("existing_binary")
            .build();

    AssetExtractor extractor = new AssetExtractor(mockContext);
    // Should return true since file exists with content
    boolean result = extractor.extractAsset(asset);
    assertTrue(result);
  }
}
