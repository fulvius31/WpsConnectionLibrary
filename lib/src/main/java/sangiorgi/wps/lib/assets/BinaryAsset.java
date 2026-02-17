package sangiorgi.wps.lib.assets;

public class BinaryAsset {
  private final String assetName;
  private final String targetName;
  private final boolean isExecutable;
  private final AssetType type;

  public enum AssetType {
    BINARY,
    LIBRARY,
    CONFIG,
    DATABASE
  }

  private BinaryAsset(Builder builder) {
    this.assetName = builder.assetName;
    this.targetName = builder.targetName;
    this.isExecutable = builder.isExecutable;
    this.type = builder.type;
  }

  public String getAssetName() {
    return assetName;
  }

  public String getTargetName() {
    return targetName;
  }

  public boolean isExecutable() {
    return isExecutable;
  }

  public AssetType getType() {
    return type;
  }

  public static class Builder {
    private String assetName;
    private String targetName;
    private boolean isExecutable = false;
    private AssetType type = AssetType.LIBRARY;

    public Builder assetName(String assetName) {
      this.assetName = assetName;
      return this;
    }

    public Builder targetName(String targetName) {
      this.targetName = targetName;
      return this;
    }

    public Builder executable() {
      this.isExecutable = true;
      return this;
    }

    public Builder type(AssetType type) {
      this.type = type;
      return this;
    }

    public BinaryAsset build() {
      if (targetName == null) {
        targetName = assetName;
      }
      return new BinaryAsset(this);
    }
  }
}
