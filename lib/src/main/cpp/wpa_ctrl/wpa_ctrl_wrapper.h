#ifndef WPA_CTRL_WRAPPER_H
#define WPA_CTRL_WRAPPER_H

/**
 * Set the directory for local control socket bind addresses.
 * Must be called before any send functions. Use the app's files directory
 * (e.g., /data/data/com.package.name/files/) since /data/local/tmp/ is
 * restricted by SELinux on modern Android.
 */
void wpa_ctrl_set_local_dir(const char *dir);

/**
 * Send a WPS_REG command to wpa_supplicant via Unix domain socket.
 *
 * @param socket_path   Full path to the control socket (e.g., "/data/vendor/wifi/wpa/wpswpatester/wlan0")
 * @param bssid         Target BSSID (e.g., "a4:11:62:49:98:fa")
 * @param pin           WPS PIN (e.g., "48232906")
 * @param response      Buffer for the response
 * @param response_size Size of response buffer
 * @return              0 on success, -1 on error
 */
int wpa_ctrl_send_cmd(const char *socket_path, const char *bssid, const char *pin,
                       char *response, int response_size);

/**
 * Send a raw command to wpa_supplicant via Unix domain socket.
 *
 * @param socket_path   Full path to the control socket
 * @param command       Raw command string (e.g., "IFNAME=wlan0 wps_reg aa:bb:cc:dd:ee:ff 12345678")
 * @param response      Buffer for the response
 * @param response_size Size of response buffer
 * @return              Number of bytes read, or -1 on error
 */
int wpa_ctrl_send_raw(const char *socket_path, const char *command,
                       char *response, int response_size);

#endif // WPA_CTRL_WRAPPER_H
