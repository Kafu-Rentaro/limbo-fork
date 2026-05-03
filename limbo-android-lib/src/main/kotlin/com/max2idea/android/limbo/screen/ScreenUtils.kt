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
package com.max2idea.android.limbo.screen

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Point
import com.max2idea.android.limbo.main.LimboSettingsManager

object ScreenUtils {
    @JvmStatic
    fun updateOrientation(activity: Activity, lastOrientation: Int) {
        when (LimboSettingsManager.getOrientationSetting(activity)) {
            0 -> {
                if (lastOrientation >= 0) {
                    activity.requestedOrientation = lastOrientation
                }
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            1 -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            2 -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            3 -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            4 -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        }
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun isLandscapeOrientation(activity: Activity): Boolean {
        val display = activity.windowManager.defaultDisplay
        val screenSize = Point()
        display.getSize(screenSize)
        return screenSize.x >= screenSize.y
    }
}
