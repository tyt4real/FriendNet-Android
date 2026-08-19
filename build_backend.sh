#!/bin/bash
set -e

# Find go binary
GO_BIN=$(which go || echo "/usr/local/go/bin/go")

if ! [ -x "$GO_BIN" ]; then
    echo "Error: go binary not found. Please install Go or set PATH."
    exit 1
fi

# Find NDK path
SDK_PATH="/home/afim/Android/Sdk"
NDK_BASE_PATH="$SDK_PATH/ndk"
NDK_PATH=""

if [ -d "$NDK_BASE_PATH" ]; then
    # Pick the latest version available in the ndk directory
    NDK_PATH=$(ls -d $NDK_BASE_PATH/* 2>/dev/null | tail -1)
fi

# Fallback for older NDK layout (ndk-bundle)
if [ -z "$NDK_PATH" ] && [ -d "$SDK_PATH/ndk-bundle" ]; then
    NDK_PATH="$SDK_PATH/ndk-bundle"
fi

PROJECT_ROOT=$(pwd)
JNI_LIBS_DIR="$PROJECT_ROOT/app/src/main/jniLibs"
GO_APP_MAIN="./client/cmd/client"

# Configuration for cross-compilation
HOST_TAG="linux-x86_64" # Adjust to "darwin-x86_64" if on macOS
MIN_API=26

if [ -n "$NDK_PATH" ]; then
    echo "Found NDK at: $NDK_PATH"
    TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/$HOST_TAG/bin"
else
    echo "Warning: Android NDK not found. x86_64 and armeabi-v7a builds will likely fail."
fi

cd FriendNet

# The upnp module upstream excludes Android (//go:build !android). Inject a
# minimal shim so the client compiles for GOOS=android, then remove it after
# building so the submodule stays pristine.
UPNP_SHIM="upnp/interfaces_android.go"

# The upstream webserver only enables HTTP/2 over TLS (protos.SetHTTP2), not
# cleartext h2c (protos.SetUnencryptedHTTP2). The app's gRPC client (OkHttp)
# speaks HTTP/2 with prior knowledge over a plaintext unix socket, which
# requires UnencryptedHTTP2. Apply a one-line transient patch for the build,
# then restore the file so the submodule stays pristine.
WEBSERVER_FILE="common/webserver/webserver.go"
WEBSERVER_PATCHED=0
if ! grep -q "SetUnencryptedHTTP2" "$WEBSERVER_FILE"; then
    sed -i '/protos.SetHTTP1(true)/a\		protos.SetUnencryptedHTTP2(true)' "$WEBSERVER_FILE"
    WEBSERVER_PATCHED=1
    echo "Patched $WEBSERVER_FILE for cleartext h2c support"
fi
UPNP_SHIM_INJECTED=0
if [ -f "$UPNP_SHIM" ]; then
    echo "Note: $UPNP_SHIM already exists; leaving it in place."
else
    cat > "$UPNP_SHIM" <<'SHIMEOF'
//go:build android

package upnp

import "net"

func listInterfaces() ([]net.Interface, error) {
	return net.Interfaces()
}

func interfaceAddrsByInterface(intf *net.Interface) ([]net.Addr, error) {
	return intf.Addrs()
}
SHIMEOF
    UPNP_SHIM_INJECTED=1
fi

cleanup() {
    if [ "$UPNP_SHIM_INJECTED" = "1" ] && [ -f "$UPNP_SHIM" ]; then
        rm -f "$UPNP_SHIM"
        echo "Removed injected $UPNP_SHIM"
    fi
    if [ "$WEBSERVER_PATCHED" = "1" ]; then
        git checkout -- "$WEBSERVER_FILE"
        echo "Restored $WEBSERVER_FILE"
    fi
}
trap cleanup EXIT

build_arch() {
    local GOARCH=$1
    local ABI=$2
    local TARGET=$3
    local GOARM=$4

    echo "Building for $ABI ($GOARCH)..."
    mkdir -p "$JNI_LIBS_DIR/$ABI"

    if [ -n "$NDK_PATH" ] && [ -n "$TARGET" ]; then
        local CC_BIN="$TOOLCHAIN/${TARGET}${MIN_API}-clang"
        if [ -x "$CC_BIN" ]; then
            echo "  Using NDK Compiler: $(basename $CC_BIN)"
            CGO_ENABLED=1 CC="$CC_BIN" GOOS=android GOARCH=$GOARCH GOARM=$GOARM \
            "$GO_BIN" build -ldflags="-s -w" -o "$JNI_LIBS_DIR/$ABI/libfriendnet.so" "$GO_APP_MAIN"
            return
        fi
    fi

    # Fallback to CGO_ENABLED=0 if NDK is missing or no target specified
    echo "  Attempting build with CGO_ENABLED=0..."
    CGO_ENABLED=0 GOOS=android GOARCH=$GOARCH GOARM=$GOARM \
    "$GO_BIN" build -ldflags="-s -w" -o "$JNI_LIBS_DIR/$ABI/libfriendnet.so" "$GO_APP_MAIN" || echo "  FAILED to build for $ABI"
}

# 1. ARM64 (Most physical devices)
build_arch "arm64" "arm64-v8a" "aarch64-linux-android" ""

# 2. x86_64 (Most emulators)
build_arch "amd64" "x86_64" "x86_64-linux-android" ""

# 3. ARM v7 (Older devices)
build_arch "arm" "armeabi-v7a" "armv7a-linux-androideabi" "7"

echo "Build process finished. Check $JNI_LIBS_DIR for results."
