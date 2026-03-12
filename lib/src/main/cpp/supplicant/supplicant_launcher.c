#include "supplicant_launcher.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/select.h>
#include <errno.h>
#include <android/log.h>
#include <fcntl.h>
#include <pthread.h>
#include <time.h>
#include <limits.h>

#define LOG_TAG "SupplicantLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Max line length for output parsing
#define MAX_LINE 2048

/**
 * Helper: get current monotonic time in milliseconds (using long long to
 * avoid 32-bit overflow on devices with high uptime).
 */
static long long now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

/**
 * Helper: read a line from fd with timeout using select().
 * Returns number of chars read, 0 on timeout, -1 on error/EOF.
 */
static int read_line_timeout(int fd, char *buf, int buf_size, int timeout_ms) {
    int pos = 0;
    long long deadline = now_ms() + timeout_ms;

    while (pos < buf_size - 1) {
        long long remaining = deadline - now_ms();
        if (remaining <= 0) {
            buf[pos] = '\0';
            return pos > 0 ? pos : 0;
        }

        fd_set fds;
        FD_ZERO(&fds);
        FD_SET(fd, &fds);

        struct timeval tv;
        tv.tv_sec = remaining / 1000;
        tv.tv_usec = (remaining % 1000) * 1000;

        int sel = select(fd + 1, &fds, NULL, NULL, &tv);

        if (sel < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (sel == 0) {
            buf[pos] = '\0';
            return pos > 0 ? pos : 0;
        }

        char c;
        int r = read(fd, &c, 1);
        if (r < 0) {
            if (errno == EINTR || errno == EAGAIN) continue;
            buf[pos] = '\0';
            return pos > 0 ? pos : -1;
        }
        if (r == 0) {
            // EOF — pipe write end closed
            buf[pos] = '\0';
            return pos > 0 ? pos : -1;
        }

        if (c == '\n') {
            buf[pos] = '\0';
            return pos;
        }

        buf[pos++] = c;
    }

    buf[pos] = '\0';
    return pos;
}

/**
 * Helper: extract hex value from a hexdump line.
 * Format: "  WPS: field_name - hexdump(len): xx xx xx ..."
 * OR:     "field_name - hexdump(len): xx xx xx ..."
 * We want just the hex bytes, spaces stripped.
 */
static void extract_hexdump(const char *line, char *out, int out_size) {
    const char *hex_start = strstr(line, "): ");
    if (hex_start == NULL) {
        out[0] = '\0';
        return;
    }
    hex_start += 3; // Skip "): "

    int pos = 0;
    while (*hex_start && pos < out_size - 1) {
        if (*hex_start != ' ') {
            out[pos++] = *hex_start;
        }
        hex_start++;
    }
    out[pos] = '\0';
}

/**
 * Helper: convert hex string from "Network Key" hexdump to ASCII password.
 */
static void hex_to_ascii(const char *hex, char *ascii, int ascii_size) {
    int pos = 0;
    int len = strlen(hex);

    for (int i = 0; i + 1 < len && pos < ascii_size - 1; i += 2) {
        char byte_str[3] = {hex[i], hex[i + 1], '\0'};
        int val = (int)strtol(byte_str, NULL, 16);
        if (val > 0) {
            ascii[pos++] = (char)val;
        }
    }
    ascii[pos] = '\0';
}

// =============================================================================
// session_create
// Create a session from a file descriptor provided by Java.
// Java launches "su -c wpa_supplicant ..." via Runtime.exec() for proper
// Magisk mount namespace handling, then passes the stdout fd here.
// =============================================================================
wps_session_t *session_create(int stdout_fd, const char *ctrl_dir, const char *iface) {
    if (stdout_fd < 0) {
        LOGE("session_create: invalid fd=%d", stdout_fd);
        return NULL;
    }

    // dup() so native owns its own copy of the fd
    int dup_fd = dup(stdout_fd);
    if (dup_fd < 0) {
        LOGE("session_create: dup() failed: %s", strerror(errno));
        return NULL;
    }

    wps_session_t *session = calloc(1, sizeof(wps_session_t));
    if (!session) {
        LOGE("session_create: calloc failed");
        close(dup_fd);
        return NULL;
    }

    session->stdout_fd = dup_fd;
    session->running = 1;
    strncpy(session->ctrl_path, ctrl_dir, sizeof(session->ctrl_path) - 1);
    strncpy(session->iface, iface, sizeof(session->iface) - 1);
    pthread_mutex_init(&session->lock, NULL);

    LOGI("session_create: fd=%d (dup'd from %d), iface=%s, ctrl=%s",
         dup_fd, stdout_fd, iface, ctrl_dir);
    return session;
}

// =============================================================================
// session_destroy
// Clean up the native session. Java is responsible for destroying the Process.
// =============================================================================
void session_destroy(wps_session_t *session) {
    if (session == NULL) return;

    pthread_mutex_lock(&session->lock);

    if (session->running) {
        session->running = 0;

        if (session->stdout_fd >= 0) {
            close(session->stdout_fd);
            session->stdout_fd = -1;
        }
    }

    pthread_mutex_unlock(&session->lock);
    pthread_mutex_destroy(&session->lock);
    free(session);

    LOGI("session_destroy: cleaned up");
}

// =============================================================================
// supplicant_read_wps_result
// =============================================================================
void supplicant_read_wps_result(wps_session_t *session, wps_result_t *result, int timeout_ms) {
    if (!session || !session->running) {
        result->status = WPS_STATUS_ERROR;
        return;
    }

    char line[MAX_LINE];
    long long deadline = now_ms() + timeout_ms;

    while (1) {
        long long remaining = deadline - now_ms();
        if (remaining <= 0) {
            result->status = WPS_STATUS_TIMEOUT;
            return;
        }

        int len = read_line_timeout(session->stdout_fd, line, sizeof(line),
                                    (int)(remaining > INT_MAX ? INT_MAX : remaining));

        if (len < 0) {
            result->status = WPS_STATUS_ERROR;
            return;
        }
        if (len == 0) {
            result->status = WPS_STATUS_TIMEOUT;
            return;
        }

        LOGD("supplicant output: %s", line);
        strncpy(result->raw_line, line, sizeof(result->raw_line) - 1);

        // Check for SELinux denial
        if (strstr(line, "avc: denied { sendto }") && strstr(line, "permissive=0")) {
            result->status = WPS_STATUS_SELINUX;
            return;
        }

        // Check for Network Key (success with password)
        if (strstr(line, "Network Key") && strstr(line, "hexdump(")) {
            char hex[512];
            extract_hexdump(line, hex, sizeof(hex));
            hex_to_ascii(hex, result->network_key, sizeof(result->network_key));
            result->status = WPS_STATUS_SUCCESS;
            LOGI("WPS SUCCESS: password extracted");
            return;
        }

        // Check for WPS message results
        if (strstr(line, "msg=") && strstr(line, "config_error")) {
            if (strstr(line, "config_error=15")) {
                result->status = WPS_STATUS_LOCKED;
                return;
            }
            if (strstr(line, "config_error=2")) {
                result->status = WPS_STATUS_CRC_FAIL;
                return;
            }
            if (strstr(line, "msg=8")) {
                result->status = WPS_STATUS_FOUR_FAIL;
                return;
            }
            if (strstr(line, "msg=10")) {
                result->status = WPS_STATUS_THREE_FAIL;
                return;
            }
            if (strstr(line, "msg=11") && strstr(line, "config_error=0")) {
                result->status = WPS_STATUS_SUCCESS;
                return;
            }
        }
    }
}

// =============================================================================
// supplicant_extract_pixiedust
// =============================================================================
int supplicant_extract_pixiedust(wps_session_t *session, pixiedust_params_t *params, int timeout_ms) {
    if (!session || !session->running) return -1;

    char line[MAX_LINE];
    int found = 0; // Bitmask: bit 0-5 for each param
    int read_errors = 0;
    long long deadline = now_ms() + timeout_ms;

    while (found != 0x3F) {
        long long remaining = deadline - now_ms();
        if (remaining <= 0) break;

        int len = read_line_timeout(session->stdout_fd, line, sizeof(line),
                                    (int)(remaining > INT_MAX ? INT_MAX : remaining));

        if (len < 0) {
            read_errors++;
            LOGE("pixie: read error #%d (errno=%d: %s), continuing...",
                 read_errors, errno, strerror(errno));
            if (read_errors >= 3) {
                LOGE("pixie: too many read errors, giving up");
                return -1;
            }
            continue; // Retry instead of immediately failing
        }
        if (len == 0) {
            // Timeout on this read, but overall deadline may not have expired
            continue;
        }
        read_errors = 0; // Reset on successful read

        LOGD("pixie output: %s", line);

        // Extract each parameter from hexdump lines
        if (strstr(line, "Enrollee Nonce") && strstr(line, "hexdump")) {
            extract_hexdump(line, params->enrollee_nonce, sizeof(params->enrollee_nonce));
            found |= 0x01;
            LOGD("Found Enrollee Nonce");
        }
        else if (strstr(line, "DH own Public Key") && strstr(line, "hexdump")) {
            extract_hexdump(line, params->dh_own_pubkey, sizeof(params->dh_own_pubkey));
            found |= 0x02;
            LOGD("Found DH own Public Key (PKE)");
        }
        else if (strstr(line, "DH peer Public Key") && strstr(line, "hexdump")) {
            extract_hexdump(line, params->dh_peer_pubkey, sizeof(params->dh_peer_pubkey));
            found |= 0x04;
            LOGD("Found DH peer Public Key (PKR)");
        }
        else if (strstr(line, "AuthKey") && strstr(line, "hexdump")) {
            extract_hexdump(line, params->auth_key, sizeof(params->auth_key));
            found |= 0x08;
            LOGD("Found AuthKey");
        }
        else if (strstr(line, "E-Hash1") && strstr(line, "hexdump")) {
            extract_hexdump(line, params->e_hash1, sizeof(params->e_hash1));
            found |= 0x10;
            LOGD("Found E-Hash1");
        }
        else if (strstr(line, "E-Hash2") && strstr(line, "hexdump")) {
            extract_hexdump(line, params->e_hash2, sizeof(params->e_hash2));
            found |= 0x20;
            LOGD("Found E-Hash2");
        }
    }

    return (found == 0x3F) ? 0 : -1;
}

// =============================================================================
// supplicant_read_output
// =============================================================================
int supplicant_read_output(wps_session_t *session, char *buf, int buf_size, int timeout_ms) {
    if (!session || session->stdout_fd < 0) return -1;

    fd_set fds;
    FD_ZERO(&fds);
    FD_SET(session->stdout_fd, &fds);

    struct timeval tv;
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;

    int sel = select(session->stdout_fd + 1, &fds, NULL, NULL, &tv);
    if (sel <= 0) return sel;

    return read(session->stdout_fd, buf, buf_size);
}
