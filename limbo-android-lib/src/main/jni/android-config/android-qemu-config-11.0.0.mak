#### QEMU 11.0.0 version-specific options

# QEMU 11 is the modernization target. The Limbo Android integration still needs
# the shared-library and SDL patches to be ported before this can replace 5.1.0
# as the default build.

ifneq ($(filter $(BUILD_HOST),arm64-v8a x86_64),$(BUILD_HOST))
$(error QEMU 11 dropped 32-bit host support. Use BUILD_HOST=arm64-v8a for ARMv8/ARMv9 or BUILD_HOST=x86_64)
endif

USE_QEMUSTAB ?= false
USE_SLIRP_LIB ?= true
USE_SDL_ABI ?= false

MISC += --disable-capstone
MISC += --disable-malloc-trim
MISC += --disable-plugins
MISC += --disable-download
MISC += --disable-pa
MISC += --disable-pipewire
MISC += --disable-jack
MISC += --disable-oss
