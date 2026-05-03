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
@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.max2idea.android.limbo.install

import android.app.Activity
import android.content.Context
import android.os.AsyncTask
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.files.FileInstaller
import com.max2idea.android.limbo.toast.ToastUtils
import kotlin.math.roundToInt

class Installer private constructor(
    private val activity: Activity,
    private val force: Boolean,
) : AsyncTask<Void, Void, Void>() {
    private var progressDialog: AlertDialog? = null

    override fun onPreExecute() {
        progressDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.PleaseWait)
            .setView(createProgressView())
            .setCancelable(false)
            .create()
            .also { it.show() }
    }

    override fun doInBackground(vararg params: Void?): Void? {
        FileInstaller.installFiles(activity, force)
        return null
    }

    override fun onPostExecute(result: Void?) {
        ToastUtils.toastShort(activity, "BIOS and Keymap files installed")
        progressDialog?.takeIf { it.isShowing }?.dismiss()
        progressDialog = null
    }

    private fun createProgressView(): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padding = dpToPx(24)
            setPadding(padding, padding, padding, padding)

            addView(ProgressBar(activity))
            addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.InstallingBIOS)
                    setPadding(padding, 0, 0, 0)
                },
            )
        }

    private fun dpToPx(dp: Int): Int = (dp * activity.resources.displayMetrics.density).roundToInt()

    companion object {
        @JvmStatic
        fun getAttrs(context: Context, res: Int): Array<String> {
            val fileContents = StringBuilder()
            try {
                context.resources.openRawResource(res).use { stream ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        val bytesRead = stream.read(buffer, 0, buffer.size)
                        if (bytesRead <= 0) {
                            break
                        }
                        fileContents.append(String(buffer, 0, bytesRead))
                    }
                }
            } catch (ex: Exception) {
                ToastUtils.toastShort(context, "${context.getString(R.string.CouldNotOpenRawFile)}: $ex")
            }
            return fileContents.toString().split(Regex("\\r\\n|\\n")).toTypedArray()
        }

        @JvmStatic
        fun installFiles(activity: Activity, force: Boolean) {
            Installer(activity, force).execute()
        }
    }
}
