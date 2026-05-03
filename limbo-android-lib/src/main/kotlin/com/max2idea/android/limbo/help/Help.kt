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
package com.max2idea.android.limbo.help

import android.app.Activity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.HostCapabilities
import com.max2idea.android.limbo.main.LimboApplication
import com.max2idea.android.limbo.main.LimboSettingsManager
import com.max2idea.android.limbo.network.NetworkUtils

object Help {
    @JvmStatic
    fun showHelp(activity: Activity) {
        val density = activity.resources.displayMetrics.density
        val padding = (20 * density).toInt()
        val scrollHeight = (300 * density).toInt()

        val textView = TextView(activity).apply {
            textSize = 15f
            text = activity.getString(R.string.welcomeText) + "\n\n" + HostCapabilities.getSummary()
            setPadding(padding, padding, padding, padding)
        }

        val scrollView = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                scrollHeight,
            )
            addView(textView)
        }

        val checkUpdates = CheckBox(activity).apply {
            setText(R.string.checkForUpdates)
            isChecked = LimboSettingsManager.getPromptUpdateVersion(activity)
            setOnCheckedChangeListener { _, checked ->
                LimboSettingsManager.setPromptUpdateVersion(activity, checked)
            }
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(scrollView)
            addView(checkUpdates)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(
                "${Config.APP_NAME} ${LimboApplication.getLimboVersionString()} " +
                    "QEMU ${LimboApplication.getQemuVersionString()}",
            )
            .setView(content)
            .setPositiveButton(R.string.GoToWiki) { _, _ ->
                NetworkUtils.openURL(activity, Config.guidesLink)
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }
}
