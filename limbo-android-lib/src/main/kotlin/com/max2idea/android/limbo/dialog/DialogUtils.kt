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
package com.max2idea.android.limbo.dialog

import android.app.Activity
import android.content.DialogInterface
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object DialogUtils {
    @JvmStatic
    fun UIAlert(activity: Activity, title: String?, body: String?) {
        val textView = TextView(activity).apply {
            setPadding(20, 20, 20, 20)
            text = body.orEmpty()
        }
        val scrollView = ScrollView(activity).apply {
            addView(textView)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    @JvmStatic
    fun UIAlert(
        activity: Activity,
        title: String?,
        body: String?,
        textSize: Int,
        cancelable: Boolean,
        button1title: String?,
        button1Listener: DialogInterface.OnClickListener?,
        button2title: String?,
        button2Listener: DialogInterface.OnClickListener?,
        button3title: String?,
        button3Listener: DialogInterface.OnClickListener?,
    ) {
        val textView = TextView(activity).apply {
            setPadding(20, 20, 20, 20)
            text = body.orEmpty()
            if (textSize > 0) {
                setTextSize(textSize.toFloat())
            }
        }
        val scrollView = ScrollView(activity).apply {
            addView(textView)
        }
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(scrollView)

        if (button1title != null) {
            builder.setPositiveButton(button1title, button1Listener)
        }
        if (button2title != null) {
            builder.setNegativeButton(button2title, button2Listener)
        }
        if (button3title != null) {
            builder.setNeutralButton(button3title, button3Listener)
        }

        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(cancelable)
        dialog.show()
    }
}
