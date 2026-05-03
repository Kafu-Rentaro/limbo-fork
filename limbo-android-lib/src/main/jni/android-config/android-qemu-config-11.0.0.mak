#### QEMU 11.0.0 version-specific options

# QEMU 11 is the modernization target. The Limbo Android integration still needs
# the shared-library and SDL patches to be ported before this can replace 5.1.0
# as the default build.

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
