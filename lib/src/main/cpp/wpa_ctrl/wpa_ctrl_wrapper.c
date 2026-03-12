#include "wpa_ctrl_wrapper.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <errno.h>
#include <sys/select.h>
#include <android/log.h>

#define LOG_TAG "WpaCtrlWrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Timeout for socket operations (milliseconds)
#define SOCKET_TIMEOUT_MS 2000

// Directory for local socket bind addresses (set via wpa_ctrl_set_local_dir)
static char s_local_dir[256] = "";

void wpa_ctrl_set_local_dir(const char *dir) {
    if (dir) {
        strncpy(s_local_dir, dir, sizeof(s_local_dir) - 1);
        s_local_dir[sizeof(s_local_dir) - 1] = '\0';
    }
}

/**
 * Internal: connect to wpa_supplicant control interface.
 * The client needs its own bind address for the datagram socket.
 */
static int ctrl_connect(const char *socket_path) {
    int fd = socket(AF_UNIX, SOCK_DGRAM, 0);
    if (fd < 0) {
        LOGE("socket() failed: %s", strerror(errno));
        return -1;
    }

    // Bind to a unique local address so wpa_supplicant can reply.
    // Use the app's files directory (writable and SELinux-allowed).
    struct sockaddr_un local;
    memset(&local, 0, sizeof(local));
    local.sun_family = AF_UNIX;

    if (s_local_dir[0]) {
        snprintf(local.sun_path, sizeof(local.sun_path),
                 "%s/wps_ctrl_%d_%d", s_local_dir, getpid(), fd);
    } else {
        // Fallback to abstract namespace socket (no filesystem path needed)
        local.sun_path[0] = '\0';
        snprintf(local.sun_path + 1, sizeof(local.sun_path) - 1,
                 "wps_ctrl_%d_%d", getpid(), fd);
    }

    // Remove stale socket file (only for non-abstract sockets)
    if (local.sun_path[0] != '\0') {
        unlink(local.sun_path);
    }

    socklen_t local_len;
    if (local.sun_path[0] == '\0') {
        // Abstract socket: length includes the null byte + name
        int name_len = strlen(local.sun_path + 1);
        local_len = offsetof(struct sockaddr_un, sun_path) + 1 + name_len;
    } else {
        local_len = sizeof(local);
    }

    if (bind(fd, (struct sockaddr *)&local, local_len) < 0) {
        LOGE("bind(%s) failed: %s",
             local.sun_path[0] ? local.sun_path : local.sun_path + 1,
             strerror(errno));
        close(fd);
        return -1;
    }

    // Connect to wpa_supplicant's control socket
    struct sockaddr_un remote;
    memset(&remote, 0, sizeof(remote));
    remote.sun_family = AF_UNIX;
    strncpy(remote.sun_path, socket_path, sizeof(remote.sun_path) - 1);

    if (connect(fd, (struct sockaddr *)&remote, sizeof(remote)) < 0) {
        LOGE("connect(%s) failed: %s", socket_path, strerror(errno));
        if (local.sun_path[0] != '\0') unlink(local.sun_path);
        close(fd);
        return -1;
    }

    LOGI("Connected to control socket: %s", socket_path);
    return fd;
}

/**
 * Internal: cleanup socket and local bind file.
 */
static void ctrl_disconnect(int fd) {
    if (fd >= 0) {
        // Get the local socket path to unlink
        struct sockaddr_un local;
        socklen_t len = sizeof(local);
        if (getsockname(fd, (struct sockaddr *)&local, &len) == 0) {
            if (local.sun_path[0] != '\0') {
                unlink(local.sun_path);
            }
        }
        close(fd);
    }
}

// =============================================================================
// wpa_ctrl_send_raw
// =============================================================================
int wpa_ctrl_send_raw(const char *socket_path, const char *command,
                       char *response, int response_size) {
    int fd = ctrl_connect(socket_path);
    if (fd < 0) return -1;

    // Send command
    if (write(fd, command, strlen(command)) < 0) {
        LOGE("write() failed: %s", strerror(errno));
        ctrl_disconnect(fd);
        return -1;
    }

    // Wait for response with timeout
    fd_set fds;
    FD_ZERO(&fds);
    FD_SET(fd, &fds);

    struct timeval tv;
    tv.tv_sec = SOCKET_TIMEOUT_MS / 1000;
    tv.tv_usec = (SOCKET_TIMEOUT_MS % 1000) * 1000;

    int sel = select(fd + 1, &fds, NULL, NULL, &tv);
    if (sel <= 0) {
        LOGE("select() timeout or error: %s", sel < 0 ? strerror(errno) : "timeout");
        ctrl_disconnect(fd);
        return -1;
    }

    int read_size = read(fd, response, response_size - 1);
    if (read_size < 0) {
        LOGE("read() failed: %s", strerror(errno));
        ctrl_disconnect(fd);
        return -1;
    }

    response[read_size] = '\0';
    LOGI("Command '%s' -> Response: '%s'", command, response);

    ctrl_disconnect(fd);
    return read_size;
}

// =============================================================================
// wpa_ctrl_send_cmd
// =============================================================================
int wpa_ctrl_send_cmd(const char *socket_path, const char *bssid, const char *pin,
                       char *response, int response_size) {
    // Build WPS_REG command: "WPS_REG <bssid> <pin>"
    char command[512];
    snprintf(command, sizeof(command), "WPS_REG %s %s", bssid, pin);

    LOGI("Sending command: %s to %s", command, socket_path);

    return wpa_ctrl_send_raw(socket_path, command, response, response_size);
}
