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
package com.max2idea.android.limbo.files

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.main.LimboApplication
import com.max2idea.android.limbo.toast.ToastUtils
import java.io.File
import java.io.FileOutputStream
import java.util.logging.Level
import java.util.logging.Logger

object FileInstaller {
    private const val TAG = "FileInstaller"
    private const val BUFFER_SIZE = 8092

    @JvmStatic
    fun installFiles(activity: Activity, force: Boolean) {
        Log.d(TAG, "Installing files")
        val baseDir = File(LimboApplication.getBasefileDir())
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            ToastUtils.toastShort(
                activity,
                "${activity.getString(R.string.CouldNotCreateBaseDir)}: ${baseDir.path}",
            )
            return
        }

        val machineDir = File(LimboApplication.getMachineDir())
        if (!machineDir.exists() && !machineDir.mkdirs()) {
            ToastUtils.toastShort(
                activity,
                "${activity.getString(R.string.CouldNotCreateMachineDir)}: ${machineDir.path}",
            )
            return
        }

        if (baseDir.exists() && !baseDir.isDirectory) {
            Log.e(TAG, "Could not create Dir, file found: ${LimboApplication.getBasefileDir()}")
            return
        }
        if (!baseDir.exists() && !baseDir.mkdir()) {
            Log.e(TAG, "Could not create base Dir: ${LimboApplication.getBasefileDir()}")
            return
        }

        val destDir = LimboApplication.getBasefileDir()
        val assets = activity.resources.assets
        val files = try {
            assets.list("roms") ?: emptyArray()
        } catch (ex: Exception) {
            Logger.getLogger(FileInstaller::class.java.name).log(Level.SEVERE, null, ex)
            Log.e(TAG, "Could not install files: ${ex.message}")
            return
        }

        for (assetName in files) {
            val subfiles = try {
                assets.list("roms/$assetName")
            } catch (ex: Exception) {
                Logger.getLogger(FileInstaller::class.java.name).log(Level.SEVERE, null, ex)
                null
            }

            if (!subfiles.isNullOrEmpty()) {
                val subdir = File(baseDir, assetName)
                if (subdir.exists() && !subdir.isDirectory) {
                    Log.e(TAG, "Could not create Dir, file found: ${subdir.path}")
                    return
                }
                if (!subdir.exists() && !subdir.mkdir()) {
                    Log.e(TAG, "Could not create Dir: ${subdir.path}")
                    return
                }
                for (subfile in subfiles) {
                    val relativePath = "$assetName/$subfile"
                    val file = File(destDir, relativePath)
                    if (!file.exists() || force) {
                        Log.d(TAG, "Installing file: ${file.path}")
                        installAssetFile(activity, relativePath, destDir, "roms", null)
                    }
                }
            } else {
                val file = File(destDir, assetName)
                if (!file.exists() || force) {
                    Log.d(TAG, "Installing file: ${file.path}")
                    installAssetFile(activity, assetName, destDir, "roms", null)
                }
            }
        }
    }

    @JvmStatic
    fun installAssetFile(
        activity: Activity,
        srcFile: String,
        destDir: String,
        assetsDir: String,
        destFile: String?,
    ): Boolean {
        return try {
            val targetName = destFile ?: srcFile
            val destDirF = File(destDir)
            if (!destDirF.exists() && !destDirF.mkdirs()) {
                ToastUtils.toastShort(activity, activity.getString(R.string.CouldNotCreateDirForImage))
                return false
            }

            activity.resources.assets.open("$assetsDir/$srcFile").use { input ->
                FileOutputStream(File(destDirF, targetName)).use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
            true
        } catch (ex: Exception) {
            Log.e(TAG, "failed to install file: $destFile, Error:${ex.message}")
            false
        }
    }

    @JvmStatic
    fun installImageTemplateToSDCard(
        context: Context,
        srcFile: String,
        destDir: Uri,
        assetsDir: String,
        destFile: String?,
    ): Uri? {
        val targetName = destFile ?: srcFile
        return try {
            val dir = DocumentFile.fromTreeUri(context, destDir) ?: return null
            val existingFile = dir.findFile(targetName)
            if (existingFile != null) {
                ToastUtils.toastShort(context, context.getString(R.string.FileExistsChooseAnotherFilename))
                return null
            }

            val destFileF = dir.createFile("application/octet-stream", targetName) ?: return null
            val output = context.contentResolver.openOutputStream(destFileF.uri) ?: return null
            context.resources.assets.open("$assetsDir/$srcFile").use { input ->
                output.use { target ->
                    input.copyTo(target, BUFFER_SIZE)
                }
            }
            destFileF.uri
        } catch (ex: Exception) {
            Log.e(TAG, "failed to install file: $targetName, Error:${ex.message}")
            null
        }
    }

    @JvmStatic
    fun installImageTemplateToExternalStorage(
        context: Context,
        srcFile: String,
        destDir: String,
        assetsDir: String,
        destFile: String?,
    ): String? {
        val targetName = destFile ?: srcFile
        val file = File(destDir, targetName)
        return try {
            if (!file.exists()) {
                file.createNewFile()
            } else {
                ToastUtils.toastShort(context, context.getString(R.string.FileExistsChooseAnotherFilename))
                return null
            }

            context.resources.assets.open("$assetsDir/$srcFile").use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
            file.absolutePath
        } catch (ex: Exception) {
            Log.e(TAG, "failed to install file: $targetName, Error:${ex.message}")
            null
        }
    }
}
