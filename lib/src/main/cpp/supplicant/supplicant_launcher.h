#ifndef SUPPLICANT_LAUNCHER_H
#define SUPPLICANT_LAUNCHER_H

#include "../jni/wpsnative_jni.h"

/**
 * Create a session from a stdout file descriptor provided by Java.
 *
 * Java launches "su -c wpa_supplicant ..." via Runtime.exec() for proper
 * Magisk mount namespace handling, then passes the stdout pipe fd here.
 * The fd is dup()'d so native code owns its own copy.
 *
 * @param stdout_fd    File descriptor for reading process stdout
 * @param ctrl_dir     Control interface directory
 * @param iface        Network interface name (e.g., "wlan0")
 * @return             Session handle, or NULL on failure
 */
wps_session_t *session_create(int stdout_fd, const char *ctrl_dir, const char *iface);

/**
 * Destroy the native session (close fd, free memory).
 * Java is responsible for destroying the actual su Process.
 */
void session_destroy(wps_session_t *session);

/**
 * Read wpa_supplicant output and parse WPS result.
 * Blocks until a result is found or timeout expires.
 */
void supplicant_read_wps_result(wps_session_t *session, wps_result_t *result, int timeout_ms);

/**
 * Read wpa_supplicant debug output and extract Pixie Dust parameters.
 *
 * @return 0 on success (all 6 params found), -1 on failure
 */
int supplicant_extract_pixiedust(wps_session_t *session, pixiedust_params_t *params, int timeout_ms);

/**
 * Read raw output from the supplicant process.
 * @return Number of bytes read, or -1 on error
 */
int supplicant_read_output(wps_session_t *session, char *buf, int buf_size, int timeout_ms);

#endif // SUPPLICANT_LAUNCHER_H
