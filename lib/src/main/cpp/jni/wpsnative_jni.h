#ifndef WPSNATIVE_JNI_H
#define WPSNATIVE_JNI_H

#include <jni.h>
#include <pthread.h>

// Session handle for a running wpa_supplicant instance.
// The su process is launched and owned by Java (Runtime.exec) for proper
// Magisk mount namespace handling. Native code only holds the stdout fd
// for output parsing.
typedef struct {
    int stdout_fd;          // File descriptor to read stdout (owned by native, from Java pipe)
    volatile int running;   // Is the session still active
    char ctrl_path[256];    // Control interface path (e.g., /data/vendor/wifi/wpa/wpswpatester/)
    char iface[32];         // Interface name (e.g., wlan0)
    pthread_mutex_t lock;   // Thread safety
} wps_session_t;

// WPS result codes matching Java WpsResult.Status enum
#define WPS_STATUS_SUCCESS      0
#define WPS_STATUS_FOUR_FAIL    1
#define WPS_STATUS_THREE_FAIL   2
#define WPS_STATUS_LOCKED       3
#define WPS_STATUS_CRC_FAIL     4
#define WPS_STATUS_SELINUX      5
#define WPS_STATUS_TIMEOUT      6
#define WPS_STATUS_ERROR        7

// WPS result structure
typedef struct {
    int status;
    char network_key[256];  // Password on success
    char raw_line[1024];    // Raw output line for debugging
} wps_result_t;

// Pixie Dust parameters
typedef struct {
    char enrollee_nonce[128];
    char dh_own_pubkey[1024];   // PKE
    char dh_peer_pubkey[1024];  // PKR
    char auth_key[128];
    char e_hash1[128];
    char e_hash2[128];
} pixiedust_params_t;

#endif // WPSNATIVE_JNI_H
