cmake_minimum_required(VERSION 3.18)
project(wpsnative C)

set(CMAKE_C_STANDARD 11)

# ==============================================================================
# Third-party dependencies (static libraries)
# ==============================================================================
# FetchContent no longer needed - all dependencies use ExternalProject

# Map Android ABI to OpenSSL target and compiler host
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(OPENSSL_TARGET "android-arm64")
    set(COMPILER_PREFIX "aarch64-linux-android")
    set(CONFIGURE_HOST "aarch64-linux-android")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(OPENSSL_TARGET "android-arm")
    set(COMPILER_PREFIX "armv7a-linux-androideabi")
    set(CONFIGURE_HOST "arm-linux-androideabi")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(OPENSSL_TARGET "android-x86_64")
    set(COMPILER_PREFIX "x86_64-linux-android")
    set(CONFIGURE_HOST "x86_64-linux-android")
else()
    message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
endif()

set(NDK_TOOLCHAIN "${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64")
set(ANDROID_API_LEVEL 23)

# Create dummy flex/bison scripts for CI environments that lack them.
# The libnl release tarball ships pre-generated .c files so these are never
# actually invoked — they only satisfy the configure-time check.
set(DUMMY_TOOLS_DIR "${CMAKE_CURRENT_BINARY_DIR}/dummy-tools")
file(MAKE_DIRECTORY "${DUMMY_TOOLS_DIR}")
file(WRITE "${DUMMY_TOOLS_DIR}/flex" "#!/bin/sh\nexit 0\n")
file(WRITE "${DUMMY_TOOLS_DIR}/bison" "#!/bin/sh\nexit 0\n")
execute_process(COMMAND chmod +x "${DUMMY_TOOLS_DIR}/flex" "${DUMMY_TOOLS_DIR}/bison")

# Install directories
set(OPENSSL_INSTALL_DIR "${CMAKE_CURRENT_BINARY_DIR}/openssl-install/${ANDROID_ABI}")
set(LIBNL_INSTALL_DIR "${CMAKE_CURRENT_BINARY_DIR}/libnl-install/${ANDROID_ABI}")
set(HOSTAP_SOURCE_DIR "${CMAKE_CURRENT_BINARY_DIR}/hostap-src")

# Pre-create include directories so CMake configure-time validation passes.
# The actual headers get populated when ExternalProject builds run.
file(MAKE_DIRECTORY "${OPENSSL_INSTALL_DIR}/include")
file(MAKE_DIRECTORY "${OPENSSL_INSTALL_DIR}/lib")
file(MAKE_DIRECTORY "${LIBNL_INSTALL_DIR}/include/libnl3")
file(MAKE_DIRECTORY "${LIBNL_INSTALL_DIR}/lib")

# ==============================================================================
# OpenSSL (static build)
# ==============================================================================
set(OPENSSL_VERSION "3.2.1")
set(OPENSSL_SOURCE_DIR "${CMAKE_CURRENT_BINARY_DIR}/openssl-src")

include(ExternalProject)

ExternalProject_Add(openssl_external
    URL "https://github.com/openssl/openssl/releases/download/openssl-${OPENSSL_VERSION}/openssl-${OPENSSL_VERSION}.tar.gz"
    SOURCE_DIR "${OPENSSL_SOURCE_DIR}"
    INSTALL_DIR "${OPENSSL_INSTALL_DIR}"
    CONFIGURE_COMMAND
        ${CMAKE_COMMAND} -E env
            "ANDROID_NDK_ROOT=${ANDROID_NDK}"
            "PATH=${NDK_TOOLCHAIN}/bin:$ENV{PATH}"
        ${OPENSSL_SOURCE_DIR}/Configure
            ${OPENSSL_TARGET}
            -D__ANDROID_API__=${ANDROID_API_LEVEL}
            no-shared
            no-tests
            no-ui-console
            no-comp
            no-engine
            no-dso
            --prefix=${OPENSSL_INSTALL_DIR}
            --openssldir=${OPENSSL_INSTALL_DIR}
    BUILD_COMMAND
        ${CMAKE_COMMAND} -E env
            "ANDROID_NDK_ROOT=${ANDROID_NDK}"
            "PATH=${NDK_TOOLCHAIN}/bin:$ENV{PATH}"
        make -j4
    INSTALL_COMMAND
        ${CMAKE_COMMAND} -E env
            "ANDROID_NDK_ROOT=${ANDROID_NDK}"
            "PATH=${NDK_TOOLCHAIN}/bin:$ENV{PATH}"
        make install_sw
    BUILD_IN_SOURCE 1
    BUILD_BYPRODUCTS
        "${OPENSSL_INSTALL_DIR}/lib/libcrypto.a"
        "${OPENSSL_INSTALL_DIR}/lib/libssl.a"
    LOG_CONFIGURE ON
    LOG_BUILD ON
    LOG_INSTALL ON
)

# Imported targets for OpenSSL
add_library(openssl_crypto STATIC IMPORTED)
set_target_properties(openssl_crypto PROPERTIES
    IMPORTED_LOCATION "${OPENSSL_INSTALL_DIR}/lib/libcrypto.a"
    INTERFACE_INCLUDE_DIRECTORIES "${OPENSSL_INSTALL_DIR}/include"
)
add_dependencies(openssl_crypto openssl_external)

add_library(openssl_ssl STATIC IMPORTED)
set_target_properties(openssl_ssl PROPERTIES
    IMPORTED_LOCATION "${OPENSSL_INSTALL_DIR}/lib/libssl.a"
    INTERFACE_INCLUDE_DIRECTORIES "${OPENSSL_INSTALL_DIR}/include"
)
add_dependencies(openssl_ssl openssl_external)

# ==============================================================================
# libnl (static build)
# ==============================================================================
set(LIBNL_VERSION "3.2.25")
set(LIBNL_SOURCE_DIR "${CMAKE_CURRENT_BINARY_DIR}/libnl-src")

ExternalProject_Add(libnl_external
    URL "https://github.com/thom311/libnl/releases/download/libnl3_2_25/libnl-${LIBNL_VERSION}.tar.gz"
    SOURCE_DIR "${LIBNL_SOURCE_DIR}"
    INSTALL_DIR "${LIBNL_INSTALL_DIR}"
    CONFIGURE_COMMAND
        ${CMAKE_COMMAND} -E env
            "CC=${NDK_TOOLCHAIN}/bin/${COMPILER_PREFIX}${ANDROID_API_LEVEL}-clang"
            "AR=${NDK_TOOLCHAIN}/bin/llvm-ar"
            "RANLIB=${NDK_TOOLCHAIN}/bin/llvm-ranlib"
            "STRIP=${NDK_TOOLCHAIN}/bin/llvm-strip"
            "CFLAGS=-fPIC -D__ANDROID_API__=${ANDROID_API_LEVEL}"
            "PATH=${DUMMY_TOOLS_DIR}:$ENV{PATH}"
        ${LIBNL_SOURCE_DIR}/configure
            --host=${CONFIGURE_HOST}
            --prefix=${LIBNL_INSTALL_DIR}
            --disable-shared
            --enable-static
            --disable-cli
            --disable-pthreads
            "LEX=${DUMMY_TOOLS_DIR}/flex"
            "YACC=${DUMMY_TOOLS_DIR}/bison"
    BUILD_COMMAND make -j4
    INSTALL_COMMAND make install
    BUILD_IN_SOURCE 1
    BUILD_BYPRODUCTS
        "${LIBNL_INSTALL_DIR}/lib/libnl-3.a"
        "${LIBNL_INSTALL_DIR}/lib/libnl-genl-3.a"
        "${LIBNL_INSTALL_DIR}/lib/libnl-route-3.a"
    LOG_CONFIGURE OFF
    LOG_BUILD ON
    LOG_INSTALL ON
)

# Imported targets for libnl
add_library(libnl3 STATIC IMPORTED)
set_target_properties(libnl3 PROPERTIES
    IMPORTED_LOCATION "${LIBNL_INSTALL_DIR}/lib/libnl-3.a"
    INTERFACE_INCLUDE_DIRECTORIES "${LIBNL_INSTALL_DIR}/include/libnl3"
)
add_dependencies(libnl3 libnl_external)

add_library(libnl_genl STATIC IMPORTED)
set_target_properties(libnl_genl PROPERTIES
    IMPORTED_LOCATION "${LIBNL_INSTALL_DIR}/lib/libnl-genl-3.a"
    INTERFACE_INCLUDE_DIRECTORIES "${LIBNL_INSTALL_DIR}/include/libnl3"
)
add_dependencies(libnl_genl libnl_external)

add_library(libnl_route STATIC IMPORTED)
set_target_properties(libnl_route PROPERTIES
    IMPORTED_LOCATION "${LIBNL_INSTALL_DIR}/lib/libnl-route-3.a"
    INTERFACE_INCLUDE_DIRECTORIES "${LIBNL_INSTALL_DIR}/include/libnl3"
)
add_dependencies(libnl_route libnl_external)

# ==============================================================================
# hostap / wpa_supplicant (built as external project)
# ==============================================================================
set(HOSTAP_BUILD_DIR "${CMAKE_CURRENT_BINARY_DIR}/hostap-build/${ANDROID_ABI}")

ExternalProject_Add(hostap_external
    GIT_REPOSITORY "https://w1.fi/hostap.git"
    GIT_TAG "hostap_2_10"
    GIT_SHALLOW ON
    SOURCE_DIR "${HOSTAP_SOURCE_DIR}"
    PATCH_COMMAND
        sed -i "s/-lrt//g" ${HOSTAP_SOURCE_DIR}/wpa_supplicant/Makefile &&
        sed -i "s|define CONFIG_CTRL_IFACE_CLIENT_DIR \"/tmp\"|define CONFIG_CTRL_IFACE_CLIENT_DIR \"/data/local/tmp\"|" ${HOSTAP_SOURCE_DIR}/src/common/wpa_ctrl.c
    CONFIGURE_COMMAND ""
    BUILD_COMMAND
        ${CMAKE_COMMAND} -E env
            "CC=${NDK_TOOLCHAIN}/bin/${COMPILER_PREFIX}${ANDROID_API_LEVEL}-clang"
            "AR=${NDK_TOOLCHAIN}/bin/llvm-ar"
            "RANLIB=${NDK_TOOLCHAIN}/bin/llvm-ranlib"
            "CFLAGS=-fPIC -D__ANDROID_API__=${ANDROID_API_LEVEL} -I${OPENSSL_INSTALL_DIR}/include -I${LIBNL_INSTALL_DIR}/include/libnl3"
            "LDFLAGS=-L${OPENSSL_INSTALL_DIR}/lib -L${LIBNL_INSTALL_DIR}/lib -lm -Wl,-z,max-page-size=16384"
        make -C ${HOSTAP_SOURCE_DIR}/wpa_supplicant
            CONFIG_FILE=${CMAKE_CURRENT_SOURCE_DIR}/supplicant/wpa_supplicant_defconfig
            LIBDIR=${HOSTAP_BUILD_DIR}
            BINDIR=${HOSTAP_BUILD_DIR}
            -j4
    INSTALL_COMMAND ""
    BUILD_IN_SOURCE 1
    BUILD_BYPRODUCTS
        "${HOSTAP_SOURCE_DIR}/wpa_supplicant/wpa_supplicant"
        "${HOSTAP_SOURCE_DIR}/wpa_supplicant/wpa_cli"
    DEPENDS openssl_external libnl_external
    LOG_BUILD ON
)

# ==============================================================================
# pixiewps (built as external project using its own Makefile)
# ==============================================================================
set(PIXIEWPS_SOURCE_DIR "${CMAKE_CURRENT_BINARY_DIR}/pixiewps-src")

ExternalProject_Add(pixiewps_external
    GIT_REPOSITORY "https://github.com/wiire-a/pixiewps.git"
    GIT_TAG "master"
    SOURCE_DIR "${PIXIEWPS_SOURCE_DIR}"
    CONFIGURE_COMMAND ""
    BUILD_COMMAND
        ${CMAKE_COMMAND} -E env
            "CC=${NDK_TOOLCHAIN}/bin/${COMPILER_PREFIX}${ANDROID_API_LEVEL}-clang"
        make -C ${PIXIEWPS_SOURCE_DIR}
            "CFLAGS=-O2"
            "LDFLAGS=-Wl,-z,max-page-size=16384"
            LIBS=
            pixiewps
    INSTALL_COMMAND ""
    BUILD_IN_SOURCE 1
    BUILD_BYPRODUCTS "${PIXIEWPS_SOURCE_DIR}/pixiewps"
    LOG_BUILD ON
)

# ==============================================================================
# Main JNI shared library: libwpsnative.so
# ==============================================================================
add_library(wpsnative SHARED
    jni/wpsnative_jni.c
    wpa_ctrl/wpa_ctrl_wrapper.c
    supplicant/supplicant_launcher.c
    pixiewps/pixiewps_wrapper.c
)

target_include_directories(wpsnative PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
    ${CMAKE_CURRENT_SOURCE_DIR}/wpa_ctrl
    ${CMAKE_CURRENT_SOURCE_DIR}/supplicant
    ${CMAKE_CURRENT_SOURCE_DIR}/pixiewps
    ${OPENSSL_INSTALL_DIR}/include
    ${LIBNL_INSTALL_DIR}/include/libnl3
)

# Ensure wpsnative waits for external projects to finish
add_dependencies(wpsnative openssl_external libnl_external hostap_external pixiewps_exec wpa_supplicant_exec)

target_compile_options(wpsnative PRIVATE
    -ffunction-sections
    -fdata-sections
    -Wall
    -Wextra
    -O2
)

target_link_options(wpsnative PRIVATE
    -Wl,--gc-sections
    -Wl,-z,max-page-size=16384
)

target_link_libraries(wpsnative
    log
    android
)

# ==============================================================================
# Pixiewps executable - copy from build and rename as lib*.so
# ==============================================================================
add_custom_target(pixiewps_exec ALL
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "${PIXIEWPS_SOURCE_DIR}/pixiewps"
        "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libpixiewps_exec.so"
    DEPENDS pixiewps_external
    COMMENT "Copying pixiewps executable as libpixiewps_exec.so"
)

# ==============================================================================
# wpa_supplicant executable - copy from hostap build and rename as lib*.so
# ==============================================================================
add_custom_target(wpa_supplicant_exec ALL
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "${HOSTAP_SOURCE_DIR}/wpa_supplicant/wpa_supplicant"
        "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libwpa_supplicant_exec.so"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "${HOSTAP_SOURCE_DIR}/wpa_supplicant/wpa_cli"
        "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libwpa_cli_exec.so"
    DEPENDS hostap_external
    COMMENT "Copying wpa_supplicant and wpa_cli executables"
)
