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

package com.max2idea.android.limbo.machine

import android.app.Activity
import android.net.Uri
import android.os.AsyncTask
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.files.FileUtils
import com.max2idea.android.limbo.machine.Machine.FileType
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboFileManager
import com.max2idea.android.limbo.main.LimboSettingsManager
import com.max2idea.android.limbo.toast.ToastUtils
import java.io.File

class MachineExporter(
    private val activity: Activity,
    @JvmField var exportFilePath: String,
) : AsyncTask<Void, Void, String?>() {

    override fun doInBackground(vararg params: Void?): String? = exportMachinesToFile(exportFilePath)

    override fun onPostExecute(displayName: String?) {
        if (displayName != null) {
            ToastUtils.toastLong(
                activity,
                "${activity.getString(R.string.MachinesExported)}: $displayName",
            )
        }
    }

    private fun exportMachinesToFile(exportFileName: String): String? {
        val exportDir = LimboSettingsManager.getExportDir(activity) ?: return null
        return if (exportDir.startsWith("content://")) {
            val exportDirUri = Uri.parse(exportDir)
            val machinesToExport = MachineOpenHelper.getInstance().exportMachines()
            val fileCreatedUri = FileUtils.exportFileContents(
                activity,
                exportDirUri,
                exportFileName,
                machinesToExport.toByteArray(),
            )
            fileCreatedUri?.toString()?.let(FileUtils::getFullPathFromDocumentFilePath)
        } else {
            exportMachinesFile(activity, exportDir, exportFileName)
        }
    }

    companion object {
        @JvmStatic
        fun promptExport(activity: Activity) {
            val exportNameView = TextInputEditText(activity).apply {
                isEnabled = true
                visibility = View.VISIBLE
                isSingleLine = true
            }
            val inputLayout = TextInputLayout(activity).apply {
                hint = activity.getString(R.string.ExportFilename)
                addView(
                    exportNameView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            val dialogView = LinearLayout(activity).apply {
                val padding = activity.dp(24)
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
                addView(
                    inputLayout,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }

            val alertDialog = MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.ExportFilename))
                .setView(dialogView)
                .setPositiveButton(activity.getString(R.string.Export), null)
                .setNegativeButton(activity.getString(R.string.ChangeDirectory), null)
                .create()

            alertDialog.show()

            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (LimboSettingsManager.getExportDir(activity) == null) {
                    changeExportDir(activity)
                    return@setOnClickListener
                }

                var exportFilename = exportNameView.text?.toString().orEmpty()
                if (exportFilename.trim().isEmpty()) {
                    ToastUtils.toastShort(
                        activity,
                        activity.getString(R.string.ExportFilenameCannotBeEmpty),
                    )
                    return@setOnClickListener
                }

                if (!exportFilename.endsWith(".csv")) {
                    exportFilename += ".csv"
                }

                exportMachines(activity, exportFilename)
                alertDialog.dismiss()
            }

            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                changeExportDir(activity)
            }
        }

        @JvmStatic
        fun exportMachines(activity: Activity, filePath: String) {
            MachineExporter(activity, filePath).execute()
        }

        @JvmStatic
        fun changeExportDir(activity: Activity) {
            ToastUtils.toastLong(activity, activity.getString(R.string.ChooseDirForVMExport))
            LimboFileManager.browse(
                activity,
                FileType.EXPORT_DIR,
                Config.OPEN_EXPORT_DIR_REQUEST_CODE,
            )
        }

        @JvmStatic
        fun exportMachinesFile(activity: Activity, destDir: String, destFile: String): String? {
            val destFileF = File(destDir, destFile)
            return try {
                val machinesToExport = MachineOpenHelper.getInstance().exportMachines()
                FileUtils.saveFileContents(destFileF.absolutePath, machinesToExport)
                destFileF.absolutePath
            } catch (ex: Exception) {
                ToastUtils.toastShort(
                    activity,
                    "${activity.getString(R.string.FailedToExportFile)}: ${destFileF.absolutePath}, " +
                        "${activity.getString(R.string.Error)}:${ex.message}",
                )
                null
            }
        }

        private fun Activity.dp(value: Int): Int =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                resources.displayMetrics,
            ).toInt()
    }
}
