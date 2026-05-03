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
package com.max2idea.android.limbo.log

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.files.FileUtils
import com.max2idea.android.limbo.machine.Machine.FileType
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboApplication
import com.max2idea.android.limbo.main.LimboFileManager
import com.max2idea.android.limbo.toast.ToastUtils
import java.io.File

object Logger {
    const val TAG = "Logger"

    @JvmStatic
    fun formatAndroidLog(contents: String): Spannable {
        val formattedString = SpannableString(contents)
        if (contents.isEmpty()) {
            return formattedString
        }

        try {
            var counter = 0
            contents.lineSequence().forEach { line ->
                val colorSpan = when {
                    line.startsWith("E/") || line.contains(" E ") ->
                        ForegroundColorSpan(Color.parseColor("#F78181"))
                    line.startsWith("W/") || line.contains(" W ") ->
                        ForegroundColorSpan(Color.parseColor("#81A3F7"))
                    line.startsWith("D/") || line.contains(" D ") ->
                        ForegroundColorSpan(Color.parseColor("#B1FFD7"))
                    else -> null
                }

                if (colorSpan != null) {
                    formattedString.setSpan(
                        colorSpan,
                        counter,
                        counter + line.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                counter += line.length + 1
            }
        } catch (ex: Exception) {
            Log.w(TAG, "Could not format limbo log: ${ex.message}")
        }

        return formattedString
    }

    @JvmStatic
    fun promptShowLog(activity: Activity) {
        val stateView = TextView(activity).apply {
            setText(R.string.LogWarning)
            setPadding(20, 20, 20, 20)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.ShowLog)
            .setView(stateView)
            .setPositiveButton(R.string.Yes) { _, _ ->
                viewLimboLog(activity)
            }
            .setNegativeButton(R.string.Cancel, null)
            .show()
    }

    @JvmStatic
    fun UIAlertLog(activity: Activity, title: String, body: Spannable) {
        val textView = TextView(activity).apply {
            setPadding(20, 20, 20, 20)
            text = body
            setBackgroundColor(Color.BLACK)
            setTextSize(12f)
            inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = false
        }
        val scrollView = ScrollView(activity).apply {
            addView(textView)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton(R.string.Ok, null)
            .setNeutralButton(R.string.CopyTo) { _, _ ->
                ToastUtils.toastShort(activity, activity.getString(R.string.ChooseDirToSaveLogFile))
                LimboFileManager.browse(activity, FileType.LOG_DIR, Config.OPEN_LOG_FILE_DIR_REQUEST_CODE)
            }
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
                show()
            }
    }

    @JvmStatic
    fun viewLimboLog(activity: Activity) {
        val logFilePath = Config.logFilePath.orEmpty()
        var contents = FileUtils.getFileContents(logFilePath)

        if (contents.length > 50 * 1024) {
            contents = contents.substring(0, 25 * 1024) +
                "\n.....\n" +
                contents.substring(contents.length - 25 * 1024)
        }

        val contentsFormatted = formatAndroidLog(contents)

        activity.runOnUiThread {
            if (Config.viewLogInternally) {
                UIAlertLog(activity, activity.getString(R.string.LimboLog), contentsFormatted)
            } else {
                try {
                    val intent = Intent(Intent.ACTION_EDIT)
                    val file = File(logFilePath)
                    val uri = Uri.fromFile(file)
                    intent.setDataAndType(uri, "text/plain")
                    activity.startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    UIAlertLog(activity, activity.getString(R.string.LimboLog), contentsFormatted)
                }
            }
        }
    }

    @JvmStatic
    fun setupLogFile(filePath: String) {
        Config.logFilePath = LimboApplication.getInstance().cacheDir.toString() + filePath
    }
}
