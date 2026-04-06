#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Detect OS
case "$(uname -s)" in
    Darwin) OS="macos" ;;
    Linux)  OS="linux" ;;
    MINGW*|MSYS*|CYGWIN*) OS="windows" ;;
    *) echo "Unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

# Detect architecture
case "$(uname -m)" in
    arm64|aarch64) ARCH="aarch64" ;;
    x86_64|amd64)  ARCH="x86_64" ;;
    *) echo "Unsupported arch: $(uname -m)" >&2; exit 1 ;;
esac

BUILD_DIR="${SCRIPT_DIR}/build-jvm-${OS}-${ARCH}"

echo "Building simdjson_jni for ${OS}-${ARCH}..."

# CMake requires CMakeLists.txt as filename — copy our JVM config to a temp dir
TEMP_SRC=$(mktemp -d)
trap 'rm -rf "${TEMP_SRC}"' EXIT
cp "${SCRIPT_DIR}/CMakeLists-jvm.txt" "${TEMP_SRC}/CMakeLists.txt"

# Remove stale CMake cache — source dir is a fresh tmpdir each run
rm -f "${BUILD_DIR}/CMakeCache.txt"

cmake -S "${TEMP_SRC}" -B "${BUILD_DIR}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DSIMDJSON_NATIVE_DIR="${SCRIPT_DIR}"

cmake --build "${BUILD_DIR}" --config Release --parallel

# Verify output
case "${OS}" in
    macos)   LIB_FILE="libsimdjson_jni.dylib" ;;
    linux)   LIB_FILE="libsimdjson_jni.so" ;;
    windows) LIB_FILE="simdjson_jni.dll" ;;
esac

OUTPUT="${BUILD_DIR}/${LIB_FILE}"
if [ ! -f "${OUTPUT}" ]; then
    echo "ERROR: Build output not found: ${OUTPUT}" >&2
    exit 1
fi

echo "Build complete: ${OUTPUT}"
ls -la "${OUTPUT}"
