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

import android.app.Activity
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.machine.Machine
import com.max2idea.android.limbo.machine.Machine.FileType
import com.max2idea.android.limbo.machine.MachineAction
import com.max2idea.android.limbo.machine.MachineController
import com.max2idea.android.limbo.machine.MachineFilePaths
import com.max2idea.android.limbo.machine.MachineProperty
import com.max2idea.android.limbo.ui.SpinnerAdapter
import java.util.Observable
import java.util.Observer

/**
 * Dialog for changing removable drives while a VM is running.
 */
class DrivesDialogBox(
    private val activity: Activity,
    theme: Int,
    private val currMachine: Machine,
) : Dialog(activity, theme), Observer {

    @JvmField var mCD: Spinner? = null
    @JvmField var mFDA: Spinner? = null
    @JvmField var mSD: Spinner? = null
    @JvmField var mFDB: Spinner? = null
    @JvmField var mCDLayout: LinearLayout? = null
    @JvmField var mFDALayout: LinearLayout? = null
    @JvmField var mFDBLayout: LinearLayout? = null
    @JvmField var mSDLayout: LinearLayout? = null
    @JvmField var fileType: FileType? = null

    private var viewListener: ViewListener? = null

    init {
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
        )
        setContentView(R.layout.dev_dialog)
        setTitle(R.string.RemovableDrives)
        getWidgets()
        initUI()
        setupController()
        setOnDismissListener {
            setViewListener(null)
            MachineController.getInstance().machine?.deleteObserver(this)
        }
        MachineController.getInstance().machine?.addObserver(this)
    }

    private fun setupController() {
        setViewListener(LimboApplication.getViewListener())
    }

    fun setViewListener(viewListener: ViewListener?) {
        this.viewListener = viewListener
    }

    override fun onBackPressed() {
        dismiss()
    }

    private fun getWidgets() {
        mCD = findViewById(R.id.cdromimgval)
        mCDLayout = findViewById(R.id.cdromimgl)
        mFDA = findViewById(R.id.floppyimgval)
        mFDALayout = findViewById(R.id.floppyimgl)
        mFDB = findViewById(R.id.floppybimgval)
        mFDBLayout = findViewById(R.id.floppybimgl)
        mSD = findViewById(R.id.sdimgval)
        mSDLayout = findViewById(R.id.sdimgl)
    }

    private fun setupListeners() {
        setupListener(mCD, MachineProperty.CDROM, FileType.CDROM)
        setupListener(mFDA, MachineProperty.FDA, FileType.FDA)
        setupListener(mFDB, MachineProperty.FDB, FileType.FDB)
        setupListener(mSD, MachineProperty.SD, FileType.SD)
    }

    private fun setupListener(
        spinner: Spinner?,
        machineDrive: MachineProperty,
        fileType: FileType,
    ) {
        spinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>?,
                selectedItemView: View?,
                position: Int,
                id: Long,
            ) {
                setDriveValue(spinner, position, machineDrive, fileType)
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) = Unit
        }
    }

    private fun setDriveValue(
        spinner: Spinner,
        position: Int,
        driveLabel: MachineProperty,
        filetype: FileType,
    ) {
        val diskValue = (spinner.adapter as ArrayAdapter<*>).getItem(position) as String
        when {
            position == 0 -> notifyFieldChange(
                MachineProperty.REMOVABLE_DRIVE,
                arrayOf<Any>(driveLabel, ""),
            )
            position == 1 -> {
                fileType = filetype
                LimboFileManager.browse(activity, filetype, Config.OPEN_IMAGE_FILE_REQUEST_CODE)
                spinner.setSelection(0)
            }
            position > 1 -> notifyFieldChange(
                MachineProperty.REMOVABLE_DRIVE,
                arrayOf<Any>(driveLabel, diskValue),
            )
        }
    }

    fun populateDiskAdapter(
        spinner: Spinner?,
        fileType: FileType,
        createOption: Boolean,
        value: String?,
    ) {
        Thread {
            val oldHDs = MachineFilePaths.getRecentFilePaths(fileType)
            val arraySpinner = ArrayList<String>().apply {
                add("None")
                if (createOption) {
                    add("New")
                }
                add(activity.getString(R.string.open))
            }
            val index = arraySpinner.size
            arraySpinner.apply {
                for (file in oldHDs) {
                    add(file)
                }
            }
            Handler(Looper.getMainLooper()).post {
                if (spinner == null) {
                    return@post
                }
                val adapter = SpinnerAdapter(activity, R.layout.custom_spinner_item, arraySpinner, index)
                adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
                spinner.adapter = adapter
                spinner.invalidate()
                setDiskValue(spinner, value)
            }
        }.start()
    }

    private fun initUI() {
        Thread {
            if (currMachine.isEnableCDROM()) {
                populateDiskAdapter(mCD, FileType.CDROM, false, currMachine.getCdImagePath())
            } else {
                mCDLayout?.visibility = View.GONE
            }
            if (currMachine.isEnableFDA()) {
                populateDiskAdapter(mFDA, FileType.FDA, false, currMachine.getFdaImagePath())
            } else {
                mFDALayout?.visibility = View.GONE
            }
            if (currMachine.isEnableFDB()) {
                populateDiskAdapter(mFDB, FileType.FDB, false, currMachine.getFdbImagePath())
            } else {
                mFDBLayout?.visibility = View.GONE
            }
            if (currMachine.isEnableSD()) {
                populateDiskAdapter(mSD, FileType.SD, false, currMachine.getSdImagePath())
            } else {
                mSDLayout?.visibility = View.GONE
            }
            Handler(Looper.getMainLooper()).postDelayed(
                { setupListeners() },
                500,
            )
        }.apply {
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    fun setDriveAttr(fileType: FileType?, file: String?) {
        if (fileType == null || file.isNullOrBlank()) {
            return
        }
        notifyAction(MachineAction.INSERT_FAV, arrayOf<Any>(fileType, file))
        when (fileType) {
            FileType.CDROM -> {
                notifyFieldChange(MachineProperty.CDROM, file)
                setSpinnerValue(mCD, file)
            }
            FileType.SD -> {
                notifyFieldChange(MachineProperty.SD, file)
                setSpinnerValue(mSD, file)
            }
            FileType.FDA -> {
                notifyFieldChange(MachineProperty.FDA, file)
                setSpinnerValue(mFDA, file)
            }
            FileType.FDB -> {
                notifyFieldChange(MachineProperty.FDB, file)
                setSpinnerValue(mFDB, file)
            }
            else -> Unit
        }
    }

    private fun setSpinnerValue(spinner: Spinner?, value: String) {
        Handler(Looper.getMainLooper()).post {
            if (spinner == null) {
                return@post
            }
            if (SpinnerAdapter.getItemPosition(spinner, value) < 0) {
                SpinnerAdapter.addItem(spinner, value)
            }
            setDiskValue(spinner, value)
            if (spinner.selectedItemPosition == 1) {
                spinner.setSelection(0)
            }
        }
    }

    private fun setDiskValue(spinner: Spinner?, value: String?) {
        activity.runOnUiThread {
            if (spinner != null) {
                val pos = SpinnerAdapter.getItemPosition(spinner, value ?: "")
                if (pos > 1) {
                    spinner.setSelection(pos)
                } else {
                    spinner.setSelection(0)
                }
            }
        }
    }

    fun notifyFieldChange(property: MachineProperty, value: Any?) {
        viewListener?.onFieldChange(property, value)
    }

    fun notifyAction(action: MachineAction, value: Any?) {
        viewListener?.onAction(action, value)
    }

    override fun update(observable: Observable?, value: Any?) {
        val params = value as? Array<*> ?: return
        val property = params[0] as? MachineProperty ?: return
        val driveValue = params.getOrNull(1)
        if (driveValue != null) {
            return
        }
        when (property) {
            MachineProperty.CDROM -> setDiskValue(mCD, "")
            MachineProperty.FDA -> setDiskValue(mFDA, "")
            MachineProperty.FDB -> setDiskValue(mFDB, "")
            MachineProperty.SD -> setDiskValue(mSD, "")
            else -> Unit
        }
    }
}
