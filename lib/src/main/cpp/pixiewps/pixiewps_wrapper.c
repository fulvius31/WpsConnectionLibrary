#include "pixiewps_wrapper.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/wait.h>
#include <android/log.h>

#define LOG_TAG "PixiewpsWrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/*
 * IMPORTANT: There are two approaches to integrate pixiewps:
 *
 * APPROACH 1 (Current - Process-based):
 *   Build pixiewps as a standalone executable via CMake, ship it in
 *   jniLibs as "libpixiewps_exec.so", and call it via fork/exec.
 *   This works immediately without modifying pixiewps source.
 *
 * APPROACH 2 (Future - In-process):
 *   Modify pixiewps source to rename main() -> pixiewps_main() and
 *   expose a library API. Then compile it directly into libwpsnative.so.
 *   This requires patching pixiewps source.
 *
 * We use APPROACH 1 for now. The executable is built by CMake and placed
 * alongside libwpsnative.so in the native libs directory. Android extracts
 * it per-ABI automatically.
 *
 * To switch to APPROACH 2 later:
 * 1. Patch pixiewps/src/pixiewps.c: rename main() to pixiewps_main()
 * 2. Add: int pixiewps_main(int argc, char *argv[]);
 * 3. Replace the fork/exec below with direct pixiewps_main() call
 * 4. Capture stdout via pipe/redirect for PIN parsing
 */

// Path to the pixiewps executable (set by JNI init or computed at runtime)
static char pixiewps_exec_path[512] = {0};

// Called from JNI_OnLoad or set externally
void pixiewps_set_exec_path(const char *path) {
    strncpy(pixiewps_exec_path, path, sizeof(pixiewps_exec_path) - 1);
}

int pixiewps_compute(const char *pke, const char *pkr,
                      const char *e_hash1, const char *e_hash2,
                      const char *auth_key, const char *e_nonce,
                      int force, char *pin_out, int pin_size) {

    if (pixiewps_exec_path[0] == '\0') {
        LOGE("pixiewps_compute: executable path not set");
        return -1;
    }

    // Create pipe to capture pixiewps stdout
    int pipefd[2];
    if (pipe(pipefd) < 0) {
        LOGE("pixiewps_compute: pipe() failed");
        return -1;
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("pixiewps_compute: fork() failed");
        close(pipefd[0]);
        close(pipefd[1]);
        return -1;
    }

    if (pid == 0) {
        // Child: redirect stdout to pipe and exec pixiewps via su
        // Direct execl fails on modern Android (SELinux denies exec from
        // /data/local/tmp for untrusted_app domain). Running through su
        // switches to the root/magisk SELinux domain which has exec permission.
        close(pipefd[0]);
        dup2(pipefd[1], STDOUT_FILENO);
        dup2(pipefd[1], STDERR_FILENO);
        close(pipefd[1]);

        char cmd[8192];
        if (force) {
            snprintf(cmd, sizeof(cmd),
                     "%s --force --pke %s --pkr %s --e-hash1 %s --e-hash2 %s --authkey %s --e-nonce %s",
                     pixiewps_exec_path, pke, pkr, e_hash1, e_hash2, auth_key, e_nonce);
        } else {
            snprintf(cmd, sizeof(cmd),
                     "%s --pke %s --pkr %s --e-hash1 %s --e-hash2 %s --authkey %s --e-nonce %s",
                     pixiewps_exec_path, pke, pkr, e_hash1, e_hash2, auth_key, e_nonce);
        }

        execlp("su", "su", "-c", cmd, (char *)NULL);
        _exit(127);
    }

    // Parent: read pixiewps output
    close(pipefd[1]);

    char buf[4096];
    int total = 0;
    int n;
    while ((n = read(pipefd[0], buf + total, sizeof(buf) - total - 1)) > 0) {
        total += n;
    }
    buf[total] = '\0';
    close(pipefd[0]);

    // Wait for child
    int status;
    waitpid(pid, &status, 0);

    if (WIFSIGNALED(status)) {
        LOGE("pixiewps_compute: killed by signal %d, path=%s", WTERMSIG(status), pixiewps_exec_path);
        pin_out[0] = '\0';
        return -1;
    }

    if (WIFEXITED(status) && WEXITSTATUS(status) == 127) {
        LOGE("pixiewps_compute: execl failed (exit 127), path=%s", pixiewps_exec_path);
        pin_out[0] = '\0';
        return -1;
    }

    LOGI("pixiewps_compute: exit=%d, output len=%d",
         WIFEXITED(status) ? WEXITSTATUS(status) : -1, total);

    // Parse output for "WPS pin:" line
    char *pin_line = strstr(buf, "WPS pin:");
    if (pin_line == NULL) {
        pin_line = strstr(buf, "WPS pin :");
    }

    if (pin_line) {
        char *colon = strchr(pin_line, ':');
        if (colon) {
            colon++; // Skip ':'
            while (*colon == ' ') colon++; // Skip spaces

            // Copy PIN digits
            int i = 0;
            while (colon[i] && colon[i] != '\n' && colon[i] != '\r' && i < pin_size - 1) {
                pin_out[i] = colon[i];
                i++;
            }
            pin_out[i] = '\0';

            LOGI("pixiewps_compute: found PIN=%s", pin_out);
            return 0;
        }
    }

    // Check for "not found"
    if (strstr(buf, "not found")) {
        LOGI("pixiewps_compute: PIN not found (not vulnerable)");
    } else {
        LOGE("pixiewps_compute: unexpected output: %s", buf);
    }

    pin_out[0] = '\0';
    return -1;
}
