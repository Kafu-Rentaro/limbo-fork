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
package com.max2idea.android.limbo.keyboard

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.max2idea.android.limbo.machine.MachineController
import com.max2idea.android.limbo.main.Config

object KeyboardUtils {
    @JvmStatic
    @Suppress("DEPRECATION")
    fun showKeyboard(activity: Activity, toggle: Boolean, view: View?): Boolean {
        if (MachineController.getInstance().isPaused) {
            return !toggle
        }

        val inputManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (toggle || !Config.enableToggleKeyboard) {
            view?.requestFocus()
            view?.let { inputManager.showSoftInput(it, InputMethodManager.SHOW_FORCED) }
        } else {
            view?.let { inputManager.hideSoftInputFromWindow(it.windowToken, 0) }
        }
        return !toggle
    }

    @JvmStatic
    fun hideKeyboard(activity: Activity, view: View?) {
        val inputManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view?.let { inputManager.hideSoftInputFromWindow(it.windowToken, 0) }
    }
}
