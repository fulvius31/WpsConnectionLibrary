package sangiorgi.wps.lib.models;

import java.util.Arrays;

public class NetworkToTest {
  private String bssid;
  private String ssid;
  private String[] pins;
  private String password;

  public NetworkToTest() {}

  public NetworkToTest(String bssid, String ssid, String[] pins) {
    this.bssid = bssid;
    this.ssid = ssid;
    this.pins = pins;
  }

  @Override
  public String toString() {
    return "NetworkToTest{"
        + "bssid='"
        + bssid
        + '\''
        + ", ssid='"
        + ssid
        + '\''
        + ", pins="
        + Arrays.toString(pins)
        + '}';
  }

  public String getBssid() {
    return bssid;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public void setBssid(String bssid) {
    this.bssid = bssid;
  }

  public String getSsid() {
    return ssid;
  }

  public void setSsid(String ssid) {
    this.ssid = ssid;
  }

  public String[] getPins() {
    return pins;
  }

  public void setPins(String[] pins) {
    this.pins = pins;
  }
}
