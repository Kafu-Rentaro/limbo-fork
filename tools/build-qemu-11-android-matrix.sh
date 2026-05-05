#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIGURE_SCRIPT="${ROOT_DIR}/tools/configure-qemu-11-android.sh"

BUILD_HOSTS=("arm64-v8a")
BUILD_PROFILES=("armv8" "armv9")
BUILD_GUESTS=("i386-softmmu" "x86_64-softmmu")
NDK_PLATFORM_API="${NDK_PLATFORM_API:-23}"
NDK_ROOT="${NDK_ROOT:-${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"
PYTHON_BIN="${PYTHON_BIN:-${PYTHON:-}}"
NINJA_BIN="${NINJA_BIN:-}"
DEPS_PREFIX="${QEMU_DEPS_PREFIX:-}"
USE_VIRGL=false
ALLOW_DOWNLOADS=false
DRY_RUN=false
RUN_BUILD=false

usage() {
    cat <<EOF
Usage: $0 [options]

Build or dry-run QEMU 11 Android host/guest variants.

Options:
  --host ABI           Host ABI. Repeatable. Supported: arm64-v8a, x86_64.
                       Use "all" for both. Default: arm64-v8a.
  --profile PROFILE    ARM64 profile. Repeatable: armv8, armv9, all.
                       Default: armv8 and armv9 for arm64-v8a.
  --guest TARGET       QEMU softmmu target. Repeatable.
                       Default: i386-softmmu and x86_64-softmmu.
  --api LEVEL          Android API level. Default: ${NDK_PLATFORM_API}
  --ndk PATH           Android NDK root.
  --python PATH        Python for QEMU configure.
  --ninja PATH         Ninja executable.
  --deps-prefix PATH   Prefix containing Android target dependencies.
  --virgl              Enable OpenGL and virglrenderer configure flags.
  --allow-downloads    Let QEMU configure download missing Python/build deps.
  --dry-run            Print configure commands without running configure.
  --build              Run make after configure.
  -h, --help           Show this help.

Examples:
  $0 --dry-run
  $0 --host arm64-v8a --profile all --guest x86_64-softmmu --virgl
  $0 --host all --guest i386-softmmu --guest x86_64-softmmu --build
EOF
}

reset_array() {
    local name="$1"
    eval "${name}=()"
}

append_host() {
    case "$1" in
        all)
            BUILD_HOSTS=("arm64-v8a" "x86_64")
            ;;
        arm64-v8a|x86_64)
            BUILD_HOSTS+=("$1")
            ;;
        *)
            printf 'Unsupported host ABI: %s\n' "$1" >&2
            exit 2
            ;;
    esac
}

append_profile() {
    case "$1" in
        all)
            BUILD_PROFILES=("armv8" "armv9")
            ;;
        armv8|armv9)
            BUILD_PROFILES+=("$1")
            ;;
        *)
            printf 'Unsupported ARM profile: %s\n' "$1" >&2
            exit 2
            ;;
    esac
}

while (($#)); do
    case "$1" in
        --host)
            shift
            [[ ${HOSTS_RESET:-false} == true ]] || { reset_array BUILD_HOSTS; HOSTS_RESET=true; }
            append_host "${1:?Missing value for --host}"
            ;;
        --profile)
            shift
            [[ ${PROFILES_RESET:-false} == true ]] || { reset_array BUILD_PROFILES; PROFILES_RESET=true; }
            append_profile "${1:?Missing value for --profile}"
            ;;
        --guest)
            shift
            [[ ${GUESTS_RESET:-false} == true ]] || { reset_array BUILD_GUESTS; GUESTS_RESET=true; }
            BUILD_GUESTS+=("${1:?Missing value for --guest}")
            ;;
        --api)
            shift
            NDK_PLATFORM_API="${1:?Missing value for --api}"
            ;;
        --ndk)
            shift
            NDK_ROOT="${1:?Missing value for --ndk}"
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
        --virgl)
            USE_VIRGL=true
            ;;
        --allow-downloads)
            ALLOW_DOWNLOADS=true
            ;;
        --dry-run)
            DRY_RUN=true
            ;;
        --build)
            RUN_BUILD=true
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

if [[ ! -x "${CONFIGURE_SCRIPT}" ]]; then
    printf 'Missing executable configure helper: %s\n' "${CONFIGURE_SCRIPT}" >&2
    exit 1
fi

for host in "${BUILD_HOSTS[@]}"; do
    profiles=("${BUILD_PROFILES[@]}")
    if [[ "${host}" != "arm64-v8a" ]]; then
        profiles=("baseline")
    fi
    for profile in "${profiles[@]}"; do
        for guest in "${BUILD_GUESTS[@]}"; do
            args=(
                "--host" "${host}"
                "--guest" "${guest}"
                "--api" "${NDK_PLATFORM_API}"
            )
            if [[ -n "${NDK_ROOT}" ]]; then
                args+=("--ndk" "${NDK_ROOT}")
            fi
            if [[ -n "${PYTHON_BIN}" ]]; then
                args+=("--python" "${PYTHON_BIN}")
            fi
            if [[ -n "${NINJA_BIN}" ]]; then
                args+=("--ninja" "${NINJA_BIN}")
            fi
            if [[ -n "${DEPS_PREFIX}" ]]; then
                args+=("--deps-prefix" "${DEPS_PREFIX}")
            fi
            if [[ "${host}" == "arm64-v8a" && "${profile}" == "armv9" ]]; then
                args+=("--armv9")
            fi
            if [[ "${USE_VIRGL}" == true ]]; then
                args+=("--virgl")
            fi
            if [[ "${ALLOW_DOWNLOADS}" == true ]]; then
                args+=("--allow-downloads")
            fi
            if [[ "${DRY_RUN}" == true ]]; then
                args+=("--dry-run")
            fi
            if [[ "${RUN_BUILD}" == true ]]; then
                args+=("--build")
            fi

            printf '\n==> QEMU 11 %s %s %s\n' "${host}" "${profile}" "${guest}"
            "${CONFIGURE_SCRIPT}" "${args[@]}"
        done
    done
done
