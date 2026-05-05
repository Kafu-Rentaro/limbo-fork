/*
Copyright (C) Max Kastanas 2012

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
#include <stdbool.h>
#include <SDL.h>
#include "SDL_limboscreen.h"

JNIEXPORT void JNICALL Java_com_max2idea_android_limbo_jni_VMExecutor_nativeFullscreen(
        JNIEnv* env, jobject thiz) {
    SDL_Window *window = SDL_GetKeyboardFocus();
    if (!window) {
        window = SDL_GetMouseFocus();
    }
    if (window) {
        SDL_SetWindowFullscreen(window, SDL_WINDOW_FULLSCREEN_DESKTOP);
    }
}
