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

package com.max2idea.android.limbo.main

import android.Manifest
import android.app.Activity
import android.app.ListActivity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.dialog.DialogUtils
import com.max2idea.android.limbo.files.FileUtils
import com.max2idea.android.limbo.machine.Machine.FileType
import com.max2idea.android.limbo.toast.ToastUtils
import java.io.File
import java.util.Comparator

/**
 * Legacy File Manager Activity for older devices and devices that don't support Android Storage
 * Framework. This requires android:requestLegacyExternalStorage="true" in AndroidManifest.xml.
 */
class LimboFileManager : ListActivity() {
    private val selectDir = 1
    private val createDir = 2
    private val cancel = 3

    val comparator: Comparator<File> = Comparator { object1, object2 ->
        when {
            object1.name.startsWith("..") -> -1
            object2.name.startsWith("..") -> 1
            object1.name.endsWith("/") && !object2.name.endsWith("/") -> -1
            !object1.name.endsWith("/") && object2.name.endsWith("/") -> 1
            else -> object1.toString().compareTo(object2.toString(), ignoreCase = true)
        }
    }

    private var items = ArrayList<File>()
    private var currdir = File(Environment.getExternalStorageDirectory().absolutePath)
    private var file: File? = null
    private lateinit var currentDir: TextView
    private lateinit var select: Button
    private var fileType: FileType? = null
    private var filter = HashMap<String, String>()
    private var selectionMode = SelectionMode.FILE

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        setContentView(R.layout.directory_list)
        select = findViewById(R.id.select_button)
        select.setOnClickListener { selectDir() }
        currentDir = findViewById(R.id.currDir)
        val bundle = intent.extras ?: Bundle()
        var lastDirectory = bundle.getString("lastDir")
        @Suppress("DEPRECATION")
        fileType = bundle.getSerializable("fileType") as? FileType
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        filter = bundle.getSerializable("filterExt") as? HashMap<String, String> ?: HashMap()

        if (isFileTypeDirectory(fileType)) {
            selectionMode = SelectionMode.DIRECTORY
        } else {
            selectionMode = SelectionMode.FILE
            select.visibility = View.GONE
        }

        if (selectionMode == SelectionMode.DIRECTORY) {
            title = getString(R.string.SelectADirectory)
        } else {
            title = getString(R.string.SelectAFile)
        }

        if (lastDirectory == null) {
            lastDirectory = Environment.getExternalStorageDirectory().path
        }
        currdir = File(lastDirectory)
        if (!currdir.isDirectory || !currdir.exists()) {
            lastDirectory = Environment.getExternalStorageDirectory().path
            currdir = File(lastDirectory)
        }
        currentDir.text = currdir.path
        Handler(Looper.getMainLooper()).postDelayed({ checkPermissionsAndBrowse() }, 500)
    }

    private fun filter(filePath: File): Boolean {
        val ext = FileUtils.getExtensionFromFilename(filePath.name)
        return filter.isEmpty() || filter.containsKey(ext.lowercase())
    }

    private fun checkPermissionsAndBrowse() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                DialogUtils.UIAlert(
                    this,
                    getString(R.string.WriteAccess),
                    getString(R.string.FullAccessWarning),
                    16,
                    false,
                    getString(R.string.OkIUnderstand),
                    { _, _ ->
                        ActivityCompat.requestPermissions(
                            this@LimboFileManager,
                            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            REQUEST_WRITE_PERMISSION,
                        )
                    },
                    null,
                    null,
                    null,
                    null,
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_PERMISSION,
                )
            }
        } else {
            fill(currdir.listFiles())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        if (requestCode == REQUEST_WRITE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fill(currdir.listFiles())
            } else {
                ToastUtils.toastShort(this, getString(R.string.FeaturDisabled))
                finish()
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun fill(files: Array<File>?) {
        items = ArrayList()
        items.add(File(".. (Parent Directory)"))

        if (files != null) {
            for (candidate in files) {
                if (candidate.isFile && filter(candidate)) {
                    items.add(candidate)
                } else if (candidate.isDirectory) {
                    items.add(candidate)
                }
            }
        }
        items.sortWith(comparator)
        listAdapter = FileAdapter(this, R.layout.dir_row, items)

        LimboSettingsManager.setLastDir(this, currdir.absolutePath)
    }

    override fun onListItemClick(list: ListView, view: View, position: Int, id: Long) {
        super.onListItemClick(list, view, position, id)
        val selectionRowId = id.toInt()
        if (selectionRowId == 0) {
            fillWithParent()
            return
        }

        file = items.getOrNull(selectionRowId)
        val selectedFile = file
        if (selectedFile == null) {
            ToastUtils.toastShort(this, getString(R.string.AccessDeniedCannotRetrieveDirectory))
        } else if (!selectedFile.isDirectory && selectionMode == SelectionMode.DIRECTORY) {
            ToastUtils.toastShort(this, getString(R.string.NotADirectory))
        } else if (selectedFile.isDirectory) {
            currdir = selectedFile
            val files = selectedFile.listFiles()
            if (files != null) {
                currentDir.text = selectedFile.path
                fill(files)
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.AccessDenied)
                    .setMessage(R.string.CannotListDirectory)
                    .show()
            }
        } else {
            selectFile()
        }
    }

    private fun fillWithParent() {
        if (currdir.path.equals("/", ignoreCase = true)) {
            currentDir.text = currdir.path
            fill(currdir.listFiles())
        } else {
            currdir = currdir.parentFile ?: currdir
            currentDir.text = currdir.path
            fill(currdir.listFiles())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, selectDir, 0, "Select Directory")
        menu.add(0, createDir, 0, "Create Directory")
        menu.add(0, cancel, 0, "Cancel")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            selectDir -> {
                selectDir()
                true
            }
            createDir -> {
                promptCreateDir(this)
                true
            }
            cancel -> {
                cancel()
                true
            }
            else -> false
        }

    fun promptCreateDir(activity: Activity) {
        val alertDialog: AlertDialog = MaterialAlertDialogBuilder(activity).create()
        alertDialog.setTitle(getString(R.string.NewDirectory))
        val dirNameTextview = EditText(activity)
        dirNameTextview.setPadding(20, 20, 20, 20)
        dirNameTextview.isEnabled = true
        dirNameTextview.visibility = View.VISIBLE
        dirNameTextview.setSingleLine()
        alertDialog.setView(dirNameTextview)
        alertDialog.setCanceledOnTouchOutside(false)
        alertDialog.setButton(
            DialogInterface.BUTTON_POSITIVE,
            getString(R.string.Create),
            null as DialogInterface.OnClickListener?,
        )

        alertDialog.show()

        val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        button.setOnClickListener {
            if (dirNameTextview.text.toString().trim().isEmpty()) {
                ToastUtils.toastShort(activity, getString(R.string.DirNameCannotBeEmpty))
            } else {
                createDirectory(dirNameTextview.text.toString())
                fill(currdir.listFiles())
                alertDialog.dismiss()
            }
        }
    }

    private fun createDirectory(dirName: String) {
        val dir = File(currdir, dirName)
        if (!dir.exists()) {
            dir.mkdirs()
        } else {
            ToastUtils.toastShort(this, getString(R.string.DirectoryAlreadyExists))
        }
    }

    fun selectDir() {
        val data = Intent()
        val bundle = Bundle()
        bundle.putString("currDir", currdir.path)
        bundle.putSerializable("fileType", fileType)
        data.putExtras(bundle)
        setResult(Config.FILEMAN_RETURN_CODE, data)
        finish()
    }

    fun selectFile() {
        val data = Intent()
        val bundle = Bundle()
        bundle.putString("currDir", currdir.path)
        bundle.putString("file", file?.path)
        bundle.putSerializable("fileType", fileType)
        data.putExtras(bundle)
        setResult(Config.FILEMAN_RETURN_CODE, data)
        finish()
    }

    private fun cancel() {
        val data = Intent()
        data.putExtra("currDir", "")
        setResult(Config.FILEMAN_RETURN_CODE, data)
        finish()
    }

    enum class SelectionMode {
        DIRECTORY,
        FILE,
    }

    class FileAdapter(
        context: Context,
        layout: Int,
        private val files: ArrayList<File>,
    ) : ArrayAdapter<File>(context, layout, files) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val rowView = inflater.inflate(R.layout.dir_row, parent, false)
            val textView = rowView.findViewById<TextView>(R.id.FILE_NAME)
            val imageView = rowView.findViewById<ImageView>(R.id.FILE_ICON)
            val file = files[position]
            textView.text = file.name

            if (file.name.startsWith("..") || file.isDirectory) {
                imageView.setImageResource(R.drawable.folder)
            } else {
                imageView.setImageResource(FileUtils.getIconForFile(file.name))
            }
            return rowView
        }
    }

    companion object {
        private const val REQUEST_WRITE_PERMISSION = 1001
        private const val TAG = "FileManager"

        @JvmStatic
        fun browse(activity: Activity, fileType: FileType, requestCode: Int) {
            val lastDir = getLastDir(activity, fileType)
            val state = Environment.getExternalStorageState()
            if (Environment.MEDIA_MOUNTED != state) {
                ToastUtils.toastShort(activity, activity.resources.getString(R.string.sdcardNotMounted))
                return
            }

            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP ||
                LimboSettingsManager.getEnableLegacyFileManager(activity) ||
                fileType == FileType.SHARED_DIR
            ) {
                promptLegacyStorageAccess(activity, fileType, requestCode, lastDir)
            } else {
                try {
                    promptOpenFileASF(activity, fileType, getASFFileManagerRequestCode(requestCode), lastDir)
                } catch (ex: Exception) {
                    Log.e(TAG, "Using Legacy File Manager due to exception :${ex.message}")
                    promptLegacyStorageAccess(activity, fileType, requestCode, lastDir)
                }
            }
        }

        private fun getLastDir(context: Context, fileType: FileType): String? =
            when (fileType) {
                FileType.SHARED_DIR -> LimboSettingsManager.getSharedDir(context)
                FileType.EXPORT_DIR,
                FileType.IMPORT_FILE,
                -> LimboSettingsManager.getExportDir(context)
                FileType.IMAGE_DIR -> LimboSettingsManager.getImagesDir(context)
                else -> LimboSettingsManager.getLastDir(context)
            }

        private fun getASFFileManagerRequestCode(requestCode: Int): Int =
            when (requestCode) {
                Config.OPEN_IMAGE_FILE_REQUEST_CODE -> Config.OPEN_IMAGE_FILE_ASF_REQUEST_CODE
                Config.OPEN_IMAGE_DIR_REQUEST_CODE -> Config.OPEN_IMAGE_DIR_ASF_REQUEST_CODE
                Config.OPEN_SHARED_DIR_REQUEST_CODE -> Config.OPEN_SHARED_DIR_ASF_REQUEST_CODE
                Config.OPEN_EXPORT_DIR_REQUEST_CODE -> Config.OPEN_EXPORT_DIR_ASF_REQUEST_CODE
                Config.OPEN_IMPORT_FILE_REQUEST_CODE -> Config.OPEN_IMPORT_FILE_ASF_REQUEST_CODE
                Config.OPEN_IMPORT_BIOS_FILE_REQUEST_CODE -> Config.OPEN_IMPORT_BIOS_FILE_ASF_REQUEST_CODE
                Config.OPEN_LOG_FILE_DIR_REQUEST_CODE -> Config.OPEN_LOG_FILE_DIR_ASF_REQUEST_CODE
                else -> requestCode
            }

        @JvmStatic
        fun promptLegacyStorageAccess(
            activity: Activity,
            fileType: FileType,
            requestCode: Int,
            lastDir: String?,
        ) {
            try {
                val filterExt = getFileExt(fileType)
                val intent = getFileManIntent(activity)
                val bundle = Bundle()
                if (lastDir != null && !lastDir.startsWith("content://")) {
                    bundle.putString("lastDir", lastDir)
                }
                bundle.putSerializable("fileType", fileType)
                bundle.putSerializable("filterExt", filterExt)
                intent.putExtras(bundle)
                activity.startActivityForResult(intent, requestCode)
            } catch (ex: Exception) {
                Log.e(TAG, "Error while starting Filemanager: ${ex.message}")
            }
        }

        @JvmStatic
        fun getFileManIntent(activity: Activity): Intent =
            Intent(activity, LimboFileManager::class.java)

        @JvmStatic
        fun promptOpenFileASF(
            context: Activity,
            fileType: FileType,
            requestCode: Int,
            lastDir: String?,
        ) {
            val intent = if (isFileTypeDirectory(fileType)) {
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            } else {
                Intent(Intent.ACTION_OPEN_DOCUMENT)
            }

            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)

            if (!isFileTypeDirectory(fileType)) {
                val fileMimeTypes = getFileMimeTypes(fileType)
                if (fileMimeTypes != null) {
                    for (fileMimeType in fileMimeTypes) {
                        intent.type = fileMimeType
                    }
                }
            }

            if (lastDir != null && lastDir.startsWith("content://")) {
                val uri = Uri.parse(lastDir)
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
            }

            context.startActivityForResult(intent, requestCode)
        }

        private fun isFileTypeDirectory(fileType: FileType?): Boolean =
            fileType == FileType.SHARED_DIR ||
                fileType == FileType.EXPORT_DIR ||
                fileType == FileType.IMAGE_DIR ||
                fileType == FileType.LOG_DIR

        private fun getFileMimeTypes(fileType: FileType): Array<String>? =
            if (fileType == FileType.IMPORT_FILE) {
                arrayOf(MimeTypeMap.getSingleton().getMimeTypeFromExtension("csv") ?: "text/csv")
            } else {
                arrayOf("*/*")
            }

        private fun getFileExt(fileType: FileType): HashMap<String, String> {
            val filterExt = HashMap<String, String>()
            if (fileType == FileType.IMPORT_FILE) {
                filterExt["csv"] = "csv"
            }
            return filterExt
        }

        @JvmStatic
        fun getMimeType(url: String): String? {
            val extension = MimeTypeMap.getFileExtensionFromUrl(url)
            return if (extension != null) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            } else {
                null
            }
        }
    }
}
