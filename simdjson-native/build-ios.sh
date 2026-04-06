#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Optional clean build (removes FetchContent cache in _deps/)
if [ "${1:-}" = "--clean" ]; then
    echo "Cleaning build directories..."
    rm -rf "$SCRIPT_DIR/build-ios-arm64" "$SCRIPT_DIR/build-ios-simulator-arm64"
fi

# Validate iOS SDKs
IPHONEOS_SDK=$(xcrun --sdk iphoneos --show-sdk-path 2>/dev/null || true)
IPHONESIMULATOR_SDK=$(xcrun --sdk iphonesimulator --show-sdk-path 2>/dev/null || true)

if [ -z "$IPHONEOS_SDK" ] || [ ! -d "$IPHONEOS_SDK" ]; then
    echo "Error: iOS SDK not found. Install Xcode and run 'xcode-select --install'." >&2
    exit 1
fi

if [ -z "$IPHONESIMULATOR_SDK" ] || [ ! -d "$IPHONESIMULATOR_SDK" ]; then
    echo "Error: iOS Simulator SDK not found. Install Xcode and run 'xcode-select --install'." >&2
    exit 1
fi

# --- iOS Device (iosArm64) ---
echo "=== Building for iOS Device (arm64) ==="
cmake -S "$SCRIPT_DIR" -B "$SCRIPT_DIR/build-ios-arm64" \
    -DCMAKE_SYSTEM_NAME=iOS \
    -DCMAKE_OSX_SYSROOT="$IPHONEOS_SDK" \
    -DCMAKE_OSX_ARCHITECTURES=arm64 \
    -DCMAKE_BUILD_TYPE=Release

cmake --build "$SCRIPT_DIR/build-ios-arm64" --config Release --parallel

OUTPUT="$SCRIPT_DIR/build-ios-arm64/libsimdjson_c.a"
if [ ! -f "$OUTPUT" ]; then
    echo "ERROR: Build output not found: $OUTPUT" >&2
    exit 1
fi

# --- iOS Simulator (iosSimulatorArm64) ---
echo "=== Building for iOS Simulator (arm64) ==="
cmake -S "$SCRIPT_DIR" -B "$SCRIPT_DIR/build-ios-simulator-arm64" \
    -DCMAKE_SYSTEM_NAME=iOS \
    -DCMAKE_OSX_SYSROOT="$IPHONESIMULATOR_SDK" \
    -DCMAKE_OSX_ARCHITECTURES=arm64 \
    -DCMAKE_BUILD_TYPE=Release

cmake --build "$SCRIPT_DIR/build-ios-simulator-arm64" --config Release --parallel

OUTPUT="$SCRIPT_DIR/build-ios-simulator-arm64/libsimdjson_c.a"
if [ ! -f "$OUTPUT" ]; then
    echo "ERROR: Build output not found: $OUTPUT" >&2
    exit 1
fi

echo "=== Done ==="
echo "iOS Device:    $SCRIPT_DIR/build-ios-arm64/libsimdjson_c.a"
echo "iOS Simulator: $SCRIPT_DIR/build-ios-simulator-arm64/libsimdjson_c.a"
