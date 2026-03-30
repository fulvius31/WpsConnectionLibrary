#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <android/log.h>

#include "wpsnative_jni.h"
#include "../supplicant/supplicant_launcher.h"
#include "../pixiewps/pixiewps_wrapper.h"

#define LOG_TAG "WpsNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Forward declarations
extern void pixiewps_set_exec_path(const char *path);
extern void wpa_ctrl_set_local_dir(const char *dir);

// =============================================================================
// JNI: nativeInit
// Set up paths for internal executables
// =============================================================================
JNIEXPORT void JNICALL
Java_sangiorgi_wps_lib_ndk_WpsNative_nativeInit(
        JNIEnv *env, jobject thiz, jstring jExecDir, jstring jFilesDir) {
    const char *exec_dir = (*env)->GetStringUTFChars(env, jExecDir, NULL);
    if (exec_dir) {
        // Set pixiewps executable path: <execDir>/libpixiewps_exec.so
        char path[512];
        snprintf(path, sizeof(path), "%s/libpixiewps_exec.so", exec_dir);
        pixiewps_set_exec_path(path);
        LOGI("nativeInit: execDir=%s", exec_dir);
        (*env)->ReleaseStringUTFChars(env, jExecDir, exec_dir);
    }

    const char *files_dir = (*env)->GetStringUTFChars(env, jFilesDir, NULL);
    if (files_dir) {
        wpa_ctrl_set_local_dir(files_dir);
        LOGI("nativeInit: filesDir=%s", files_dir);
        (*env)->ReleaseStringUTFChars(env, jFilesDir, files_dir);
    }
}

// Helper to convert jstring to C string (caller must free)
static char *jstring_to_cstr(JNIEnv *env, jstring jstr) {
    if (jstr == NULL) return NULL;
    const char *utf = (*env)->GetStringUTFChars(env, jstr, NULL);
    if (utf == NULL) return NULL;
    char *result = strdup(utf);
    (*env)->ReleaseStringUTFChars(env, jstr, utf);
    return result;
}

// =============================================================================
// JNI: nativeCreateSession
// Creates a native session from a stdout fd provided by Java.
// Java launches "su -c wpa_supplicant ..." via Runtime.exec() for proper
// Magisk mount namespace handling.
// =============================================================================
JNIEXPORT jlong JNICALL
Java_sangiorgi_wps_lib_ndk_WpsNative_nativeCreateSession(
        JNIEnv *env, jobject thiz,
        jint stdoutFd, jstring jCtrlDir, jstring jIface) {

    char *ctrl_dir = jstring_to_cstr(env, jCtrlDir);
    char *iface = jstring_to_cstr(env, jIface);

    if (!ctrl_dir || !iface) {
        LOGE("nativeCreateSession: null parameter");
        free(ctrl_dir); free(iface);
        return 0;
    }

    wps_session_t *session = session_create(stdoutFd, ctrl_dir, iface);

    free(ctrl_dir);
    free(iface);

    if (session == NULL) {
        LOGE("nativeCreateSession: failed to create session");
        return 0;
    }

    LOGI("nativeCreateSession: created, fd=%d", session->stdout_fd);
    return (jlong)(intptr_t)session;
}

// =============================================================================
// JNI: nativeDestroySession
// Cleans up the native session. Java destroys the actual su Process.
// =============================================================================
JNIEXPORT void JNICALL
Java_sangiorgi_wps_lib_ndk_WpsNative_nativeDestroySession(
        JNIEnv *env, jobject thiz, jlong handle) {

    wps_session_t *session = (wps_session_t *)(intptr_t)handle;
    if (session == NULL) return;

    session_destroy(session);
    LOGI("nativeDestroySession: done");
}

// =============================================================================
// JNI: readWpsResult
// Reads wpa_supplicant output and parses WPS result
// Returns a WpsResult object
// =============================================================================
JNIEXPORT jobject JNICALL
Java_sangiorgi_wps_lib_ndk_WpsNative_readWpsResult(
        JNIEnv *env, jobject thiz, jlong handle, jint timeoutMs) {

    wps_session_t *session = (wps_session_t *)(intptr_t)handle;
    if (session == NULL) return NULL;

    wps_result_t result;
    memset(&result, 0, sizeof(result));
    result.status = WPS_STATUS_TIMEOUT;

    supplicant_read_wps_result(session, &result, timeoutMs);

    // Create WpsResult Java object
    jclass cls = (*env)->FindClass(env, "sangiorgi/wps/lib/ndk/WpsResult");
    if (cls == NULL) {
        LOGE("readWpsResult: WpsResult class not found");
        return NULL;
    }

    jmethodID ctor = (*env)->GetMethodID(env, cls, "<init>",
        "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    if (ctor == NULL) {
        LOGE("readWpsResult: WpsResult constructor not found");
        return NULL;
    }

    jstring jKey = result.network_key[0] ? (*env)->NewStringUTF(env, result.network_key) : NULL;
    jstring jRaw = result.raw_line[0] ? (*env)->NewStringUTF(env, result.raw_line) : NULL;
    jstring jLog = result.exchange_log[0] ? (*env)->NewStringUTF(env, result.exchange_log) : NULL;

    return (*env)->NewObject(env, cls, ctor, result.status, jKey, jRaw, jLog);
}

// =============================================================================
// JNI: extractPixieDustParams
// Reads wpa_supplicant debug output and extracts the 6 PixieDust hex values
// Returns String[6] or null
// =============================================================================
JNIEXPORT jobjectArray JNICALL
Java_sangiorgi_wps_lib_ndk_WpsNative_extractPixieDustParams(
        JNIEnv *env, jobject thiz, jlong handle, jint timeoutMs) {

    wps_session_t *session = (wps_session_t *)(intptr_t)handle;
    if (session == NULL) return NULL;

    pixiedust_params_t params;
    memset(&params, 0, sizeof(params));

    int ret = supplicant_extract_pixiedust(session, &params, timeoutMs);
    if (ret < 0) {
        LOGE("extractPixieDustParams: failed, ret=%d", ret);
        return NULL;
    }

    // Check all params are populated
    if (!params.enrollee_nonce[0] || !params.dh_own_pubkey[0] ||
        !params.dh_peer_pubkey[0] || !params.auth_key[0] ||
        !params.e_hash1[0] || !params.e_hash2[0]) {
        LOGE("extractPixieDustParams: incomplete params");
        return NULL;
    }

    // Create String[6]
    jclass strClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray arr = (*env)->NewObjectArray(env, 6, strClass, NULL);

    (*env)->SetObjectArrayElement(env, arr, 0, (*env)->NewStringUTF(env, params.enrollee_nonce));
    (*env)->SetObjectArrayElement(env, arr, 1, (*env)->NewStringUTF(env, params.dh_own_pubkey));
    (*env)->SetObjectArrayElement(env, arr, 2, (*env)->NewStringUTF(env, params.dh_peer_pubkey));
    (*env)->SetObjectArrayElement(env, arr, 3, (*env)->NewStringUTF(env, params.auth_key));
    (*env)->SetObjectArrayElement(env, arr, 4, (*env)->NewStringUTF(env, params.e_hash1));
    (*env)->SetObjectArrayElement(env, arr, 5, (*env)->NewStringUTF(env, params.e_hash2));

    LOGI("extractPixieDustParams: all 6 params extracted");
    return arr;
}

// =============================================================================
// JNI: computePixiePin
// Pure computation - runs pixiewps algorithm (NO root needed)
// =============================================================================
JNIEXPORT jstring JNICALL
Java_sangiorgi_wps_lib_ndk_WpsNative_computePixiePin(
        JNIEnv *env, jobject thiz,
        jstring jPke, jstring jPkr,
        jstring jEHash1, jstring jEHash2,
        jstring jAuthKey, jstring jENonce,
        jboolean force) {

    char *pke = jstring_to_cstr(env, jPke);
    char *pkr = jstring_to_cstr(env, jPkr);
    char *e_hash1 = jstring_to_cstr(env, jEHash1);
    char *e_hash2 = jstring_to_cstr(env, jEHash2);
    char *auth_key = jstring_to_cstr(env, jAuthKey);
    char *e_nonce = jstring_to_cstr(env, jENonce);

    if (!pke || !pkr || !e_hash1 || !e_hash2 || !auth_key || !e_nonce) {
        LOGE("computePixiePin: null parameter");
        free(pke); free(pkr); free(e_hash1); free(e_hash2);
        free(auth_key); free(e_nonce);
        return NULL;
    }

    char pin_out[16];
    memset(pin_out, 0, sizeof(pin_out));

    int ret = pixiewps_compute(pke, pkr, e_hash1, e_hash2,
                                auth_key, e_nonce, force ? 1 : 0, pin_out, sizeof(pin_out));

    free(pke); free(pkr); free(e_hash1); free(e_hash2);
    free(auth_key); free(e_nonce);

    if (ret < 0 || pin_out[0] == '\0') {
        LOGI("computePixiePin: PIN not found");
        return NULL;
    }

    LOGI("computePixiePin: found PIN=%s", pin_out);
    return (*env)->NewStringUTF(env, pin_out);
}

