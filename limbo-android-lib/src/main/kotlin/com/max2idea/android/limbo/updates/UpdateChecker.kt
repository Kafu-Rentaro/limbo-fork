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
package com.max2idea.android.limbo.updates

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboApplication
import com.max2idea.android.limbo.main.LimboSettingsManager
import com.max2idea.android.limbo.network.NetworkUtils

object UpdateChecker {
    private const val TAG = "UpdateChecker"

    @JvmStatic
    fun checkNewVersion(activity: Activity) {
        if (!LimboSettingsManager.getPromptUpdateVersion(activity)) {
            return
        }

        try {
            val streamData = NetworkUtils.getContentFromUrl(Config.newVersionLink)
            val versionStr = String(streamData).trim()
            val version = versionStr.toFloat()
            val versionName = getVersionName(versionStr)

            val versionCheck = (version * 100).toInt()
            if (versionCheck > LimboApplication.getLimboVersion()) {
                Handler(Looper.getMainLooper()).post {
                    promptNewVersion(activity, versionName)
                }
            }
        } catch (ex: Exception) {
            Log.w(TAG, "Could not get new version: ${ex.message}")
            if (Config.debug) {
                ex.printStackTrace()
            }
        }
    }

    private fun getVersionName(versionStr: String): String {
        val versionSegments = versionStr.split(".")
        val majorAndMinor = versionSegments[0].toInt()
        val major = majorAndMinor / 100
        val minor = majorAndMinor % 100
        val micro = if (versionSegments.size > 1) {
            versionSegments[1].toInt()
        } else {
            0
        }
        return "$major.$minor.$micro"
    }

    @JvmStatic
    fun promptNewVersion(activity: Activity, version: String?) {
        val stateView = TextView(activity).apply {
            setText(R.string.NewVersionWarning)
            setPadding(20, 20, 20, 20)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle("${activity.getString(R.string.NewVersion)} ${version.orEmpty()}")
            .setView(stateView)
            .setPositiveButton(R.string.GenNewVersion) { _, _ ->
                NetworkUtils.openURL(activity, Config.downloadLink)
            }
            .setNegativeButton(R.string.DoNotShowAgain) { _, _ ->
                LimboSettingsManager.setPromptUpdateVersion(activity, false)
            }
            .show()
    }
}
