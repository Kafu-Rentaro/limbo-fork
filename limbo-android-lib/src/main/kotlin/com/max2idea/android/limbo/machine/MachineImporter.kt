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
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.files.FileUtils
import com.max2idea.android.limbo.machine.Machine.FileType
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboFileManager
import java.io.IOException
import java.util.regex.Pattern

object MachineImporter {
    private const val TAG = "MachineImporter"
    private val csvSplitPattern: Pattern = Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")

    private fun getVMsFromFile(importFilePath: String): ArrayList<Machine> {
        val machines = ArrayList<Machine>()
        try {
            Log.d(TAG, "Import file: $importFilePath")
            val stream = FileUtils.getStreamFromFilePath(importFilePath)
            if (stream != null) {
                stream.bufferedReader().use { reader ->
                    val attrs = mutableMapOf<Int, String>()
                    val headerLine = reader.readLine() ?: return machines
                    headerLine.split(",").forEachIndexed { index, header ->
                        attrs[index] = header.replace("\"", "")
                    }

                    while (true) {
                        val line = reader.readLine() ?: break
                        val machineAttr = csvSplitPattern.split(line, -1)
                        if (machineAttr.isEmpty()) {
                            continue
                        }
                        val machine = Machine(machineAttr[0], false)
                        for (index in machineAttr.indices) {
                            val value = machineAttr[index]
                            if (value == "\"null\"") {
                                continue
                            }
                            applyMachineAttribute(machine, attrs[index], value)
                        }
                        Log.d(TAG, "Adding Machine: ${machine.getName()}")
                        machines.add(machine)
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            try {
                FileUtils.closeFileDescriptor(importFilePath)
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
        return machines
    }

    private fun applyMachineAttribute(machine: Machine, attr: String?, rawValue: String) {
        val value = rawValue.replace("\"", "")
        when (attr) {
            "MACHINE_NAME" -> machine.setName(value)
            "UI" -> if (value == "VNC") machine.setEnableVNC(1)
            "PAUSED" -> machine.setPaused(value.toInt())
            "ARCH" -> machine.setArch(value)
            "MACHINETYPE" -> machine.setMachineType(value)
            "CPU" -> machine.setCpu(value)
            "CPUNUM" -> machine.setCpuNum(value.toInt())
            "MEMORY" -> machine.setMemory(value.toInt())
            "HDA" -> machine.setHdaImagePath(value)
            "HDB" -> machine.setHdbImagePath(value)
            "HDC" -> machine.setHdcImagePath(value)
            "HDD" -> machine.setHddImagePath(value)
            "SHARED_FOLDER" -> machine.setSharedFolderPath(value)
            "SHARED_FOLDER_MODE" -> machine.setShared_folder_mode(value.toInt())
            "CDROM" -> machine.setCdImagePath(value)
            "FDA" -> machine.setFdaImagePath(value)
            "FDB" -> machine.setFdbImagePath(value)
            "SD" -> machine.setSdImagePath(value)
            "HDA_INTERFACE" -> machine.setHdaInterface(value)
            "HDB_INTERFACE" -> machine.setHdbInterface(value)
            "HDC_INTERFACE" -> machine.setHdcInterface(value)
            "HDD_INTERFACE" -> machine.setHddInterface(value)
            "CDROM_INTERFACE" -> machine.setCdInterface(value)
            "VGA" -> machine.setVga(value)
            "SOUNDCARD" -> machine.setSoundCard(value)
            "NETCONFIG" -> machine.setNetwork(value)
            "NICCONFIG" -> machine.setNetworkCard(value)
            "HOSTFWD" -> machine.setHostFwd(value)
            "GUESTFWD" -> machine.setGuestFwd(value)
            "DISABLE_ACPI" -> machine.setDisableACPI(value.toInt())
            "DISABLE_HPET" -> machine.setDisableHPET(value.toInt())
            "DISABLE_TSC" -> machine.setDisableTSC(value.toInt())
            "DISABLE_FD_BOOT_CHK" -> machine.setDisableFdBootChk(value.toInt())
            "BOOT_CONFIG" -> machine.setBootDevice(value)
            "KERNEL" -> machine.setKernel(value)
            "INITRD" -> machine.setInitRd(value)
            "APPEND" -> machine.setAppend(value)
            "EXTRA_PARAMS" -> machine.setExtraParams(value)
            "MOUSE" -> machine.setMouse(value)
            "KEYBOARD" -> machine.setKeyboard(value)
            "ENABLE_MTTCG" -> machine.setEnableMTTCG(value.toInt())
            "ENABLE_KVM" -> machine.setEnableKVM(value.toInt())
        }
    }

    @JvmStatic
    fun promptImportMachines(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.ImportMachines))
            .setView(createInstructionView(activity, R.string.importInstructions))
            .setPositiveButton(activity.getString(R.string.Ok)) { _, _ ->
                promptForImportDir(activity)
            }
            .setNegativeButton(activity.getString(R.string.Cancel), null)
            .show()
    }

    private fun promptForImportDir(activity: Activity) {
        Thread {
            LimboFileManager.browse(
                activity,
                FileType.IMPORT_FILE,
                Config.OPEN_IMPORT_FILE_REQUEST_CODE,
            )
        }.start()
    }

    @JvmStatic
    fun importMachines(importFilePath: String): ArrayList<Machine> {
        val machines = getVMsFromFile(importFilePath)
        for (machine in machines) {
            if (MachineOpenHelper.getInstance().getMachine(machine.getName()) != null) {
                MachineOpenHelper.getInstance().deleteMachine(machine)
            }
            MachineOpenHelper.getInstance().insertMachine(machine)
        }
        return machines
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
