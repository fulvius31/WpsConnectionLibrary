package sangiorgi.wps.lib.services;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import java.util.HashSet;
import java.util.Set;
import sangiorgi.wps.lib.ConnectionUpdateCallback;
import sangiorgi.wps.lib.models.NetworkToTest;
import sangiorgi.wps.lib.utils.PinUtils;

/** Manages WiFi connection state and pin storage during WPS testing. */
@SuppressWarnings("MissingPermission")
public class ConnectionStateManager {
  private static final String TAG = "ConnectionStateManager";

  private final ConnectionUpdateCallback callback;
  private final Set<String> knownNetworkSsids;
  private final PinUtils pinUtils;

  private volatile boolean isCancelled = false;
  private volatile String currentPin;
  private volatile NetworkToTest currentNetwork;

  public ConnectionStateManager(
      Context context, WifiManager wifiManager, ConnectionUpdateCallback callback) {
    this.callback = callback;
    this.knownNetworkSsids = getKnownNetworkSsids(wifiManager);
    this.pinUtils = new PinUtils(context);
  }

  /**
   * Gets SSIDs of known/configured networks. On Android 10+ (API 29+), getConfiguredNetworks()
   * returns empty list for privacy. We fall back to checking only the currently connected network.
   */
  @SuppressWarnings("deprecation")
  private Set<String> getKnownNetworkSsids(WifiManager wifiManager) {
    Set<String> ssids = new HashSet<>();

    // On Android 10+, getConfiguredNetworks returns empty unless app owns the config
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      try {
        var configs = wifiManager.getConfiguredNetworks();
        if (configs != null) {
          for (var config : configs) {
            if (config.SSID != null) {
              // SSID is quoted, remove quotes
              String ssid = config.SSID.replace("\"", "");
              ssids.add(ssid);
            }
          }
        }
      } catch (Exception e) {
        Log.w(TAG, "Failed to get configured networks", e);
      }
    }

    // Always try to add currently connected network
    try {
      WifiInfo wifiInfo = wifiManager.getConnectionInfo();
      if (wifiInfo != null && wifiInfo.getSSID() != null) {
        String ssid = wifiInfo.getSSID().replace("\"", "");
        if (!ssid.equals("<unknown ssid>")) {
          ssids.add(ssid);
        }
      }
    } catch (Exception e) {
      Log.w(TAG, "Failed to get current connection info", e);
    }

    return ssids;
  }

  public synchronized void setCurrentConnection(NetworkToTest network, String pin) {
    this.currentNetwork = network;
    this.currentPin = pin;
  }

  public boolean isNetworkAlreadyConfigured() {
    NetworkToTest network = currentNetwork;
    if (network == null) {
      return false;
    }
    String ssid = network.getSsid();
    if (ssid == null) {
      return false;
    }
    return knownNetworkSsids.contains(ssid);
  }

  public void handleSuccessfulConnection(String password) {
    NetworkToTest network = currentNetwork;
    String pin = currentPin;

    if (network == null || pin == null) {
      Log.w(TAG, "Cannot handle success - missing network or pin info");
      return;
    }

    // Set the password on the network object
    if (password != null && !password.isEmpty()) {
      network.setPassword(password);
      Log.i(TAG, "Password set on network: " + password);
    }

    if (!isNetworkAlreadyConfigured()) {
      pinUtils.storePinResult(network.getBssid(), pin, true, password);
    }

    callback.success(network, true);
  }

  public void handleFailedConnection(String reason, int errorType) {
    NetworkToTest network = currentNetwork;
    String pin = currentPin;

    if (network == null || pin == null) {
      Log.w(TAG, "Cannot handle failure - missing network or pin info");
      return;
    }

    pinUtils.storePinResult(network.getBssid(), pin, false, null);
    callback.error(reason, errorType);
  }

  public void updateProgress(String message) {
    callback.updateMessage(message);
  }

  public void updateCount(int count) {
    callback.updateCount(count);
  }

  public void cancel() {
    isCancelled = true;
  }

  public void setCancelled(boolean cancelled) {
    isCancelled = cancelled;
  }

  public boolean isCancelled() {
    return isCancelled;
  }

  public void createConnection(String title, String message, int totalPins) {
    callback.create(title, message, totalPins);
  }
}
