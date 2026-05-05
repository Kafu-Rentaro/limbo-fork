cmake_minimum_required(VERSION 3.22.1)

project(limbo_native LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "QEMU 11 native bundle is currently built for arm64-v8a only.")
endif()

set(LIMBO_JNI_DIR "${CMAKE_CURRENT_LIST_DIR}")
set(QEMU11_DEPS_DIR "${LIMBO_JNI_DIR}/deps/arm64-v8a-armv8-a" CACHE PATH "QEMU 11 Android dependency prefix")

add_library(SDL2 SHARED IMPORTED GLOBAL)
set_target_properties(SDL2 PROPERTIES
    IMPORTED_LOCATION "${QEMU11_DEPS_DIR}/lib/libSDL2.so"
    INTERFACE_INCLUDE_DIRECTORIES "${QEMU11_DEPS_DIR}/include/SDL2"
)

add_library(compat-musl SHARED
    compat/musl/musl-locale/iconv.c
    compat/musl/musl-locale/intl.c
)
target_include_directories(compat-musl PRIVATE
    compat/musl/include
    compat/musl/musl-locale
)
target_link_options(compat-musl PRIVATE "-Wl,-u,iconv_open")

add_library(compat-limbo SHARED
    compat/limbo_compat_filesystem.c
    compat/limbo_compat.c
    compat/limbo_compat_qemu.c
    compat/limbo_compat_signals.cpp
)
target_include_directories(compat-limbo PRIVATE
    compat
    compat/musl/include
)
target_link_libraries(compat-limbo PRIVATE log)

add_library(compat-SDL2-addons SHARED
    compat/sdl-addons/SDL_limboaudio.c
    compat/sdl-addons/SDL_limboaudio_bridge.c
)
target_include_directories(compat-SDL2-addons PRIVATE
    compat/sdl-addons
)
target_link_libraries(compat-SDL2-addons PRIVATE
    dl
    log
    m
)

add_library(compat-SDL2-ext SHARED
    compat/sdl-extensions/SDL_limbomouse.c
    compat/sdl-extensions/SDL_limboscreen.c
)
target_include_directories(compat-SDL2-ext PRIVATE
    compat/sdl-extensions
)
target_link_libraries(compat-SDL2-ext PRIVATE SDL2 log)

add_library(limbo SHARED
    limbo/vm-executor-jni.c
)
target_include_directories(limbo PRIVATE
    compat
    limbo
    qemu
    qemu/include
)
target_link_libraries(limbo PRIVATE
    compat-limbo
    compat-musl
    dl
    log
)
