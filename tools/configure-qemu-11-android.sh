#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JNI_DIR="${ROOT_DIR}/limbo-android-lib/src/main/jni"
QEMU_DIR="${JNI_DIR}/qemu"
BUILD_HOST="${BUILD_HOST:-arm64-v8a}"
BUILD_GUEST="${BUILD_GUEST:-x86_64-softmmu}"
NDK_PLATFORM_API="${NDK_PLATFORM_API:-23}"
USE_ARMV9="${USE_ARMV9:-false}"
USE_VIRGL="${USE_VIRGL:-false}"
CONFIGURE_ONLY="${CONFIGURE_ONLY:-true}"

usage() {
    cat <<EOF
Usage: $0 [options]

Configure QEMU 11 for Android from:
  ${QEMU_DIR}

Options:
  --host ABI           Android host ABI: arm64-v8a, armeabi-v7a, x86, x86_64.
                       Default: ${BUILD_HOST}
  --guest TARGET       QEMU softmmu target. Default: ${BUILD_GUEST}
  --api LEVEL          Android API level. Default: ${NDK_PLATFORM_API}
  --ndk PATH           Android NDK root. Also reads ANDROID_NDK_HOME,
                       ANDROID_NDK_ROOT, or the newest ANDROID_HOME/ndk entry.
  --armv9              Use -march=armv9-a for arm64-v8a host builds.
  --virgl              Enable OpenGL and virglrenderer configure flags.
  --build              Run make after configure.
  -h, --help           Show this help.

Examples:
  BUILD_HOST=arm64-v8a BUILD_GUEST=x86_64-softmmu $0
  $0 --host arm64-v8a --guest x86_64-softmmu --armv9 --virgl
EOF
}

NDK_ROOT="${NDK_ROOT:-${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"

while (($#)); do
    case "$1" in
        --host)
            shift
            BUILD_HOST="${1:?Missing value for --host}"
            ;;
        --guest)
            shift
            BUILD_GUEST="${1:?Missing value for --guest}"
            ;;
        --api)
            shift
            NDK_PLATFORM_API="${1:?Missing value for --api}"
            ;;
        --ndk)
            shift
            NDK_ROOT="${1:?Missing value for --ndk}"
            ;;
        --armv9)
            USE_ARMV9=true
            ;;
        --virgl)
            USE_VIRGL=true
            ;;
        --build)
            CONFIGURE_ONLY=false
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            printf 'Unknown argument: %s\n' "$1" >&2
            usage >&2
            exit 2
            ;;
    esac
    shift
done

if [[ -z "${NDK_ROOT}" && -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}/ndk" ]]; then
    NDK_ROOT="$(find "${ANDROID_HOME}/ndk" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1)"
fi

if [[ -z "${NDK_ROOT}" || ! -d "${NDK_ROOT}" ]]; then
    printf 'Android NDK not found. Set --ndk, NDK_ROOT, ANDROID_NDK_HOME, or ANDROID_NDK_ROOT.\n' >&2
    exit 1
fi

if [[ ! -x "${QEMU_DIR}/configure" ]]; then
    printf 'QEMU source tree not found at %s.\n' "${QEMU_DIR}" >&2
    printf 'Run tools/fetch-qemu-11.sh first.\n' >&2
    exit 1
fi

case "$(uname -s)" in
    Darwin)
        HOST_TAG="darwin-x86_64"
        ;;
    Linux)
        HOST_TAG="linux-x86_64"
        ;;
    *)
        printf 'Unsupported build OS: %s\n' "$(uname -s)" >&2
        exit 1
        ;;
esac

TOOLCHAIN_BIN="${NDK_ROOT}/toolchains/llvm/prebuilt/${HOST_TAG}/bin"
SYSROOT="${NDK_ROOT}/toolchains/llvm/prebuilt/${HOST_TAG}/sysroot"

case "${BUILD_HOST}" in
    arm64-v8a)
        TARGET_TRIPLE="aarch64-linux-android"
        QEMU_CPU="aarch64"
        ARCH_CFLAGS="-march=armv8-a"
        if [[ "${USE_ARMV9}" == true ]]; then
            ARCH_CFLAGS="-march=armv9-a"
        fi
        ;;
    armeabi-v7a)
        TARGET_TRIPLE="armv7a-linux-androideabi"
        QEMU_CPU="arm"
        ARCH_CFLAGS="-march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16"
        ;;
    x86)
        TARGET_TRIPLE="i686-linux-android"
        QEMU_CPU="i386"
        ARCH_CFLAGS="-march=i686"
        ;;
    x86_64)
        TARGET_TRIPLE="x86_64-linux-android"
        QEMU_CPU="x86_64"
        ARCH_CFLAGS="-march=x86-64"
        ;;
    *)
        printf 'Unsupported Android host ABI: %s\n' "${BUILD_HOST}" >&2
        exit 2
        ;;
esac

CC="${TOOLCHAIN_BIN}/${TARGET_TRIPLE}${NDK_PLATFORM_API}-clang"
CXX="${TOOLCHAIN_BIN}/${TARGET_TRIPLE}${NDK_PLATFORM_API}-clang++"

if [[ ! -x "${CC}" || ! -x "${CXX}" ]]; then
    printf 'NDK clang not found for %s API %s under %s\n' "${TARGET_TRIPLE}" "${NDK_PLATFORM_API}" "${TOOLCHAIN_BIN}" >&2
    exit 1
fi

BUILD_DIR="${ROOT_DIR}/build/qemu-android/${BUILD_HOST}-${BUILD_GUEST}"
mkdir -p "${BUILD_DIR}"

export AR="${TOOLCHAIN_BIN}/llvm-ar"
export AS="${CC}"
export CC
export CXX
export LD="${TOOLCHAIN_BIN}/ld.lld"
export NM="${TOOLCHAIN_BIN}/llvm-nm"
export OBJCOPY="${TOOLCHAIN_BIN}/llvm-objcopy"
export RANLIB="${TOOLCHAIN_BIN}/llvm-ranlib"
export STRIP="${TOOLCHAIN_BIN}/llvm-strip"
export PKG_CONFIG="${PKG_CONFIG:-pkg-config}"
export PKG_CONFIG_LIBDIR="${PKG_CONFIG_LIBDIR:-${JNI_DIR}/lib/pkgconfig}"
export CFLAGS="${CFLAGS:-} --sysroot=${SYSROOT} ${ARCH_CFLAGS} -fPIC -D__ANDROID_API__=${NDK_PLATFORM_API}"
export CXXFLAGS="${CXXFLAGS:-} --sysroot=${SYSROOT} ${ARCH_CFLAGS} -fPIC -D__ANDROID_API__=${NDK_PLATFORM_API}"
export LDFLAGS="${LDFLAGS:-} --sysroot=${SYSROOT} -llog -landroid"

CONFIGURE_ARGS=(
    "--target-list=${BUILD_GUEST}"
    "--cpu=${QEMU_CPU}"
    "--cc=${CC}"
    "--host-cc=cc"
    "--extra-cflags=${CFLAGS}"
    "--extra-ldflags=${LDFLAGS}"
    "--enable-system"
    "--disable-user"
    "--disable-tools"
    "--disable-docs"
    "--disable-werror"
    "--enable-sdl"
    "--enable-vnc"
    "--disable-vnc-jpeg"
    "--disable-vnc-sasl"
    "--audio-drv-list=sdl"
    "--disable-gtk"
    "--disable-cocoa"
    "--disable-curses"
    "--disable-pa"
    "--disable-pipewire"
    "--disable-jack"
    "--disable-oss"
    "--disable-plugins"
    "--disable-download"
)

if [[ "${USE_VIRGL}" == true ]]; then
    CONFIGURE_ARGS+=("--enable-opengl" "--enable-virglrenderer")
else
    CONFIGURE_ARGS+=("--disable-opengl" "--disable-virglrenderer")
fi

printf 'Configuring QEMU for Android\n'
printf '  host ABI: %s\n' "${BUILD_HOST}"
printf '  guest:    %s\n' "${BUILD_GUEST}"
printf '  API:      %s\n' "${NDK_PLATFORM_API}"
printf '  NDK:      %s\n' "${NDK_ROOT}"
printf '  build:    %s\n' "${BUILD_DIR}"

cd "${BUILD_DIR}"
"${QEMU_DIR}/configure" "${CONFIGURE_ARGS[@]}"

if [[ "${CONFIGURE_ONLY}" != true ]]; then
    make "-j${BUILD_THREADS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || printf 4)}"
fi
