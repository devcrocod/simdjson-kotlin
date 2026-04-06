#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Optional clean build (removes FetchContent cache in _deps/)
if [ "${1:-}" = "--clean" ]; then
    echo "Cleaning build directories..."
    rm -rf "$SCRIPT_DIR/build-android-arm64-v8a" "$SCRIPT_DIR/build-android-x86_64"
fi

NDK_ROOT="${ANDROID_NDK_HOME:-${ANDROID_HOME}/ndk/$(ls "${ANDROID_HOME}/ndk/" | sort -V | tail -1)}"

if [ ! -d "$NDK_ROOT" ]; then
    echo "Error: NDK not found. Set ANDROID_NDK_HOME or install NDK via sdkmanager." >&2
    exit 1
fi

echo "Using NDK: $NDK_ROOT"

# CMake requires CMakeLists.txt as filename — copy our Android config to a temp dir
TEMP_SRC=$(mktemp -d)
trap 'rm -rf "${TEMP_SRC}"' EXIT
cp "${SCRIPT_DIR}/CMakeLists-android.txt" "${TEMP_SRC}/CMakeLists.txt"

for ABI in arm64-v8a x86_64; do
    BUILD_DIR="$SCRIPT_DIR/build-android-$ABI"
    echo "=== Building for Android $ABI ==="

    cmake -S "${TEMP_SRC}" -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$NDK_ROOT/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM=android-26 \
        -DCMAKE_BUILD_TYPE=Release \
        -DSIMDJSON_NATIVE_DIR="${SCRIPT_DIR}"

    cmake --build "$BUILD_DIR" --config Release --parallel

    OUTPUT="$BUILD_DIR/libsimdjson_jni.so"
    if [ ! -f "$OUTPUT" ]; then
        echo "ERROR: Build output not found: $OUTPUT" >&2
        exit 1
    fi
    echo "Built: $OUTPUT"
    ls -la "$OUTPUT"
done

echo "=== Done ==="
echo "arm64-v8a: $SCRIPT_DIR/build-android-arm64-v8a/libsimdjson_jni.so"
echo "x86_64:    $SCRIPT_DIR/build-android-x86_64/libsimdjson_jni.so"
