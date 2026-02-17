package sangiorgi.wps.lib.models;

/** Parameters extracted from WPS connection attempt needed for Pixie Dust attack */
public class PixieDustParameters {
  private final String pke;
  private final String pkr;
  private final String ehash1;
  private final String ehash2;
  private final String authKey;
  private final String enonce;

  public PixieDustParameters(
      String pke, String pkr, String ehash1, String ehash2, String authKey, String enonce) {
    this.pke = pke;
    this.pkr = pkr;
    this.ehash1 = ehash1;
    this.ehash2 = ehash2;
    this.authKey = authKey;
    this.enonce = enonce;
  }

  public String getPke() {
    return pke;
  }

  public String getPkr() {
    return pkr;
  }

  public String getEhash1() {
    return ehash1;
  }

  public String getEhash2() {
    return ehash2;
  }

  public String getAuthKey() {
    return authKey;
  }

  public String getEnonce() {
    return enonce;
  }

  public boolean isValid() {
    return pke != null
        && !pke.isEmpty()
        && pkr != null
        && !pkr.isEmpty()
        && ehash1 != null
        && !ehash1.isEmpty()
        && ehash2 != null
        && !ehash2.isEmpty()
        && authKey != null
        && !authKey.isEmpty()
        && enonce != null
        && !enonce.isEmpty();
  }

  @Override
  public String toString() {
    return "--pke "
        + pke
        + " "
        + "--pkr "
        + pkr
        + " "
        + "--e-hash1 "
        + ehash1
        + " "
        + "--e-hash2 "
        + ehash2
        + " "
        + "--authkey "
        + authKey
        + " "
        + "--e-nonce "
        + enonce;
  }
}
