#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JNI_DIR="${ROOT_DIR}/limbo-android-lib/src/main/jni"
QEMU_VERSION="${QEMU_VERSION:-11.0.0}"
FORCE=false
VERIFY=true

usage() {
    cat <<EOF
Usage: $0 [--force] [--skip-verify] [--version VERSION]

Fetch QEMU into:
  ${JNI_DIR}/qemu

Options:
  --force          Replace an existing jni/qemu directory.
  --skip-verify   Skip GPG signature verification.
  --version       Fetch a specific QEMU version. Default: ${QEMU_VERSION}
EOF
}

while (($#)); do
    case "$1" in
        --force)
            FORCE=true
            ;;
        --skip-verify)
            VERIFY=false
            ;;
        --version)
            shift
            if [[ $# -eq 0 ]]; then
                printf 'Missing value for --version\n' >&2
                exit 2
            fi
            QEMU_VERSION="$1"
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

QEMU_ARCHIVE="qemu-${QEMU_VERSION}.tar.xz"
QEMU_SIGNATURE="${QEMU_ARCHIVE}.sig"
QEMU_URL="https://download.qemu.org/${QEMU_ARCHIVE}"
QEMU_SIGNATURE_URL="https://download.qemu.org/${QEMU_SIGNATURE}"
QEMU_RELEASE_KEY_FINGERPRINT="CEACC9E15534EBABB82D3FA03353C9CEF108B584"
QEMU_RELEASE_KEY_URL="https://keys.openpgp.org/vks/v1/by-fingerprint/${QEMU_RELEASE_KEY_FINGERPRINT}"
TMP_DIR="${TMPDIR:-/tmp}/limbo-qemu-${QEMU_VERSION}"
TARGET_DIR="${JNI_DIR}/qemu"

if [[ -e "${TARGET_DIR}" && "${FORCE}" != true ]]; then
    printf '%s already exists. Re-run with --force to replace it.\n' "${TARGET_DIR}" >&2
    exit 1
fi

mkdir -p "${TMP_DIR}"
curl --fail --location --retry 3 "${QEMU_URL}" --output "${TMP_DIR}/${QEMU_ARCHIVE}"

if [[ "${VERIFY}" == true ]]; then
    if ! command -v gpg >/dev/null 2>&1; then
        printf 'gpg is required for signature verification. Install it or use --skip-verify.\n' >&2
        exit 1
    fi
    curl --fail --location --retry 3 "${QEMU_SIGNATURE_URL}" --output "${TMP_DIR}/${QEMU_SIGNATURE}"
    GNUPGHOME="${TMP_DIR}/gnupg"
    export GNUPGHOME
    rm -rf "${GNUPGHOME}"
    mkdir -p "${GNUPGHOME}"
    chmod 700 "${GNUPGHOME}"
    curl --fail --location --retry 3 "${QEMU_RELEASE_KEY_URL}" --output "${TMP_DIR}/qemu-release-key.asc"
    gpg --batch --import "${TMP_DIR}/qemu-release-key.asc"
    gpg --batch --verify "${TMP_DIR}/${QEMU_SIGNATURE}" "${TMP_DIR}/${QEMU_ARCHIVE}"
fi

rm -rf "${TMP_DIR}/qemu-${QEMU_VERSION}"
tar -C "${TMP_DIR}" -xJf "${TMP_DIR}/${QEMU_ARCHIVE}"

rm -rf "${TARGET_DIR}"
mv "${TMP_DIR}/qemu-${QEMU_VERSION}" "${TARGET_DIR}"

printf 'QEMU %s installed at %s\n' "${QEMU_VERSION}" "${TARGET_DIR}"
printf 'Next: port and apply the Limbo Android QEMU patch for this version.\n'
