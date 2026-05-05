#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JNI_DIR="${ROOT_DIR}/limbo-android-lib/src/main/jni"
QEMU_DIR="${JNI_DIR}/qemu"
BUILD_HOST="${BUILD_HOST:-arm64-v8a}"
BUILD_GUEST="${BUILD_GUEST:-x86_64-softmmu}"
NDK_PLATFORM_API="${NDK_PLATFORM_API:-23}"
HOST_TAG="${HOST_TAG:-}"
PYTHON_BIN="${PYTHON_BIN:-${PYTHON:-}}"
NINJA_BIN="${NINJA_BIN:-}"
DEPS_PREFIX="${QEMU_DEPS_PREFIX:-}"
USE_ARMV9="${USE_ARMV9:-false}"
USE_VIRGL="${USE_VIRGL:-false}"
ALLOW_DOWNLOADS="${ALLOW_DOWNLOADS:-false}"
APPLY_ANDROID_PATCHES="${APPLY_ANDROID_PATCHES:-true}"
CONFIGURE_ONLY="${CONFIGURE_ONLY:-true}"
BUILD_TARGETS="${BUILD_TARGETS:-}"
DRY_RUN="${DRY_RUN:-false}"

usage() {
    cat <<EOF
Usage: $0 [options]

Configure QEMU 11 for Android from:
  ${QEMU_DIR}

Options:
  --host ABI           Android host ABI: arm64-v8a or x86_64. QEMU 11 no
                       longer supports 32-bit hosts.
                       Default: ${BUILD_HOST}
  --guest TARGET       QEMU softmmu target. Default: ${BUILD_GUEST}
  --api LEVEL          Android API level. Default: ${NDK_PLATFORM_API}
  --ndk PATH           Android NDK root. Also reads ANDROID_NDK_HOME,
                       ANDROID_NDK_ROOT, or the newest ANDROID_HOME/ndk entry.
  --host-tag TAG       NDK prebuilt host tag. Auto-detected by default.
  --python PATH        Python for QEMU configure. Auto-detects a Python with
                       venv and wheel before falling back to the NDK Python.
  --ninja PATH         Ninja executable. Auto-detects Android SDK CMake's ninja
                       before falling back to PATH.
  --deps-prefix PATH   Prefix containing Android target dependencies. Defaults
                       to jni/deps/<host>-<profile>.
  --armv9              Use -march=armv9-a for arm64-v8a host builds.
  --virgl              Enable OpenGL and virglrenderer configure flags.
  --allow-downloads    Let QEMU configure download Python/build dependencies
                       such as wheel when they are absent from python/wheels.
  --no-android-patches Do not apply the Android host compatibility patch set
                       before configuring QEMU.
  --dry-run            Print the configure environment and command, then exit.
  --build              Build the configured qemu-system-* binary after
                       configure. Override target names with BUILD_TARGETS.
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
        --host-tag)
            shift
            HOST_TAG="${1:?Missing value for --host-tag}"
            ;;
        --python)
            shift
            PYTHON_BIN="${1:?Missing value for --python}"
            ;;
        --ninja)
            shift
            NINJA_BIN="${1:?Missing value for --ninja}"
            ;;
        --deps-prefix)
            shift
            DEPS_PREFIX="${1:?Missing value for --deps-prefix}"
            ;;
        --armv9)
            USE_ARMV9=true
            ;;
        --virgl)
            USE_VIRGL=true
            ;;
        --allow-downloads)
            ALLOW_DOWNLOADS=true
            ;;
        --no-android-patches)
            APPLY_ANDROID_PATCHES=false
            ;;
        --dry-run)
            DRY_RUN=true
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

case "$(uname -s)" in
    Darwin)
        HOST_TAG_CANDIDATES=("darwin-$(uname -m)" "darwin-x86_64" "darwin-arm64")
        ;;
    Linux)
        HOST_TAG_CANDIDATES=("linux-$(uname -m)" "linux-x86_64" "linux-aarch64")
        ;;
    *)
        printf 'Unsupported build OS: %s\n' "$(uname -s)" >&2
        exit 1
        ;;
esac

if [[ -z "${HOST_TAG}" ]]; then
    for candidate in "${HOST_TAG_CANDIDATES[@]}"; do
        if [[ -d "${NDK_ROOT}/toolchains/llvm/prebuilt/${candidate}" ]]; then
            HOST_TAG="${candidate}"
            break
        fi
    done
fi

if [[ -z "${HOST_TAG}" ]]; then
    printf 'Could not find an NDK llvm prebuilt under %s/toolchains/llvm/prebuilt.\n' "${NDK_ROOT}" >&2
    printf 'Tried: %s\n' "${HOST_TAG_CANDIDATES[*]}" >&2
    printf 'Use --host-tag to select one manually.\n' >&2
    exit 1
fi

TOOLCHAIN_BIN="${NDK_ROOT}/toolchains/llvm/prebuilt/${HOST_TAG}/bin"
SYSROOT="${NDK_ROOT}/toolchains/llvm/prebuilt/${HOST_TAG}/sysroot"
NDK_PYTHON_BIN="${NDK_ROOT}/toolchains/llvm/prebuilt/${HOST_TAG}/python3/bin/python3.11"

if [[ ! -d "${TOOLCHAIN_BIN}" || ! -d "${SYSROOT}" ]]; then
    printf 'NDK llvm prebuilt host tag not found or incomplete: %s\n' "${HOST_TAG}" >&2
    printf 'Expected bin and sysroot under %s/toolchains/llvm/prebuilt/%s\n' "${NDK_ROOT}" "${HOST_TAG}" >&2
    exit 1
fi

python_has_qemu_build_deps() {
    local candidate="$1"
    [[ -x "${candidate}" ]] || return 1
    "${candidate}" - <<'PY' >/dev/null 2>&1
import sys
import venv
try:
    from importlib import metadata
except ImportError:
    import importlib_metadata as metadata

try:
    metadata.version("wheel")
except metadata.PackageNotFoundError:
    sys.exit(1)
PY
}

select_python_bin() {
    local candidate
    local fallback=""
    local candidates=(
        "/usr/bin/python3"
        "$(command -v python3 || true)"
        "${NDK_PYTHON_BIN}"
    )

    for candidate in "${candidates[@]}"; do
        [[ -n "${candidate}" && -x "${candidate}" ]] || continue
        if python_has_qemu_build_deps "${candidate}"; then
            PYTHON_BIN="${candidate}"
            return
        fi
        if [[ -z "${fallback}" ]]; then
            fallback="${candidate}"
        fi
    done

    PYTHON_BIN="${fallback}"
}

if [[ -z "${PYTHON_BIN}" ]]; then
    select_python_bin
fi

if [[ -n "${PYTHON_BIN}" && ! -x "${PYTHON_BIN}" ]]; then
    printf 'Configured Python is not executable: %s\n' "${PYTHON_BIN}" >&2
    exit 1
fi

if [[ -n "${PYTHON_BIN}" && "${ALLOW_DOWNLOADS}" != true ]] && ! python_has_qemu_build_deps "${PYTHON_BIN}"; then
    printf 'Configured Python is missing the venv/wheel support required by QEMU configure: %s\n' "${PYTHON_BIN}" >&2
    printf 'Use --python /path/to/python3 with wheel installed, or pass --allow-downloads.\n' >&2
    exit 1
fi

if [[ -z "${NINJA_BIN}" && -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}/cmake" ]]; then
    NINJA_BIN="$(find "${ANDROID_HOME}/cmake" -path '*/bin/ninja' -type f -perm +111 | sort | tail -n 1)"
fi

if [[ -n "${NINJA_BIN}" && ! -x "${NINJA_BIN}" ]]; then
    printf 'Configured ninja is not executable: %s\n' "${NINJA_BIN}" >&2
    exit 1
fi

if [[ "${BUILD_HOST}" == "armeabi-v7a" || "${BUILD_HOST}" == "x86" ]]; then
    printf 'Unsupported Android host ABI for QEMU 11: %s\n' "${BUILD_HOST}" >&2
    printf 'QEMU 11 dropped 32-bit host support. Use arm64-v8a for ARMv8/ARMv9 or x86_64.\n' >&2
    exit 2
fi

case "${BUILD_HOST}" in
    arm64-v8a)
        TARGET_TRIPLE="aarch64-linux-android"
        QEMU_CPU="aarch64"
        ARCH_CFLAGS="-march=armv8-a"
        if [[ "${USE_ARMV9}" == true ]]; then
            ARCH_CFLAGS="-march=armv9-a"
        fi
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

if [[ "${USE_ARMV9}" == true && "${BUILD_HOST}" != "arm64-v8a" ]]; then
    printf 'Warning: --armv9 only affects arm64-v8a host builds; current host is %s.\n' "${BUILD_HOST}" >&2
fi

HOST_PROFILE="${BUILD_HOST}"
if [[ "${BUILD_HOST}" == "arm64-v8a" ]]; then
    if [[ "${USE_ARMV9}" == true ]]; then
        HOST_PROFILE="armv9-a"
    else
        HOST_PROFILE="armv8-a"
    fi
fi
BUILD_DIR="${ROOT_DIR}/build/qemu-android/${BUILD_HOST}-${HOST_PROFILE}-${BUILD_GUEST}"
if [[ -z "${DEPS_PREFIX}" ]]; then
    DEPS_PREFIX="${JNI_DIR}/deps/${BUILD_HOST}-${HOST_PROFILE}"
fi
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
export PKG_CONFIG_LIBDIR="${PKG_CONFIG_LIBDIR:-${DEPS_PREFIX}/lib/pkgconfig:${DEPS_PREFIX}/share/pkgconfig:${JNI_DIR}/lib/pkgconfig}"
export CFLAGS="${CFLAGS:-}"
export CXXFLAGS="${CXXFLAGS:-}"
export LDFLAGS="${LDFLAGS:-}"

ANDROID_CFLAGS="--sysroot=${SYSROOT} ${ARCH_CFLAGS} -fPIC -DSDL_MAIN_HANDLED"
ANDROID_CXXFLAGS="--sysroot=${SYSROOT} ${ARCH_CFLAGS} -fPIC -DSDL_MAIN_HANDLED"
ANDROID_LDFLAGS="--sysroot=${SYSROOT} -llog -landroid"

if [[ -n "${NINJA_BIN}" ]]; then
    export PATH="$(dirname "${NINJA_BIN}"):${PATH}"
fi

CONFIGURE_ARGS=(
    "--target-list=${BUILD_GUEST}"
    "--python=${PYTHON_BIN:-python3}"
    "--cross-prefix=${TOOLCHAIN_BIN}/${TARGET_TRIPLE}${NDK_PLATFORM_API}-"
    "--cpu=${QEMU_CPU}"
    "--cc=${CC}"
    "--host-cc=cc"
    "--extra-cflags=${ANDROID_CFLAGS}"
    "--extra-cxxflags=${ANDROID_CXXFLAGS}"
    "--extra-ldflags=${ANDROID_LDFLAGS}"
    "--enable-system"
    "--disable-user"
    "--disable-tools"
    "--disable-guest-agent"
    "--disable-docs"
    "--disable-werror"
    "-Db_staticpic=true"
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
    "--disable-dbus-display"
    "--disable-plugins"
    "--disable-vhost-kernel"
    "--disable-vhost-net"
    "--disable-vhost-user"
    "--disable-vhost-user-blk-server"
    "--disable-vhost-crypto"
    "--disable-vhost-vdpa"
    "--disable-libvduse"
    "--disable-vduse-blk-export"
    "--disable-passt"
    "--disable-l2tpv3"
    "--disable-virtfs"
    "--disable-replication"
)

if [[ "${ALLOW_DOWNLOADS}" == true ]]; then
    CONFIGURE_ARGS+=("--enable-download")
else
    CONFIGURE_ARGS+=("--disable-download")
fi

if [[ "${USE_VIRGL}" == true ]]; then
    CONFIGURE_ARGS+=("--enable-opengl" "--enable-virglrenderer")
else
    CONFIGURE_ARGS+=("--disable-opengl" "--disable-virglrenderer")
fi

printf 'Configuring QEMU for Android\n'
printf '  host ABI: %s\n' "${BUILD_HOST}"
printf '  profile:  %s\n' "${HOST_PROFILE}"
printf '  guest:    %s\n' "${BUILD_GUEST}"
printf '  API:      %s\n' "${NDK_PLATFORM_API}"
printf '  host tag: %s\n' "${HOST_TAG}"
printf '  NDK:      %s\n' "${NDK_ROOT}"
printf '  Python:   %s\n' "${PYTHON_BIN:-python3}"
printf '  Ninja:    %s\n' "${NINJA_BIN:-ninja}"
printf '  deps:     %s\n' "${DEPS_PREFIX}"
printf '  download: %s\n' "${ALLOW_DOWNLOADS}"
printf '  build:    %s\n' "${BUILD_DIR}"

if [[ "${DRY_RUN}" == true ]]; then
    printf '\nEnvironment:\n'
    printf '  AR=%q\n' "${AR}"
    printf '  CC=%q\n' "${CC}"
    printf '  CXX=%q\n' "${CXX}"
    printf '  LD=%q\n' "${LD}"
    printf '  PYTHON=%q\n' "${PYTHON_BIN:-python3}"
    printf '  NINJA=%q\n' "${NINJA_BIN:-ninja}"
    printf '  PKG_CONFIG=%q\n' "${PKG_CONFIG}"
    printf '  PKG_CONFIG_LIBDIR=%q\n' "${PKG_CONFIG_LIBDIR}"
    printf '  CFLAGS=%q\n' "${CFLAGS}"
    printf '  CXXFLAGS=%q\n' "${CXXFLAGS}"
    printf '  LDFLAGS=%q\n' "${LDFLAGS}"
    printf '  ANDROID_CFLAGS=%q\n' "${ANDROID_CFLAGS}"
    printf '  ANDROID_CXXFLAGS=%q\n' "${ANDROID_CXXFLAGS}"
    printf '  ANDROID_LDFLAGS=%q\n' "${ANDROID_LDFLAGS}"
    printf '\nConfigure command:\n  %q' "${QEMU_DIR}/configure"
    printf ' %q' "${CONFIGURE_ARGS[@]}"
    printf '\n'
    exit 0
fi

if [[ ! -x "${QEMU_DIR}/configure" ]]; then
    printf 'QEMU source tree not found at %s.\n' "${QEMU_DIR}" >&2
    printf 'Run tools/fetch-qemu-11.sh first.\n' >&2
    exit 1
fi

apply_android_patches() {
    local patch_dir="${ROOT_DIR}/tools/qemu-11-android-patches"
    local patch_file

    if [[ ! -d "${patch_dir}" ]]; then
        printf 'Android patch directory not found: %s\n' "${patch_dir}" >&2
        exit 1
    fi

    shopt -s nullglob
    for patch_file in "${patch_dir}"/*.patch; do
        if patch --batch --silent --forward --dry-run -d "${QEMU_DIR}" -p1 < "${patch_file}" >/dev/null 2>&1; then
            printf 'Applying Android QEMU patch: %s\n' "$(basename "${patch_file}")"
            patch --batch --silent --forward -d "${QEMU_DIR}" -p1 < "${patch_file}"
        elif patch --batch --silent --reverse --dry-run -d "${QEMU_DIR}" -p1 < "${patch_file}" >/dev/null 2>&1; then
            printf 'Android QEMU patch already applied: %s\n' "$(basename "${patch_file}")"
        else
            printf 'Android QEMU patch cannot be applied cleanly: %s\n' "${patch_file}" >&2
            exit 1
        fi
    done
    shopt -u nullglob
}

sanitize_android_rpath() {
    local build_ninja="${BUILD_DIR}/build.ninja"
    local absolute_rpath="-Wl,-rpath,${DEPS_PREFIX}/lib"
    local relative_rpath='-Wl,-rpath,$$ORIGIN'

    if [[ ! -f "${build_ninja}" ]]; then
        return
    fi

    "${PYTHON_BIN:-python3}" -c '
from pathlib import Path
import sys

path = Path(sys.argv[1])
absolute = sys.argv[2]
relative = sys.argv[3]
text = path.read_text()
updated = text.replace(absolute, relative)
if updated != text:
    path.write_text(updated)
' "${build_ninja}" "${absolute_rpath}" "${relative_rpath}"
}

if [[ "${APPLY_ANDROID_PATCHES}" == true ]]; then
    apply_android_patches
fi

cd "${BUILD_DIR}"
"${QEMU_DIR}/configure" "${CONFIGURE_ARGS[@]}"
sanitize_android_rpath

if [[ "${CONFIGURE_ONLY}" != true ]]; then
    if [[ -z "${BUILD_TARGETS}" ]]; then
        IFS=',' read -r -a GUEST_TARGETS <<< "${BUILD_GUEST}"
        for target in "${GUEST_TARGETS[@]}"; do
            if [[ "${target}" == *-softmmu ]]; then
                BUILD_TARGETS+=" libqemu-system-${target%-softmmu}.so"
            fi
        done
    fi
    # shellcheck disable=SC2086
    make "-j${BUILD_THREADS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || printf 4)}" ${BUILD_TARGETS}
fi
