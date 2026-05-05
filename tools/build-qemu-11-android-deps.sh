#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JNI_DIR="${ROOT_DIR}/limbo-android-lib/src/main/jni"

BUILD_HOST="${BUILD_HOST:-arm64-v8a}"
NDK_PLATFORM_API="${NDK_PLATFORM_API:-23}"
NDK_ROOT="${NDK_ROOT:-${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"
HOST_TAG="${HOST_TAG:-}"
PYTHON_BIN="${PYTHON_BIN:-${PYTHON:-}}"
MESON_BIN="${MESON_BIN:-}"
CMAKE_BIN="${CMAKE_BIN:-}"
NINJA_BIN="${NINJA_BIN:-}"
PKG_CONFIG_BIN="${PKG_CONFIG_BIN:-${PKG_CONFIG:-pkg-config}}"
DEPS_PREFIX="${QEMU_DEPS_PREFIX:-}"
USE_ARMV9="${USE_ARMV9:-false}"
WITH_VIRGL="${WITH_VIRGL:-false}"
DRY_RUN="${DRY_RUN:-false}"

LIBICONV_VERSION="1.18"
LIBFFI_VERSION="3.5.2"
PCRE2_VERSION="10.47"
GLIB_VERSION="2.88.1"
PIXMAN_VERSION="0.46.4"
SDL2_VERSION="2.32.10"
LIBEPOXY_VERSION="1.5.10"
VIRGLRENDERER_VERSION="1.1.1"

usage() {
    cat <<EOF
Usage: $0 [options]

Build Android target dependencies required by QEMU 11 into:
  ${JNI_DIR}/deps/<host>-<profile>

Options:
  --host ABI           Android host ABI: arm64-v8a or x86_64.
                       Default: ${BUILD_HOST}
  --api LEVEL          Android API level. Default: ${NDK_PLATFORM_API}
  --ndk PATH           Android NDK root. Also reads ANDROID_NDK_HOME,
                       ANDROID_NDK_ROOT, or the newest ANDROID_HOME/ndk entry.
  --host-tag TAG       NDK prebuilt host tag. Auto-detected by default.
  --python PATH        Python with Meson available. Default: /usr/bin/python3,
                       then the NDK Python, then PATH python3.
  --meson PATH         Meson executable. If omitted, uses python -m mesonbuild.
  --cmake PATH         CMake executable. Auto-detects Android SDK CMake.
  --ninja PATH         Ninja executable. Auto-detects Android SDK CMake.
  --pkg-config PATH    pkg-config/pkgconf executable. Default: pkg-config.
  --deps-prefix PATH   Install prefix. Default: jni/deps/<host>-<profile>.
  --armv9              Use -march=armv9-a for arm64-v8a host builds.
  --virgl              Also build libepoxy and virglrenderer for OpenGL/virgl.
  --dry-run            Print commands without executing them.
  -h, --help           Show this help.

Examples:
  $0 --host arm64-v8a
  $0 --host arm64-v8a --armv9 --virgl
EOF
}

while (($#)); do
    case "$1" in
        --host)
            shift
            BUILD_HOST="${1:?Missing value for --host}"
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
        --meson)
            shift
            MESON_BIN="${1:?Missing value for --meson}"
            ;;
        --cmake)
            shift
            CMAKE_BIN="${1:?Missing value for --cmake}"
            ;;
        --ninja)
            shift
            NINJA_BIN="${1:?Missing value for --ninja}"
            ;;
        --pkg-config)
            shift
            PKG_CONFIG_BIN="${1:?Missing value for --pkg-config}"
            ;;
        --deps-prefix)
            shift
            DEPS_PREFIX="${1:?Missing value for --deps-prefix}"
            ;;
        --armv9)
            USE_ARMV9=true
            ;;
        --virgl)
            WITH_VIRGL=true
            ;;
        --dry-run)
            DRY_RUN=true
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

run() {
    printf '+' >&2
    printf ' %q' "$@" >&2
    printf '\n' >&2
    if [[ "${DRY_RUN}" != true ]]; then
        "$@"
    fi
}

find_android_sdk_tool() {
    local tool="$1"
    if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}/cmake" ]]; then
        find "${ANDROID_HOME}/cmake" -path "*/bin/${tool}" -type f -perm +111 | sort | tail -n 1
    fi
}

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
    exit 1
fi

TOOLCHAIN="${NDK_ROOT}/toolchains/llvm/prebuilt/${HOST_TAG}"
TOOLCHAIN_BIN="${TOOLCHAIN}/bin"
SYSROOT="${TOOLCHAIN}/sysroot"
NDK_PYTHON_BIN="${TOOLCHAIN}/python3/bin/python3.11"

if [[ ! -d "${TOOLCHAIN_BIN}" || ! -d "${SYSROOT}" ]]; then
    printf 'NDK llvm prebuilt host tag not found or incomplete: %s\n' "${HOST_TAG}" >&2
    exit 1
fi

if [[ -z "${PYTHON_BIN}" && -x "/usr/bin/python3" ]]; then
    PYTHON_BIN="/usr/bin/python3"
elif [[ -z "${PYTHON_BIN}" && -x "${NDK_PYTHON_BIN}" ]]; then
    PYTHON_BIN="${NDK_PYTHON_BIN}"
elif [[ -z "${PYTHON_BIN}" ]]; then
    PYTHON_BIN="$(command -v python3 || true)"
fi

if [[ -z "${PYTHON_BIN}" || ! -x "${PYTHON_BIN}" ]]; then
    printf 'Python not found. Use --python to select one.\n' >&2
    exit 1
fi

if [[ -z "${MESON_BIN}" ]]; then
    if "${PYTHON_BIN}" -c 'import mesonbuild' >/dev/null 2>&1; then
        MESON_CMD=("${PYTHON_BIN}" "-m" "mesonbuild.mesonmain")
    elif command -v meson >/dev/null 2>&1; then
        MESON_CMD=("$(command -v meson)")
    elif [[ -n "${HOME:-}" ]]; then
        FOUND_MESON="$(find "${HOME}/Library/Python" -path '*/bin/meson' -type f -perm +111 2>/dev/null | sort | tail -n 1)"
        if [[ -n "${FOUND_MESON}" ]]; then
            MESON_CMD=("${FOUND_MESON}")
        else
            MESON_CMD=()
        fi
    else
        MESON_CMD=()
    fi
else
    MESON_CMD=("${MESON_BIN}")
fi

if [[ ${#MESON_CMD[@]} -eq 0 ]]; then
    printf 'Meson not found. Install it with "%s -m pip install --user meson" or pass --meson.\n' "${PYTHON_BIN}" >&2
    exit 1
fi

if [[ -z "${CMAKE_BIN}" ]]; then
    CMAKE_BIN="$(find_android_sdk_tool cmake || true)"
fi
if [[ -z "${CMAKE_BIN}" ]]; then
    CMAKE_BIN="$(command -v cmake || true)"
fi
if [[ -z "${CMAKE_BIN}" || ! -x "${CMAKE_BIN}" ]]; then
    printf 'CMake not found. Install Android SDK CMake or pass --cmake.\n' >&2
    exit 1
fi

if [[ -z "${NINJA_BIN}" ]]; then
    NINJA_BIN="$(find_android_sdk_tool ninja || true)"
fi
if [[ -z "${NINJA_BIN}" ]]; then
    NINJA_BIN="$(command -v ninja || true)"
fi
if [[ -z "${NINJA_BIN}" || ! -x "${NINJA_BIN}" ]]; then
    printf 'Ninja not found. Install Android SDK CMake/Ninja or pass --ninja.\n' >&2
    exit 1
fi

if ! command -v "${PKG_CONFIG_BIN}" >/dev/null 2>&1 && [[ ! -x "${PKG_CONFIG_BIN}" ]]; then
    printf 'pkg-config/pkgconf not found: %s\n' "${PKG_CONFIG_BIN}" >&2
    exit 1
fi
if command -v "${PKG_CONFIG_BIN}" >/dev/null 2>&1; then
    PKG_CONFIG_BIN="$(command -v "${PKG_CONFIG_BIN}")"
fi

if [[ "${BUILD_HOST}" == "armeabi-v7a" || "${BUILD_HOST}" == "x86" ]]; then
    printf 'Unsupported Android host ABI for QEMU 11 dependencies: %s\n' "${BUILD_HOST}" >&2
    printf 'QEMU 11 dropped 32-bit host support. Use arm64-v8a or x86_64.\n' >&2
    exit 2
fi

case "${BUILD_HOST}" in
    arm64-v8a)
        TARGET_TRIPLE="aarch64-linux-android"
        MESON_CPU_FAMILY="aarch64"
        MESON_CPU="aarch64"
        AUTOTOOLS_HOST="aarch64-linux-android"
        ARCH_CFLAGS="-march=armv8-a"
        HOST_PROFILE="armv8-a"
        if [[ "${USE_ARMV9}" == true ]]; then
            ARCH_CFLAGS="-march=armv9-a"
            HOST_PROFILE="armv9-a"
        fi
        ;;
    x86_64)
        TARGET_TRIPLE="x86_64-linux-android"
        MESON_CPU_FAMILY="x86_64"
        MESON_CPU="x86_64"
        AUTOTOOLS_HOST="x86_64-linux-android"
        ARCH_CFLAGS="-march=x86-64"
        HOST_PROFILE="x86_64"
        ;;
    *)
        printf 'Unsupported Android host ABI: %s\n' "${BUILD_HOST}" >&2
        exit 2
        ;;
esac

if [[ -z "${DEPS_PREFIX}" ]]; then
    DEPS_PREFIX="${JNI_DIR}/deps/${BUILD_HOST}-${HOST_PROFILE}"
fi

CC_BIN="${TOOLCHAIN_BIN}/${TARGET_TRIPLE}${NDK_PLATFORM_API}-clang"
CXX_BIN="${TOOLCHAIN_BIN}/${TARGET_TRIPLE}${NDK_PLATFORM_API}-clang++"
AR_BIN="${TOOLCHAIN_BIN}/llvm-ar"
NM_BIN="${TOOLCHAIN_BIN}/llvm-nm"
RANLIB_BIN="${TOOLCHAIN_BIN}/llvm-ranlib"
STRIP_BIN="${TOOLCHAIN_BIN}/llvm-strip"

if [[ ! -x "${CC_BIN}" || ! -x "${CXX_BIN}" ]]; then
    printf 'NDK clang not found for %s API %s under %s\n' "${TARGET_TRIPLE}" "${NDK_PLATFORM_API}" "${TOOLCHAIN_BIN}" >&2
    exit 1
fi

THREADS="${BUILD_THREADS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || printf 4)}"
BUILD_ROOT="${ROOT_DIR}/build/qemu-android-deps/${BUILD_HOST}-${HOST_PROFILE}"
SOURCE_CACHE="${ROOT_DIR}/build/qemu-android-deps/sources"
CROSS_FILE="${BUILD_ROOT}/android-cross.meson"

ANDROID_CFLAGS="--sysroot=${SYSROOT} -I${DEPS_PREFIX}/include ${ARCH_CFLAGS} -fPIC"
ANDROID_CXXFLAGS="--sysroot=${SYSROOT} -I${DEPS_PREFIX}/include ${ARCH_CFLAGS} -fPIC"
ANDROID_LDFLAGS="--sysroot=${SYSROOT} -L${DEPS_PREFIX}/lib -llog"

export PATH="$(dirname "${PYTHON_BIN}"):$(dirname "${NINJA_BIN}"):${TOOLCHAIN_BIN}:${PATH}"
export PKG_CONFIG="${PKG_CONFIG_BIN}"
export PKG_CONFIG_LIBDIR="${DEPS_PREFIX}/lib/pkgconfig:${DEPS_PREFIX}/share/pkgconfig"
export PKG_CONFIG_PATH="${PKG_CONFIG_LIBDIR}"

mkdir -p "${BUILD_ROOT}" "${SOURCE_CACHE}" "${DEPS_PREFIX}"

cat >"${CROSS_FILE}" <<EOF
[binaries]
c = '${CC_BIN}'
cpp = '${CXX_BIN}'
ar = '${AR_BIN}'
nm = '${NM_BIN}'
strip = '${STRIP_BIN}'
pkg-config = '${PKG_CONFIG_BIN}'

[host_machine]
system = 'android'
cpu_family = '${MESON_CPU_FAMILY}'
cpu = '${MESON_CPU}'
endian = 'little'

[properties]
needs_exe_wrapper = true

[built-in options]
c_args = ['--sysroot=${SYSROOT}', '-I${DEPS_PREFIX}/include', '${ARCH_CFLAGS}', '-fPIC']
cpp_args = ['--sysroot=${SYSROOT}', '-I${DEPS_PREFIX}/include', '${ARCH_CFLAGS}', '-fPIC']
c_link_args = ['--sysroot=${SYSROOT}', '-L${DEPS_PREFIX}/lib', '-llog']
cpp_link_args = ['--sysroot=${SYSROOT}', '-L${DEPS_PREFIX}/lib', '-llog']
EOF

download_source() {
    local label="$1"
    local url="$2"
    local archive="$3"

    if [[ -f "${archive}" ]]; then
        printf 'Using cached %s: %s\n' "${label}" "${archive}" >&2
        return
    fi

    run curl --location --fail --output "${archive}" "${url}"
}

extract_source() {
    local label="$1"
    local archive="$2"
    local dir_name="$3"
    local source_dir="${SOURCE_CACHE}/${dir_name}"
    local temp_dir="${SOURCE_CACHE}/.${dir_name}.extract"

    if [[ -d "${source_dir}" ]]; then
        printf 'Using extracted %s: %s\n' "${label}" "${source_dir}" >&2
        return
    fi

    if [[ "${DRY_RUN}" == true ]]; then
        printf '+ extract %q to %q\n' "${archive}" "${source_dir}" >&2
        return
    fi

    rm -rf "${temp_dir}"
    mkdir -p "${temp_dir}"
    tar -xf "${archive}" -C "${temp_dir}"
    local extracted_root
    extracted_root="$(find "${temp_dir}" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
    if [[ -z "${extracted_root}" ]]; then
        printf 'Archive did not contain a single source directory: %s\n' "${archive}" >&2
        exit 1
    fi
    mv "${extracted_root}" "${source_dir}"
    rm -rf "${temp_dir}"
}

source_dir() {
    local label="$1"
    local url="$2"
    local archive_name="$3"
    local dir_name="$4"
    local archive="${SOURCE_CACHE}/${archive_name}"

    download_source "${label}" "${url}" "${archive}"
    extract_source "${label}" "${archive}" "${dir_name}"
    printf '%s\n' "${SOURCE_CACHE}/${dir_name}"
}

apply_source_patches() {
    local source_dir="$1"
    local patch_dir="$2"

    if [[ ! -d "${patch_dir}" ]]; then
        return
    fi

    local patch_file
    for patch_file in "${patch_dir}"/*.patch; do
        [[ -e "${patch_file}" ]] || continue
        printf 'Applying source patch: %s\n' "${patch_file}" >&2
        if [[ "${DRY_RUN}" == true ]]; then
            printf '+ patch -d %q -p1 < %q\n' "${source_dir}" "${patch_file}" >&2
            continue
        fi
        if patch --batch --silent --forward --dry-run -d "${source_dir}" -p1 <"${patch_file}" >/dev/null 2>&1; then
            patch --batch --silent --forward -d "${source_dir}" -p1 <"${patch_file}"
        elif patch --batch --silent --reverse --dry-run -d "${source_dir}" -p1 <"${patch_file}" >/dev/null 2>&1; then
            printf '  already applied\n' >&2
        else
            printf 'Patch failed or conflicted: %s\n' "${patch_file}" >&2
            exit 1
        fi
    done
}

ensure_python_yaml() {
    if ! "${PYTHON_BIN}" -c 'import yaml' >/dev/null 2>&1; then
        printf 'PyYAML is required for virglrenderer code generation.\n' >&2
        printf 'Install it with: %s -m pip install --user PyYAML\n' "${PYTHON_BIN}" >&2
        exit 1
    fi
}

meson_setup() {
    local build_dir="$1"
    shift
    if [[ -d "${build_dir}" && ! -f "${build_dir}/build.ninja" ]]; then
        if [[ "${DRY_RUN}" == true ]]; then
            printf '+ rm -rf %q\n' "${build_dir}" >&2
        else
            rm -rf "${build_dir}"
        fi
    fi
    if [[ -f "${build_dir}/build.ninja" ]]; then
        run "${MESON_CMD[@]}" setup --wipe "${build_dir}" "$@"
    else
        run "${MESON_CMD[@]}" setup "${build_dir}" "$@"
    fi
}

meson_compile_install() {
    local build_dir="$1"
    run "${MESON_CMD[@]}" compile -C "${build_dir}" -j "${THREADS}"
    run "${MESON_CMD[@]}" install -C "${build_dir}"
}

cmake_build_install() {
    local build_dir="$1"
    run "${CMAKE_BIN}" --build "${build_dir}" --parallel "${THREADS}"
    run "${CMAKE_BIN}" --install "${build_dir}"
}

build_libiconv() {
    if [[ -f "${DEPS_PREFIX}/lib/libiconv.so" || -f "${DEPS_PREFIX}/lib/libiconv.a" ]]; then
        printf 'Skipping libiconv; already installed in %s\n' "${DEPS_PREFIX}" >&2
        return
    fi

    local src
    src="$(source_dir \
        "libiconv" \
        "https://ftp.gnu.org/pub/gnu/libiconv/libiconv-${LIBICONV_VERSION}.tar.gz" \
        "libiconv-${LIBICONV_VERSION}.tar.gz" \
        "libiconv-${LIBICONV_VERSION}")"
    local build_dir="${BUILD_ROOT}/libiconv"
    [[ "${DRY_RUN}" == true ]] || mkdir -p "${build_dir}"

    if [[ "${DRY_RUN}" == true ]]; then
        run env \
            "AR=${AR_BIN}" \
            "CC=${CC_BIN}" \
            "CXX=${CXX_BIN}" \
            "NM=${NM_BIN}" \
            "RANLIB=${RANLIB_BIN}" \
            "STRIP=${STRIP_BIN}" \
            "CFLAGS=${ANDROID_CFLAGS}" \
            "CXXFLAGS=${ANDROID_CXXFLAGS}" \
            "LDFLAGS=${ANDROID_LDFLAGS}" \
            "${src}/configure" \
            "--host=${AUTOTOOLS_HOST}" \
            "--prefix=${DEPS_PREFIX}" \
            "--enable-shared" \
            "--enable-static" \
            "--disable-nls"
    else
        (
            cd "${build_dir}"
            run env \
                "AR=${AR_BIN}" \
                "CC=${CC_BIN}" \
                "CXX=${CXX_BIN}" \
                "NM=${NM_BIN}" \
                "RANLIB=${RANLIB_BIN}" \
                "STRIP=${STRIP_BIN}" \
                "CFLAGS=${ANDROID_CFLAGS}" \
                "CXXFLAGS=${ANDROID_CXXFLAGS}" \
                "LDFLAGS=${ANDROID_LDFLAGS}" \
                "${src}/configure" \
                "--host=${AUTOTOOLS_HOST}" \
                "--prefix=${DEPS_PREFIX}" \
                "--enable-shared" \
                "--enable-static" \
                "--disable-nls"
        )
    fi
    run make -C "${build_dir}" "-j${THREADS}"
    run make -C "${build_dir}" install
}

build_libffi() {
    if [[ -f "${DEPS_PREFIX}/lib/pkgconfig/libffi.pc" ]]; then
        printf 'Skipping libffi; already installed in %s\n' "${DEPS_PREFIX}" >&2
        return
    fi

    local src
    src="$(source_dir \
        "libffi" \
        "https://github.com/libffi/libffi/releases/download/v${LIBFFI_VERSION}/libffi-${LIBFFI_VERSION}.tar.gz" \
        "libffi-${LIBFFI_VERSION}.tar.gz" \
        "libffi-${LIBFFI_VERSION}")"
    local build_dir="${BUILD_ROOT}/libffi"
    [[ "${DRY_RUN}" == true ]] || mkdir -p "${build_dir}"

    if [[ "${DRY_RUN}" == true ]]; then
        run env \
            "AR=${AR_BIN}" \
            "CC=${CC_BIN}" \
            "CXX=${CXX_BIN}" \
            "NM=${NM_BIN}" \
            "RANLIB=${RANLIB_BIN}" \
            "STRIP=${STRIP_BIN}" \
            "CFLAGS=${ANDROID_CFLAGS}" \
            "CXXFLAGS=${ANDROID_CXXFLAGS}" \
            "LDFLAGS=${ANDROID_LDFLAGS}" \
            "${src}/configure" \
            "--host=${AUTOTOOLS_HOST}" \
            "--prefix=${DEPS_PREFIX}" \
            "--enable-shared" \
            "--enable-static" \
            "--disable-docs"
    else
        (
            cd "${build_dir}"
            run env \
                "AR=${AR_BIN}" \
                "CC=${CC_BIN}" \
                "CXX=${CXX_BIN}" \
                "NM=${NM_BIN}" \
                "RANLIB=${RANLIB_BIN}" \
                "STRIP=${STRIP_BIN}" \
                "CFLAGS=${ANDROID_CFLAGS}" \
                "CXXFLAGS=${ANDROID_CXXFLAGS}" \
                "LDFLAGS=${ANDROID_LDFLAGS}" \
                "${src}/configure" \
                "--host=${AUTOTOOLS_HOST}" \
                "--prefix=${DEPS_PREFIX}" \
                "--enable-shared" \
                "--enable-static" \
                "--disable-docs"
        )
    fi
    run make -C "${build_dir}" "-j${THREADS}"
    run make -C "${build_dir}" install
}

build_pcre2() {
    if [[ -f "${DEPS_PREFIX}/lib/pkgconfig/libpcre2-8.pc" ]]; then
        printf 'Skipping PCRE2; already installed in %s\n' "${DEPS_PREFIX}" >&2
        return
    fi

    local src
    src="$(source_dir \
        "PCRE2" \
        "https://github.com/PCRE2Project/pcre2/releases/download/pcre2-${PCRE2_VERSION}/pcre2-${PCRE2_VERSION}.tar.gz" \
        "pcre2-${PCRE2_VERSION}.tar.gz" \
        "pcre2-${PCRE2_VERSION}")"
    local build_dir="${BUILD_ROOT}/pcre2"

    run "${CMAKE_BIN}" \
        -S "${src}" \
        -B "${build_dir}" \
        -G Ninja \
        "-DCMAKE_MAKE_PROGRAM=${NINJA_BIN}" \
        "-DCMAKE_TOOLCHAIN_FILE=${NDK_ROOT}/build/cmake/android.toolchain.cmake" \
        "-DANDROID_ABI=${BUILD_HOST}" \
        "-DANDROID_PLATFORM=android-${NDK_PLATFORM_API}" \
        "-DCMAKE_INSTALL_PREFIX=${DEPS_PREFIX}" \
        -DCMAKE_BUILD_TYPE=Release \
        "-DCMAKE_C_FLAGS=${ARCH_CFLAGS} -fPIC" \
        -DPCRE2_BUILD_TESTS=OFF \
        -DPCRE2_BUILD_PCRE2_8=ON \
        -DPCRE2_BUILD_PCRE2_16=OFF \
        -DPCRE2_BUILD_PCRE2_32=OFF \
        -DPCRE2_BUILD_PCRE2GREP=OFF \
        -DPCRE2_SUPPORT_LIBZ=OFF \
        -DPCRE2_SUPPORT_LIBBZ2=OFF \
        -DPCRE2_SUPPORT_LIBREADLINE=OFF \
        -DPCRE2_SYMVERS=OFF \
        -DPCRE2_STATIC_PIC=ON \
        -DBUILD_SHARED_LIBS=ON
    cmake_build_install "${build_dir}"
}

build_glib() {
    if [[ -f "${DEPS_PREFIX}/lib/pkgconfig/glib-2.0.pc" ]]; then
        printf 'Skipping GLib; already installed in %s\n' "${DEPS_PREFIX}" >&2
        return
    fi

    local src
    src="$(source_dir \
        "GLib" \
        "https://download.gnome.org/sources/glib/${GLIB_VERSION%.*}/glib-${GLIB_VERSION}.tar.xz" \
        "glib-${GLIB_VERSION}.tar.xz" \
        "glib-${GLIB_VERSION}")"
    local build_dir="${BUILD_ROOT}/glib"

    meson_setup "${build_dir}" "${src}" \
        "--cross-file=${CROSS_FILE}" \
        "--prefix=${DEPS_PREFIX}" \
        --libdir=lib \
        --buildtype=release \
        --wrap-mode=default \
        -Dtests=false \
        -Dinstalled_tests=false \
        -Dselinux=disabled \
        -Dlibmount=disabled \
        -Dxattr=false \
        -Dman-pages=disabled \
        -Ddocumentation=false \
        -Dgtk_doc=false \
        -Dnls=disabled \
        -Dsysprof=disabled \
        -Dintrospection=disabled \
        -Dglib_debug=disabled
    meson_compile_install "${build_dir}"
}

build_pixman() {
    if [[ -f "${DEPS_PREFIX}/lib/pkgconfig/pixman-1.pc" ]]; then
        printf 'Skipping pixman; already installed in %s\n' "${DEPS_PREFIX}" >&2
        return
    fi

    local src
    src="$(source_dir \
        "pixman" \
        "https://www.cairographics.org/releases/pixman-${PIXMAN_VERSION}.tar.gz" \
        "pixman-${PIXMAN_VERSION}.tar.gz" \
        "pixman-${PIXMAN_VERSION}")"
    local build_dir="${BUILD_ROOT}/pixman"

    meson_setup "${build_dir}" "${src}" \
        "--cross-file=${CROSS_FILE}" \
        "--prefix=${DEPS_PREFIX}" \
        --libdir=lib \
        --buildtype=release \
        --wrap-mode=nodownload \
        -Dtests=disabled \
        -Ddemos=disabled \
        -Dgtk=disabled \
        -Dlibpng=disabled \
        -Dopenmp=disabled
    meson_compile_install "${build_dir}"
}

build_sdl2() {
    if [[ -f "${DEPS_PREFIX}/lib/pkgconfig/sdl2.pc" ]]; then
        printf 'Skipping SDL2; already installed in %s\n' "${DEPS_PREFIX}" >&2
        return
    fi

    local src
    src="$(source_dir \
        "SDL2" \
        "https://www.libsdl.org/release/SDL2-${SDL2_VERSION}.tar.gz" \
        "SDL2-${SDL2_VERSION}.tar.gz" \
        "SDL2-${SDL2_VERSION}")"
    apply_source_patches "${src}" "${ROOT_DIR}/tools/sdl2-android-patches"
    local build_dir="${BUILD_ROOT}/SDL2"

    run "${CMAKE_BIN}" \
        -S "${src}" \
        -B "${build_dir}" \
        -G Ninja \
        "-DCMAKE_MAKE_PROGRAM=${NINJA_BIN}" \
        "-DCMAKE_TOOLCHAIN_FILE=${NDK_ROOT}/build/cmake/android.toolchain.cmake" \
        "-DANDROID_ABI=${BUILD_HOST}" \
        "-DANDROID_PLATFORM=android-${NDK_PLATFORM_API}" \
        "-DCMAKE_INSTALL_PREFIX=${DEPS_PREFIX}" \
        -DCMAKE_BUILD_TYPE=Release \
        "-DCMAKE_C_FLAGS=${ARCH_CFLAGS} -fPIC -D__LIMBO__ -D__ENABLE_AAUDIO__" \
        "-DCMAKE_CXX_FLAGS=${ARCH_CFLAGS} -fPIC -D__LIMBO__ -D__ENABLE_AAUDIO__" \
        -DSDL_SHARED=ON \
        -DSDL_STATIC=ON \
        -DSDL_TESTS=OFF \
        -DSDL_TEST_LIBRARY=OFF \
        -DSDL_INSTALL_TESTS=OFF
    cmake_build_install "${build_dir}"
}

build_libepoxy() {
    if [[ -f "${DEPS_PREFIX}/lib/pkgconfig/epoxy.pc" ]]; then
        printf 'Skipping libepoxy; already installed in %s\n' "${DEPS_PREFIX}" >&2
        return
    fi

    local src
    src="$(source_dir \
        "libepoxy" \
        "https://download.gnome.org/sources/libepoxy/${LIBEPOXY_VERSION%.*}/libepoxy-${LIBEPOXY_VERSION}.tar.xz" \
        "libepoxy-${LIBEPOXY_VERSION}.tar.xz" \
        "libepoxy-${LIBEPOXY_VERSION}")"
    local build_dir="${BUILD_ROOT}/libepoxy"

    meson_setup "${build_dir}" "${src}" \
        "--cross-file=${CROSS_FILE}" \
        "--prefix=${DEPS_PREFIX}" \
        --libdir=lib \
        --buildtype=release \
        --wrap-mode=nodownload \
        -Ddocs=false \
        -Dtests=false \
        -Dglx=no \
        -Degl=yes \
        -Dx11=false
    meson_compile_install "${build_dir}"
}

build_virglrenderer() {
    if [[ -f "${DEPS_PREFIX}/lib/pkgconfig/virglrenderer.pc" ]]; then
        printf 'Skipping virglrenderer; already installed in %s\n' "${DEPS_PREFIX}" >&2
        return
    fi

    local src
    src="$(source_dir \
        "virglrenderer" \
        "https://gitlab.freedesktop.org/virgl/virglrenderer/-/archive/virglrenderer-${VIRGLRENDERER_VERSION}/virglrenderer-virglrenderer-${VIRGLRENDERER_VERSION}.tar.gz" \
        "virglrenderer-${VIRGLRENDERER_VERSION}.tar.gz" \
        "virglrenderer-virglrenderer-${VIRGLRENDERER_VERSION}")"
    apply_source_patches "${src}" "${ROOT_DIR}/tools/virglrenderer-android-patches"
    local build_dir="${BUILD_ROOT}/virglrenderer"

    meson_setup "${build_dir}" "${src}" \
        "--cross-file=${CROSS_FILE}" \
        "--prefix=${DEPS_PREFIX}" \
        --libdir=lib \
        --buildtype=release \
        --wrap-mode=nodownload \
        -Dplatforms=auto \
        -Dminigbm_allocation=false \
        -Dvenus=false \
        -Dvideo=false \
        -Dtests=false \
        -Dfuzzer=false \
        -Dvalgrind=false \
        -Dtracing=none
    meson_compile_install "${build_dir}"
}

printf 'Building QEMU 11 Android dependencies\n'
printf '  host ABI: %s\n' "${BUILD_HOST}"
printf '  profile:  %s\n' "${HOST_PROFILE}"
printf '  API:      %s\n' "${NDK_PLATFORM_API}"
printf '  host tag: %s\n' "${HOST_TAG}"
printf '  NDK:      %s\n' "${NDK_ROOT}"
printf '  Python:   %s\n' "${PYTHON_BIN}"
printf '  Meson:    %s\n' "${MESON_CMD[*]}"
printf '  CMake:    %s\n' "${CMAKE_BIN}"
printf '  Ninja:    %s\n' "${NINJA_BIN}"
printf '  pkgconf:  %s\n' "${PKG_CONFIG_BIN}"
printf '  prefix:   %s\n' "${DEPS_PREFIX}"
printf '  build:    %s\n' "${BUILD_ROOT}"
printf '  virgl:    %s\n' "${WITH_VIRGL}"

build_libiconv
build_libffi
build_pcre2
build_glib
build_pixman
build_sdl2

if [[ "${WITH_VIRGL}" == true ]]; then
    ensure_python_yaml
    build_libepoxy
    build_virglrenderer
fi

printf 'QEMU 11 Android dependencies installed at %s\n' "${DEPS_PREFIX}"
