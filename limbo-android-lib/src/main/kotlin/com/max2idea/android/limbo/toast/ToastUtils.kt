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
package com.max2idea.android.limbo.toast

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Toast

object ToastUtils {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun toastLong(activity: Context, msg: String?) {
        toastLong(activity, Gravity.CENTER, msg)
    }

    @JvmStatic
    fun toastLong(activity: Context, gravity: Int, msg: String?) {
        mainHandler.post {
            Toast.makeText(activity, msg.orEmpty(), Toast.LENGTH_LONG).apply {
                setGravity(gravity, 0, 0)
                show()
            }
        }
    }

    @JvmStatic
    fun toastShortTop(activity: Activity, msg: String?) {
        toast(activity, msg, Gravity.TOP or Gravity.CENTER, Toast.LENGTH_SHORT)
    }

    @JvmStatic
    fun toast(context: Context, msg: String?, gravity: Int, length: Int) {
        mainHandler.post {
            if (context is Activity && context.isFinishing) {
                return@post
            }
            Toast.makeText(context, msg.orEmpty(), length).apply {
                setGravity(gravity, 0, 0)
                show()
            }
        }
    }

    @JvmStatic
    fun toastShort(context: Context, msg: String?) {
        toast(context, msg, Gravity.CENTER, Toast.LENGTH_SHORT)
    }
}
