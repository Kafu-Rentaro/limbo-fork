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
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.dialog.DialogUtils
import com.max2idea.android.limbo.machine.Machine.FileType
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboApplication
import com.max2idea.android.limbo.main.LimboSettingsManager
import com.max2idea.android.limbo.toast.ToastUtils
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

object FileUtils {
    private const val TAG = "FileUtils"
    private val fdsLock = Any()
    private val fds = HashMap<Int, FileInfo>()

    @JvmStatic
    fun getNativeLibDir(context: Context): String = context.applicationInfo.nativeLibraryDir

    @JvmStatic
    fun getFullPathFromDocumentFilePath(filePath: String): String {
        var path = filePath.replace("%3A", "^3A")
        val index = path.lastIndexOf("^3A")
        if (index > 0) {
            path = path.substring(index + 3)
        }
        if (!path.startsWith("/")) {
            path = "/$path"
        }

        return try {
            URLDecoder.decode(path, "UTF-8")
        } catch (ex: UnsupportedEncodingException) {
            ex.printStackTrace()
            path
        }
    }

    @JvmStatic
    fun getFilenameFromPath(filePath: String): String {
        val path = filePath
            .replace("%2F", "/")
            .replace("%3A", "/")
            .replace("^2F", "/")
            .replace("^3A", "/")
        val index = path.lastIndexOf("/")
        return if (index > 0) path.substring(index + 1) else path
    }

    @JvmStatic
    fun decodeDocumentFilePath(filePath: String?): String? {
        var path = filePath
        if (path != null && path.startsWith("/content//")) {
            path = path.replace("/content//", "content://")
            path = path.replace("^^^", "%")
        }
        return path
    }

    @JvmStatic
    fun encodeDocumentFilePath(filePath: String?): String? {
        var path = filePath
        if (path != null && path.startsWith("content://")) {
            path = path.replace("content://", "/content//")
            path = path.replace("%", "^^^")
        }
        return path
    }

    @JvmStatic
    fun saveFileContents(filePath: String, contents: String) {
        byteArrayToFile(contents.toByteArray(), File(filePath))
    }

    @JvmStatic
    fun byteArrayToFile(byteData: ByteArray, filePath: File) {
        try {
            FileOutputStream(filePath).use { output ->
                output.write(byteData)
            }
        } catch (ex: FileNotFoundException) {
            println("FileNotFoundException : $ex")
        } catch (ex: IOException) {
            println("IOException : $ex")
        }
    }

    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun getStreamFromFilePath(filePath: String): InputStream {
        return if (filePath.startsWith("content://")) {
            val uri = Uri.parse(filePath)
            val pfd = LimboApplication.getInstance().contentResolver.openFileDescriptor(uri, "rw")!!
            FileInputStream(pfd.fileDescriptor)
        } else {
            FileInputStream(filePath)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun closeFileDescriptor(filePath: String) {
        if (filePath.startsWith("content://")) {
            val uri = Uri.parse(filePath)
            LimboApplication.getInstance().contentResolver.openFileDescriptor(uri, "rw")?.close()
        }
    }

    @JvmStatic
    fun getFileContents(filePath: String?): String {
        val file = File(filePath ?: return "")
        if (!file.exists()) {
            return ""
        }
        val builder = StringBuilder()
        try {
            FileInputStream(file).use { stream ->
                val buffer = ByteArray(32768)
                while (true) {
                    val bytesRead = stream.read(buffer, 0, buffer.size)
                    if (bytesRead <= 0) {
                        break
                    }
                    builder.append(String(buffer, 0, bytesRead))
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return builder.toString()
    }

    @JvmStatic
    fun fileValid(path: String?): Boolean {
        if (path.isNullOrEmpty()) {
            return true
        }
        if (path.startsWith("content://") || path.startsWith("/content/")) {
            return get_fd(path) > 0
        }

        val file = File(path)
        file.setWritable(true)
        return file.exists()
    }

    @JvmStatic
    fun get_fd(path: String?): Int {
        synchronized(fdsLock) {
            var fd = 0
            if (path == null) {
                return 0
            }
            if (path.startsWith("/content//") || path.startsWith("content://")) {
                val npath = decodeDocumentFilePath(path)
                try {
                    val uri = Uri.parse(npath)
                    val mode = if (path.lowercase().endsWith(".iso")) "r" else "rw"
                    val pfd = LimboApplication.getInstance().contentResolver.openFileDescriptor(uri, mode)!!
                    fd = pfd.fd
                    fds[fd] = FileInfo(path, npath, pfd)
                    Log.d(TAG, "Opening Content Uri: $npath, FD: $fd")
                } catch (ex: Exception) {
                    val msg = LimboApplication.getInstance().getString(R.string.CouldNotOpenDocFile) +
                        " " + getFullPathFromDocumentFilePath(npath ?: "") +
                        "\n" + LimboApplication.getInstance().getString(R.string.PleaseReassingYourDiskFiles)
                    ToastUtils.toastLong(LimboApplication.getInstance(), msg)
                    ex.printStackTrace()
                }
            } else {
                try {
                    val mode = if (path.lowercase().endsWith(".iso")) {
                        ParcelFileDescriptor.MODE_READ_ONLY
                    } else {
                        ParcelFileDescriptor.MODE_READ_WRITE
                    }
                    val file = File(path)
                    if (!file.exists()) {
                        file.createNewFile()
                    }
                    val pfd = ParcelFileDescriptor.open(file, mode)
                    fd = pfd.fd
                    fds[fd] = FileInfo(path, path, pfd)
                    Log.d(TAG, "Opening File: $path, FD: $fd")
                } catch (ex: Exception) {
                    Log.e(TAG, "Could not open File: $path, FD: $fd")
                    if (Config.debug) {
                        ex.printStackTrace()
                    }
                }
            }
            return fd
        }
    }

    @JvmStatic
    fun close_fds() {
        synchronized(fdsLock) {
            val openFds = fds.keys.toTypedArray()
            for (fd in openFds) {
                close_fd(fd)
            }
        }
    }

    @JvmStatic
    fun close_fd(fd: Int): Int {
        if (!Config.closeFileDescriptors) {
            return 0
        }
        synchronized(fdsLock) {
            val info = fds[fd]
            if (info != null) {
                try {
                    val pfd = info.pfd
                    if (Config.syncFilesOnClose) {
                        try {
                            pfd.fileDescriptor.sync()
                        } catch (ex: IOException) {
                            if (Config.debug) {
                                Log.w(TAG, "Syncing DocumentFile: ${info.path}: $fd : $ex")
                                ex.printStackTrace()
                            }
                        }
                    }
                    pfd.close()
                    fds.remove(fd)
                    return 0
                } catch (ex: IOException) {
                    Log.e(TAG, "Error Closing DocumentFile: ${info.path}: $fd : $ex")
                    if (Config.debug) {
                        ex.printStackTrace()
                    }
                }
            } else {
                try {
                    var path = ""
                    val fallbackInfo = fds[fd]
                    var pfd = fallbackInfo?.pfd
                    if (fallbackInfo != null) {
                        path = fallbackInfo.path
                    }
                    if (pfd == null) {
                        pfd = ParcelFileDescriptor.fromFd(fd)
                    }
                    if (Config.syncFilesOnClose) {
                        try {
                            pfd.fileDescriptor.sync()
                        } catch (ex: IOException) {
                            if (Config.debug) {
                                Log.w(TAG, "Error Syncing File: $path: $fd : $ex")
                                ex.printStackTrace()
                            }
                        }
                    }
                    pfd.close()
                    return 0
                } catch (ex: Exception) {
                    Log.e(TAG, "Error Closing File FD: $fd : $ex")
                    if (Config.debug) {
                        ex.printStackTrace()
                    }
                }
            }
            return -1
        }
    }

    @JvmStatic
    fun startLogging() {
        val logFilePath = Config.logFilePath
        if (logFilePath == null) {
            Log.w(TAG, "Log file is not ready")
            return
        }
        val thread = Thread {
            var output: FileOutputStream? = null
            try {
                val logFile = File(logFilePath)
                if (logFile.exists() && !logFile.delete()) {
                    Log.w(TAG, "Could not delete previous log file!")
                }
                logFile.createNewFile()
                Runtime.getRuntime().exec("logcat -c")
                val process = Runtime.getRuntime().exec("logcat v main")
                output = FileOutputStream(logFile)
                val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                val log = StringBuilder()
                var line = bufferedReader.readLine()
                while (line != null) {
                    log.setLength(0)
                    log.append(line).append("\n")
                    output.write(log.toString().toByteArray(Charsets.UTF_8))
                    output.flush()
                    line = bufferedReader.readLine()
                }
            } catch (ex: IOException) {
                ex.printStackTrace()
            } finally {
                try {
                    output?.flush()
                    output?.close()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            }
        }
        thread.name = "LimboLogger"
        thread.start()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun LoadFile(context: Context, fileName: String, loadFromRawFolder: Boolean): String {
        val input = if (loadFromRawFolder) {
            val resourceName = LimboApplication.getInstance().javaClass.`package`!!.name + ":raw/" + fileName
            val resourceId = context.resources.getIdentifier(resourceName, null, null)
            context.resources.openRawResource(resourceId)
        } else {
            context.resources.assets.open(fileName)
        }

        val output = ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(stream.available())
            while (true) {
                val bytesRead = stream.read(buffer)
                if (bytesRead <= 0) {
                    break
                }
                output.write(buffer, 0, bytesRead)
            }
        }
        output.close()
        return output.toString()
    }

    @JvmStatic
    fun getExtensionFromFilename(fileName: String?): String {
        if (fileName == null) {
            return ""
        }
        val index = fileName.lastIndexOf(".")
        return if (index >= 0) fileName.substring(index + 1) else ""
    }

    @JvmStatic
    fun getIconForFile(file: String): Int {
        val lowerFile = file.lowercase()
        val ext = getExtensionFromFilename(lowerFile).lowercase()

        return if (ext == "img" || ext == "qcow" ||
            ext == "qcow2" || ext == "vmdk" || ext == "vdi" || ext == "cow" ||
            ext == "dmg" || ext == "bochs" || ext == "vpc" ||
            ext == "vhd" || ext == "fs"
        ) {
            R.drawable.harddisk
        } else if (ext == "iso") {
            R.drawable.cd
        } else if (ext == "ima") {
            R.drawable.floppy
        } else if (ext == "csv") {
            R.drawable.importvms
        } else if (lowerFile.contains("kernel") || lowerFile.contains("vmlinuz") || lowerFile.contains("initrd")) {
            R.drawable.sysfile
        } else {
            R.drawable.close
        }
    }

    @JvmStatic
    fun saveLogFileSDCard(activity: Activity, destDir: Uri): Uri? {
        var output: OutputStream? = null
        var uri: Uri? = null

        try {
            val logFileContents = getFileContents(Config.logFilePath)
            val dir = DocumentFile.fromTreeUri(activity, destDir)
                ?: throw Exception("Could not get log path directory")
            var destFile = dir.findFile(Config.destLogFilename)
            if (destFile == null) {
                destFile = dir.createFile(
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension("txt") ?: "text/plain",
                    Config.destLogFilename,
                )
            }

            output = activity.contentResolver.openOutputStream(destFile!!.uri)
            output!!.write(logFileContents.toByteArray())
            uri = destFile.uri
        } catch (ex: Exception) {
            ToastUtils.toastShort(activity, activity.getString(R.string.FailedToSaveLogFile) + ex.message)
        } finally {
            try {
                output?.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
        return uri
    }

    @JvmStatic
    fun exportFileContents(
        activity: Activity,
        destDir: Uri,
        destFile: String,
        contents: ByteArray,
    ): Uri? {
        var output: OutputStream? = null
        var uri: Uri? = null

        try {
            val dir = DocumentFile.fromTreeUri(activity, destDir)
            var destFileF = dir!!.findFile(destFile)
            if (destFileF == null) {
                destFileF = dir.createFile(
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension("csv") ?: "text/csv",
                    destFile,
                )
            } else {
                ToastUtils.toastShort(activity, activity.getString(R.string.FileExistsChooseAnotherFilename))
                return null
            }

            output = activity.contentResolver.openOutputStream(destFileF!!.uri)
            output!!.write(contents)
            uri = destFileF.uri
        } catch (ex: Exception) {
            ToastUtils.toastShort(
                activity,
                activity.getString(R.string.FailedToExportFile) +
                    ": " + destFile + ", " + activity.getString(R.string.Error) + ":" + ex.message,
            )
        } finally {
            try {
                output?.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
        return uri
    }

    @JvmStatic
    fun saveLogFileLegacy(activity: Activity, destLogFilePath: String): String? {
        val destFile = File(destLogFilePath, Config.destLogFilename)
        return try {
            val logFileContents = getFileContents(Config.logFilePath)
            saveFileContents(destFile.absolutePath, logFileContents)
            destFile.absolutePath
        } catch (ex: Exception) {
            ToastUtils.toastShort(
                activity,
                activity.getString(R.string.FailedSaveLogFile) +
                    ": " + destFile.absolutePath + ", " +
                    activity.getString(R.string.Error) + ": " + ex.message,
            )
            null
        }
    }

    @JvmStatic
    fun getFileUriFromIntent(activity: Activity, data: Intent?, write: Boolean): String? {
        if (data == null) {
            return null
        }
        val uri = data.data ?: return null
        val file = uri.toString()
        if (!file.contains("com.android.externalstorage.documents")) {
            showFileNotSupported(activity)
            return null
        }
        activity.grantUriPermission(activity.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (write) {
            activity.grantUriPermission(activity.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        activity.grantUriPermission(activity.packageName, uri, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

        var takeFlags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (write) {
            takeFlags = takeFlags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        activity.contentResolver.takePersistableUriPermission(uri, takeFlags)
        return file
    }

    @JvmStatic
    fun getDirPathFromIntent(activity: Activity, data: Intent?): String? {
        return data?.extras?.getString("currDir")
    }

    @JvmStatic
    fun getFilePathFromIntent(activity: Activity, data: Intent?): String? {
        return data?.extras?.getString("file")
    }

    @JvmStatic
    fun getFileTypeFromIntent(activity: Activity, data: Intent?): FileType? {
        @Suppress("DEPRECATION")
        return data?.extras?.getSerializable("fileType") as? FileType
    }

    @JvmStatic
    fun showFileNotSupported(context: Activity) {
        DialogUtils.UIAlert(
            context,
            context.getString(R.string.Error),
            context.getString(R.string.FilePathNotSupportedWarning),
        )
    }

    @JvmStatic
    fun saveLogToFile(activity: Activity, logFileDestDir: String) {
        Thread {
            val displayName = if (logFileDestDir.startsWith("content://")) {
                val exportDirUri = Uri.parse(logFileDestDir)
                val fileCreatedUri = saveLogFileSDCard(activity, exportDirUri)
                fileCreatedUri?.let { getFullPathFromDocumentFilePath(it.toString()) }
            } else {
                saveLogFileLegacy(activity, logFileDestDir)
            }

            if (displayName != null) {
                ToastUtils.toastShort(activity, activity.getString(R.string.LogfileSaved))
            }
        }.start()
    }

    @JvmStatic
    fun convertFilePath(text: String, position: Int): String {
        return try {
            getFullPathFromDocumentFilePath(text)
        } catch (ex: Exception) {
            if (Config.debug) {
                ex.printStackTrace()
            }
            text
        }
    }

    class FileInfo(
        @JvmField val path: String,
        @JvmField val npath: String?,
        @JvmField val pfd: ParcelFileDescriptor,
    )

    @JvmStatic
    fun createImgFromTemplate(
        context: Context,
        templateImage: String,
        destImage: String,
        imgType: FileType,
    ): String? {
        val imagesDir = LimboSettingsManager.getImagesDir(context) ?: return null
        var displayName: String? = null
        var filePath: String? = null
        if (imagesDir.startsWith("content://")) {
            val imagesDirUri = Uri.parse(imagesDir)
            val fileCreatedUri = FileInstaller.installImageTemplateToSDCard(
                context,
                templateImage,
                imagesDirUri,
                "hdtemplates",
                destImage,
            )
            if (fileCreatedUri != null) {
                displayName = getFullPathFromDocumentFilePath(fileCreatedUri.toString())
                filePath = fileCreatedUri.toString()
            }
        } else {
            filePath = FileInstaller.installImageTemplateToExternalStorage(
                context,
                templateImage,
                imagesDir,
                "hdtemplates",
                destImage,
            )
            displayName = filePath
        }
        if (displayName != null) {
            ToastUtils.toastShort(context, context.getString(R.string.ImageCreated) + ": " + displayName)
            return filePath
        }
        return null
    }
}
