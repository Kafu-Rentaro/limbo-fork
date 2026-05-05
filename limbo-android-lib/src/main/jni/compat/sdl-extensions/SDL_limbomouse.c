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
#include "SDL_limbomouse.h"

#define ACTION_DOWN 0
#define ACTION_UP 1
#define ACTION_MOVE 2
#define ACTION_HOVER_MOVE 7
#define ACTION_SCROLL 8
#define BUTTON_PRIMARY 1
#define BUTTON_SECONDARY 2
#define BUTTON_TERTIARY 4
#define BUTTON_BACK 8
#define BUTTON_FORWARD 16

#define ORIENTATION_PORTRAIT 1

int x_min = 0, x_max = 0, y_min = 0, y_max = 0;
bool checkBounds = false;

JNIEXPORT void JNICALL Java_com_max2idea_android_limbo_jni_VMExecutor_nativeMouseEvent(
        JNIEnv* env, jobject thiz,
		int button, int action, int relative, int x, int y) {

    SDL_Window *window = SDL_GetMouseFocus();
    if (!window) {
        window = SDL_GetKeyboardFocus();
    }
    if (!window) {
        return;
    }
        
    //XXX: If the guest input device is not usb-tablet (ps2/usb) QEMU overrides to relative mode
    // in order to provide a workaround we force the mode. The user will still need to disable 
    // mouse acceleration within the guest and calibrate the mouse in limbo.
    SDL_bool relativeMouseMode = relative?SDL_TRUE:SDL_FALSE;
    if(SDL_GetRelativeMouseMode() != relativeMouseMode ) {
    	SDL_SetRelativeMouseMode(relativeMouseMode);
    }

	int mouse_x = 0;
	int mouse_y = 0;
	SDL_GetMouseState(&mouse_x, &mouse_y);
	// Adjust x, y if go out of bounds
	if(checkBounds) {
		if(relative && mouse_x + x < x_min)
			x = x_min - mouse_x;
		if(relative && mouse_x + x > x_max)
			x = x_max - mouse_x;
		if(relative && mouse_y + y < y_min)
			y = y_min - mouse_y;
		if(relative && mouse_y + y > y_max)
			y = y_max - mouse_y;
		if(!relative && x < x_min)
			x = x_min;
		if(!relative && x > x_max)
			x = x_max;
		if(!relative && y < y_min)
			y = y_min;
		if(!relative && y > y_max)
			y = y_max;			
		
	}
	
    switch(action) {
        case ACTION_DOWN:
            SDL_WarpMouseInWindow(window, x, y);
            SDL_PushEvent(&(SDL_Event) {
                .button = {
                    .type = SDL_MOUSEBUTTONDOWN,
                    .timestamp = SDL_GetTicks(),
                    .windowID = SDL_GetWindowID(window),
                    .which = SDL_TOUCH_MOUSEID,
                    .button = button,
                    .state = SDL_PRESSED,
                    .clicks = 1,
                    .x = x,
                    .y = y,
                },
            });
            break;

        case ACTION_UP:
            SDL_WarpMouseInWindow(window, x, y);
            SDL_PushEvent(&(SDL_Event) {
                .button = {
                    .type = SDL_MOUSEBUTTONUP,
                    .timestamp = SDL_GetTicks(),
                    .windowID = SDL_GetWindowID(window),
                    .which = SDL_TOUCH_MOUSEID,
                    .button = button,
                    .state = SDL_RELEASED,
                    .clicks = 1,
                    .x = x,
                    .y = y,
                },
            });
            break;

        case ACTION_MOVE:
        case ACTION_HOVER_MOVE:
            SDL_PushEvent(&(SDL_Event) {
                .motion = {
                    .type = SDL_MOUSEMOTION,
                    .timestamp = SDL_GetTicks(),
                    .windowID = SDL_GetWindowID(window),
                    .which = SDL_TOUCH_MOUSEID,
                    .state = 0,
                    .x = relative ? mouse_x + x : x,
                    .y = relative ? mouse_y + y : y,
                    .xrel = relative ? x : x - mouse_x,
                    .yrel = relative ? y : y - mouse_y,
                },
            });
            break;

        case ACTION_SCROLL:
            SDL_PushEvent(&(SDL_Event) {
                .wheel = {
                    .type = SDL_MOUSEWHEEL,
                    .timestamp = SDL_GetTicks(),
                    .windowID = SDL_GetWindowID(window),
                    .which = SDL_TOUCH_MOUSEID,
                    .x = x,
                    .y = y,
                    .direction = SDL_MOUSEWHEEL_NORMAL,
                },
            });
            break;

        default:
            break;
    }
}

JNIEXPORT void JNICALL Java_com_max2idea_android_limbo_jni_VMExecutor_nativeMouseBounds(
        JNIEnv* env, jobject thiz, int xmin, int xmax, int ymin, int ymax) {
    checkBounds = true;
    x_min = xmin+1;
	x_max = xmax-1;
	y_min = ymin+1;
	y_max = ymax-1;     
}
