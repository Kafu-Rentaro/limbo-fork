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

import com.max2idea.android.limbo.main.ViewListener
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Routes user actions and field changes to the backend machine controller, keeping Activities from
 * writing directly to the model.
 */
object Dispatcher : ViewListener {
    private val dispatcher: ExecutorService = Executors.newFixedThreadPool(1)

    @JvmStatic
    @Synchronized
    fun getInstance(): Dispatcher = this

    override fun onFieldChange(property: MachineProperty, value: Any?) {
        dispatcher.submit {
            requestFieldChange(property, value)
        }
    }

    private fun requestFieldChange(property: MachineProperty, value: Any?) {
        val machine = getMachine() ?: return
        when (property) {
            MachineProperty.DRIVE_ENABLED -> setDriveEnabled(value)
            MachineProperty.NON_REMOVABLE_DRIVE,
            MachineProperty.REMOVABLE_DRIVE -> setDrive(value)
            MachineProperty.MEDIA_INTERFACE -> setDriveMediaInterface(value)
            MachineProperty.ARCH -> machine.setArch(convertString(property, value))
            MachineProperty.SOUNDCARD -> machine.setSoundCard(convertString(property, value))
            MachineProperty.CPU -> machine.setCpu(convertString(property, value))
            MachineProperty.MEMORY -> machine.setMemory(convertInt(property, value))
            MachineProperty.CPUNUM -> machine.setCpuNum(convertInt(property, value))
            MachineProperty.KERNEL -> machine.setKernel(convertString(property, value))
            MachineProperty.INITRD -> machine.setInitRd(convertString(property, value))
            MachineProperty.APPEND -> machine.setAppend(convertString(property, value))
            MachineProperty.BOOT_CONFIG -> machine.setBootDevice(convertString(property, value))
            MachineProperty.NETCONFIG -> machine.setNetwork(convertString(property, value))
            MachineProperty.NICCONFIG -> machine.setNetworkCard(convertString(property, value))
            MachineProperty.DISABLE_HPET -> machine.setDisableHPET(if (convertBoolean(property, value)) 1 else 0)
            MachineProperty.DISABLE_TSC -> machine.setDisableTSC(if (convertBoolean(property, value)) 1 else 0)
            MachineProperty.VGA -> machine.setVga(convertString(property, value))
            MachineProperty.DISABLE_ACPI -> machine.setDisableACPI(if (convertBoolean(property, value)) 1 else 0)
            MachineProperty.DISABLE_FD_BOOT_CHK -> {
                machine.setDisableFdBootChk(if (convertBoolean(property, value)) 1 else 0)
            }
            MachineProperty.ENABLE_KVM -> machine.setEnableKVM(if (convertBoolean(property, value)) 1 else 0)
            MachineProperty.ENABLE_MTTCG -> machine.setEnableMTTCG(if (convertBoolean(property, value)) 1 else 0)
            MachineProperty.HOSTFWD -> machine.setHostFwd(convertString(property, value))
            MachineProperty.MOUSE -> changeMouse(convertString(property, value))
            MachineProperty.UI -> changeUi(convertString(property, value))
            MachineProperty.KEYBOARD -> machine.setKeyboard(convertString(property, value))
            MachineProperty.MACHINETYPE -> machine.setMachineType(convertString(property, value))
            MachineProperty.PAUSED -> machine.setPaused(convertInt(property, value))
            MachineProperty.EXTRA_PARAMS -> machine.setExtraParams(convertString(property, value))
            else -> throw RuntimeException("Unmapped UI field: $property")
        }
    }

    private fun setDriveValue(diskFileType: MachineProperty, drivePath: String?) {
        val machine = getMachine() ?: return
        when (diskFileType) {
            MachineProperty.HDA -> {
                machine.setHdaImagePath(drivePath)
                MachineFilePaths.insertRecentFilePath(Machine.FileType.HDA, drivePath)
            }
            MachineProperty.HDB -> {
                machine.setHdbImagePath(drivePath)
                MachineFilePaths.insertRecentFilePath(Machine.FileType.HDB, drivePath)
            }
            MachineProperty.HDC -> {
                machine.setHdcImagePath(drivePath)
                MachineFilePaths.insertRecentFilePath(Machine.FileType.HDC, drivePath)
            }
            MachineProperty.HDD -> {
                machine.setHddImagePath(drivePath)
                MachineFilePaths.insertRecentFilePath(Machine.FileType.HDD, drivePath)
            }
            MachineProperty.SHARED_FOLDER -> {
                machine.setSharedFolderPath(drivePath)
                MachineFilePaths.insertRecentFilePath(Machine.FileType.SHARED_DIR, drivePath)
            }
            MachineProperty.CDROM -> {
                MachineController.getInstance().changeRemovableDevice(diskFileType, drivePath)
                MachineFilePaths.insertRecentFilePath(Machine.FileType.CDROM, drivePath)
            }
            MachineProperty.FDA -> {
                MachineFilePaths.insertRecentFilePath(Machine.FileType.FDA, drivePath)
                MachineController.getInstance().changeRemovableDevice(diskFileType, drivePath)
            }
            MachineProperty.FDB -> {
                MachineController.getInstance().changeRemovableDevice(diskFileType, drivePath)
                MachineFilePaths.insertRecentFilePath(Machine.FileType.FDB, drivePath)
            }
            MachineProperty.SD -> {
                MachineController.getInstance().changeRemovableDevice(diskFileType, drivePath)
                MachineFilePaths.insertRecentFilePath(Machine.FileType.SD, drivePath)
            }
            else -> Unit
        }
    }

    private fun changeMouse(mouseCfg: String) {
        val mouseDB = mouseCfg.split(" ")[0]
        getMachine()?.setMouse(mouseDB)
    }

    private fun setMachineEnableDevice(machineProperty: MachineProperty, isChecked: Boolean) {
        val machine = getMachine() ?: return
        when (machineProperty) {
            MachineProperty.CDROM -> machine.setEnableCDROM(isChecked)
            MachineProperty.FDA -> machine.setEnableFDA(isChecked)
            MachineProperty.FDB -> machine.setEnableFDB(isChecked)
            MachineProperty.SD -> machine.setEnableSD(isChecked)
            else -> Unit
        }
    }

    private fun changeUi(ui: String) {
        when (ui) {
            "VNC" -> getMachine()?.setEnableVNC(1)
            "SDL" -> getMachine()?.setEnableVNC(0)
        }
    }

    private fun setDriveMediaInterface(value: Any?) {
        val params = value as Array<*>
        val driveName = params[0] as MachineProperty
        val driveInterface = params[1] as String
        val machine = getMachine() ?: return
        when (driveName) {
            MachineProperty.HDA -> machine.setHdaInterface(driveInterface)
            MachineProperty.HDB -> machine.setHdbInterface(driveInterface)
            MachineProperty.HDC -> machine.setHdcInterface(driveInterface)
            MachineProperty.HDD -> machine.setHddInterface(driveInterface)
            MachineProperty.CDROM -> machine.setCdInterface(driveInterface)
            else -> Unit
        }
    }

    private fun setDriveEnabled(value: Any?) {
        val params = value as Array<*>
        val machineDriveName = params[0] as MachineProperty
        val checked = params[1] as Boolean
        setMachineEnableDevice(machineDriveName, checked)
        if (checked) {
            setDriveValue(machineDriveName, "")
        } else {
            setDriveValue(machineDriveName, null)
        }
    }

    private fun setDrive(value: Any?) {
        val params = value as Array<*>
        val machineDriveName = params[0] as MachineProperty
        val diskFileValue = params[1] as String
        if (diskFileValue == "None" && isDriveEnabled(machineDriveName)) {
            setDriveValue(machineDriveName, "")
        } else if (diskFileValue == "None" || !isDriveEnabled(machineDriveName)) {
            setDriveValue(machineDriveName, null)
        } else if (isDriveEnabled(machineDriveName)) {
            setDriveValue(machineDriveName, diskFileValue)
        }
    }

    private fun isDriveEnabled(property: MachineProperty): Boolean {
        val machine = getMachine() ?: return false
        return when (property) {
            MachineProperty.CDROM -> machine.isEnableCDROM()
            MachineProperty.FDA -> machine.isEnableFDA()
            MachineProperty.FDB -> machine.isEnableFDB()
            MachineProperty.SD -> machine.isEnableSD()
            else -> true
        }
    }

    override fun onAction(stopVm: MachineAction, value: Any?) {
        dispatcher.submit {
            requestAction(stopVm, value)
        }
    }

    private fun requestAction(action: MachineAction, value: Any?) {
        when (action) {
            MachineAction.DELETE_VM -> deleteVM(value as Machine)
            MachineAction.CREATE_VM -> createVM(convertString(action, value))
            MachineAction.STOP_VM -> MachineController.getInstance().stopvm()
            MachineAction.START_VM -> MachineController.getInstance().startvm()
            MachineAction.PAUSE_VM -> MachineController.getInstance().pausevm()
            MachineAction.CONTINUE_VM -> MachineController.getInstance().continueVM(convertInt(action, value))
            MachineAction.RESET_VM -> MachineController.getInstance().restartvm()
            MachineAction.LOAD_VM -> MachineController.getInstance().setStoredMachine(value as String)
            MachineAction.IMPORT_VMS -> MachineController.getInstance().importMachines(convertString(action, value))
            MachineAction.SET_SDL_REFRESH_RATE -> changeSDLRefreshRate(value)
            MachineAction.SEND_MOUSE_EVENT -> sendMouseEvent(value)
            MachineAction.INSERT_FAV -> addDriveToList(value)
            MachineAction.UPDATE_NOTIFICATION -> {
                val name = MachineController.getInstance().machine?.getName() ?: return
                MachineService.getService()?.updateServiceNotification("$name: ${value as String}")
            }
            MachineAction.DISPLAY_CHANGED -> displayChanged(value)
            MachineAction.ENABLE_AAUDIO -> MachineController.getInstance().enableAaudio(convertInt(action, value))
            MachineAction.FULLSCREEN -> MachineController.getInstance().setFullscreen()
            MachineAction.IGNORE_BREAKPOINT_INVALIDATION -> {
                MachineController.getInstance().ignoreBreakpointInvalidation(convertBoolean(action, value))
            }
        }
    }

    private fun changeSDLRefreshRate(value: Any?) {
        val params = value as Array<*>
        val ms = (params[0] as Number).toInt()
        val idle = params[1] as Boolean
        MachineController.getInstance().setSdlRefreshRate(ms, idle)
    }

    private fun displayChanged(value: Any?) {
        val params = value as Array<*>
        MachineController.getInstance().updateDisplay(
            (params[0] as Number).toInt(),
            (params[1] as Number).toInt(),
            (params[2] as Number).toInt(),
        )
    }

    private fun addDriveToList(value: Any?) {
        val params = value as Array<*>
        val fileType = params[0] as Machine.FileType
        val filePath = params[1] as String
        MachineFilePaths.insertRecentFilePath(fileType, filePath)
    }

    private fun deleteVM(machine: Machine): Boolean =
        MachineController.getInstance().deleteMachine(machine)

    private fun createVM(machineName: String?): Boolean =
        MachineController.getInstance().createVM(machineName)

    private fun sendMouseEvent(value: Any?) {
        val params = value as Array<*>
        MachineController.getInstance().sendMouseEvent(
            (params[0] as Number).toInt(),
            (params[1] as Number).toInt(),
            (params[2] as Number).toInt(),
            (params[3] as Number).toFloat(),
            (params[4] as Number).toFloat(),
        )
    }

    private fun getMachine(): Machine? = MachineController.getInstance().machine

    private fun convertString(property: MachineProperty, value: Any?): String =
        value as? String ?: throw RuntimeException("Unknown property value: $value for: $property")

    private fun convertString(action: MachineAction, value: Any?): String? =
        when (value) {
            is String -> value
            null -> null
            else -> throw RuntimeException("Unknown action value: $value for: $action")
        }

    private fun convertInt(property: MachineProperty, value: Any?): Int =
        when (value) {
            is String -> value.toInt()
            is Number -> value.toInt()
            else -> throw RuntimeException("Unknown property value: $value for: $property")
        }

    private fun convertInt(action: MachineAction, value: Any?): Int =
        when (value) {
            is String -> value.toInt()
            is Number -> value.toInt()
            else -> throw RuntimeException("Unknown action value: $value for: $action")
        }

    private fun convertBoolean(property: MachineProperty, value: Any?): Boolean =
        value as? Boolean ?: throw RuntimeException("Unknown property value: $value for: $property")

    private fun convertBoolean(action: MachineAction, value: Any?): Boolean =
        value as? Boolean ?: throw RuntimeException("Unknown action value: $value for: $action")
}
