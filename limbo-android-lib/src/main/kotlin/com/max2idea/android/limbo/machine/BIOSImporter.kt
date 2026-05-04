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
package com.max2idea.android.limbo.machine

import android.app.Activity
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
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
import java.io.FileNotFoundException
import java.io.FileOutputStream

object BIOSImporter {
    private const val MAX_BIOS_SIZE_BYTES = 100 * 1024 * 1024

    @JvmStatic
    fun promptImportBIOSFile(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.ImportBIOSFile))
            .setView(createInstructionView(activity, R.string.importBIOSInstructions))
            .setPositiveButton(activity.getString(R.string.Ok)) { _, _ ->
                promptForImportBIOSFile(activity)
            }
            .setNegativeButton(activity.getString(R.string.Cancel), null)
            .show()
    }

    private fun promptForImportBIOSFile(activity: Activity) {
        Thread {
            LimboFileManager.browse(
                activity,
                FileType.IMPORT_BIOS_FILE,
                Config.OPEN_IMPORT_BIOS_FILE_REQUEST_CODE,
            )
        }.start()
    }

    @JvmStatic
    fun importBIOSFile(activity: Activity, importFilePath: String) {
        try {
            val target = File(
                LimboApplication.getBasefileDir(),
                FileUtils.getFilenameFromPath(importFilePath),
            )
            val buffer = ByteArray(32768)
            var totalBytes = 0

            val stream = FileUtils.getStreamFromFilePath(importFilePath)
                ?: throw FileNotFoundException(importFilePath)

            stream.use { input ->
                FileOutputStream(target).use { output ->
                    while (true) {
                        val bytesRead = input.read(buffer, 0, buffer.size)
                        if (bytesRead <= 0) {
                            break
                        }
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        if (totalBytes > MAX_BIOS_SIZE_BYTES) {
                            throw IllegalStateException("File too large")
                        }
                    }
                    output.flush()
                }
            }
        } catch (ex: Exception) {
            ToastUtils.toastShort(activity, ex.message)
            ex.printStackTrace()
        }
    }

    private fun createInstructionView(activity: Activity, textResId: Int): LinearLayout {
        val padding = activity.dp(24)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(
                TextView(activity).apply {
                    visibility = View.VISIBLE
                    text = activity.getString(textResId)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun Activity.dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
}
