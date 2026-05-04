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
@file:Suppress("DEPRECATION")

package com.max2idea.android.limbo.machine

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.jni.MachineExecutorFactory
import com.max2idea.android.limbo.main.LimboApplication
import java.util.Observer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Coordinates the QEMU bridge and the selected virtual machine state.
 */
class MachineController private constructor() {
    private val saveVmStatusExecutor: ExecutorService = Executors.newFixedThreadPool(1)
    private val machineExecutor: MachineExecutor =
        requireNotNull(
            MachineExecutorFactory.createMachineExecutor(this, MachineExecutorFactory.MachineExecutorType.QEMU),
        )
    private val onMachineStatusChangeListeners = HashSet<OnMachineStatusChangeListener>()
    private val onEventListeners = HashSet<OnEventListener>()
    private val serviceClass: Class<MachineService> = MachineService::class.java
    private val machineDatabase: IMachineDatabase = MachineOpenHelper.getInstance()

    private var saveVmStatusTimerQuit = false
    private var promptedPausedVM = false

    var machine: Machine? = null
        private set

    val isPaused: Boolean
        get() = machineExecutor.getMachine()?.getPaused() == 1

    fun getCurrStatus(): MachineStatus {
        val currentMachine = machine ?: return MachineStatus.Stopped
        return when {
            currentMachine.getPaused() == 1 -> MachineStatus.Paused
            MachineService.getService() == null -> MachineStatus.Ready
            MachineService.getService()?.limboThread != null -> MachineStatus.Running
            else -> MachineStatus.Stopped
        }
    }

    fun continueVM(delay: Int) {
        Thread {
            if (delay > 0) {
                try {
                    Thread.sleep(delay.toLong())
                } catch (ex: InterruptedException) {
                    ex.printStackTrace()
                }
            }
            machineExecutor.continueVM()
            notifyEventListeners(Event.MachineContinued, null)
        }.start()
    }

    fun addOnStatusChangeListener(listener: OnMachineStatusChangeListener) {
        onMachineStatusChangeListeners.add(listener)
    }

    fun removeOnStatusChangeListener(listener: OnMachineStatusChangeListener) {
        onMachineStatusChangeListeners.remove(listener)
    }

    fun removeOnStatusChangeListeners() {
        onMachineStatusChangeListeners.clear()
    }

    fun addOnEventListener(listener: OnEventListener) {
        onEventListeners.add(listener)
    }

    fun removeOnEventListener(listener: OnEventListener) {
        onEventListeners.remove(listener)
    }

    fun removeOnEventListeners() {
        onEventListeners.clear()
    }

    fun stopvm() {
        machineExecutor.stopvm(0)
    }

    private fun notifyMachineStatusChangeListeners(machine: Machine?, status: MachineStatus?, value: Any?) {
        for (listener in onMachineStatusChangeListeners) {
            listener.onMachineStatusChanged(machine, status, value)
        }
    }

    private fun notifyEventListeners(status: Event, value: Any?) {
        for (listener in onEventListeners) {
            listener.onEvent(machine, status, value)
        }
    }

    fun startvm() {
        machineExecutor.startService()
    }

    fun restartvm() {
        Thread {
            Log.d(TAG, "Restarting the VM...")
            machineExecutor.stopvm(1)
        }.start()
    }

    private fun checkSaveVMStatus(): MachineStatus? {
        val saveVmStatus = machineExecutor.getSaveVMStatus()
        if (saveVmStatus == MachineStatus.SaveCompleted) {
            saveStateVMDB()
            if (promptedPausedVM) {
                stopSaveVmStatusTimer()
            }
            machineExecutor.getMachine()?.setPaused(1)
        }
        Handler(Looper.getMainLooper()).postDelayed(
            {
                when (saveVmStatus) {
                    MachineStatus.SaveCompleted -> {
                        getInstance().promptedPausedVM = true
                        notifyMachineStatusChangeListeners(machine, saveVmStatus, null)
                    }
                    MachineStatus.SaveFailed -> notifyMachineStatusChangeListeners(machine, saveVmStatus, null)
                    else -> Unit
                }
            },
            1000,
        )
        return saveVmStatus
    }

    private fun checkSaveStatus() {
        while (!saveVmStatusTimerQuit) {
            val status = checkSaveVMStatus()
            Log.d(TAG, "State Status: $status")
            if (
                status == MachineStatus.Unknown ||
                status == MachineStatus.SaveCompleted ||
                status == MachineStatus.SaveFailed
            ) {
                Log.d(TAG, "Saving state is done: $status")
                stopSaveVmStatusTimer()
                return
            }
            try {
                Thread.sleep(1000)
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
            }
        }
        Log.d(TAG, "Save state complete")
    }

    private fun stopSaveVmStatusTimer() {
        saveVmStatusTimerQuit = true
    }

    private fun saveVmStatusTimerLoop() {
        while (!saveVmStatusTimerQuit) {
            checkSaveStatus()
            try {
                Thread.sleep(1000)
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
            }
        }
    }

    private fun execSaveVmStatusTimer() {
        saveVmStatusExecutor.submit {
            startSaveVmStatusTimer()
        }
    }

    private fun startSaveVmStatusTimer() {
        stopSaveVmStatusTimer()
        saveVmStatusTimerQuit = false
        try {
            saveVmStatusTimerLoop()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun pausevm() {
        Thread {
            val error = machineExecutor.saveVM()
            if (error != null) {
                getInstance().notifyMachineStatusChangeListeners(machine, MachineStatus.SaveFailed, error)
            }
            getInstance().execSaveVmStatusTimer()
        }.start()
    }

    fun getMachineSaveDir(): String =
        LimboApplication.getMachineDir() + machineExecutor.getMachine()?.getName()

    fun changeRemovableDevice(property: MachineProperty?, value: String?) {
        var diskValue = value
        if (isRunning()) {
            val result = machineExecutor.changeRemovableDevice(property, diskValue)
            if (!result) {
                diskValue = null
            }
        }
        when (property) {
            MachineProperty.CDROM -> machine?.setCdImagePath(diskValue)
            MachineProperty.FDA -> machine?.setFdaImagePath(diskValue)
            MachineProperty.FDB -> machine?.setFdbImagePath(diskValue)
            MachineProperty.SHARED_FOLDER -> machine?.setSharedFolderPath(diskValue)
            else -> Unit
        }
    }

    fun sendMouseEvent(button: Int, action: Int, relative: Int, x: Float, y: Float) {
        machineExecutor.sendMouseEvent(button, action, relative, x, y)
    }

    fun isRunning(): Boolean = getCurrStatus() == MachineStatus.Running

    fun getSdlRefreshRate(idle: Boolean): Int =
        machineExecutor.getSdlRefreshRate(idle)

    fun setSdlRefreshRate(refreshMs: Int, idle: Boolean) {
        machineExecutor.setSdlRefreshRate(refreshMs, idle)
    }

    fun getMachineName(): String? = machineExecutor.getMachine()?.getName()

    fun isVNCEnabled(): Boolean =
        machineExecutor.getMachine()?.getEnableVNC() == 1

    fun start(): String? {
        setPaused(0, 5000)
        return machineExecutor.start()
    }

    private fun setPaused(value: Int, delay: Int) {
        Thread {
            try {
                Thread.sleep(delay.toLong())
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
            }
            machine?.setPaused(value)
        }.start()
    }

    fun setMachine(machine: Machine?) {
        if (this.machine != machine) {
            this.machine?.deleteObservers()
            this.machine = machine
            this.machine?.addObserver(machineDatabase as Observer)
            notifyEventListeners(Event.MachineLoaded, machine)
        }
    }

    fun setStoredMachine(value: String?) {
        setMachine(machineDatabase.getMachine(value))
    }

    fun saveStateVMDB() {
        machineDatabase.updateMachineFieldAsync(getInstance().machine, MachineProperty.PAUSED, "1")
    }

    fun createVM(machineName: String?): Boolean {
        if (machineName == null) {
            return false
        }
        if (machineDatabase.getMachine(machineName) != null) {
            notifyEventListeners(Event.MachineCreateFailed, R.string.VMNameExistsChooseAnother)
            return false
        }
        val machine = Machine(machineName, true)
        notifyEventListeners(Event.MachineCreated, machineName)
        getInstance().setMachine(machine)
        machineDatabase.insertMachine(this.machine)
        notifyMachineStatusChangeListeners(machine, MachineStatus.Ready, null)
        return true
    }

    fun importMachines(importFilePath: String?) {
        if (importFilePath == null) {
            return
        }
        setMachine(null)
        val machines = MachineImporter.importMachines(importFilePath)
        for (machine in machines) {
            MachineFilePaths.insertRecentFilePath(Machine.FileType.CDROM, machine.getCdImagePath())
            MachineFilePaths.insertRecentFilePath(Machine.FileType.HDA, machine.getHdaImagePath())
            MachineFilePaths.insertRecentFilePath(Machine.FileType.HDB, machine.getHdbImagePath())
            MachineFilePaths.insertRecentFilePath(Machine.FileType.HDC, machine.getHdcImagePath())
            MachineFilePaths.insertRecentFilePath(Machine.FileType.HDD, machine.getHddImagePath())
            MachineFilePaths.insertRecentFilePath(Machine.FileType.SHARED_DIR, machine.getSharedFolderPath())
            MachineFilePaths.insertRecentFilePath(Machine.FileType.FDA, machine.getFdaImagePath())
            MachineFilePaths.insertRecentFilePath(Machine.FileType.FDB, machine.getFdbImagePath())
            MachineFilePaths.insertRecentFilePath(Machine.FileType.SD, machine.getSdImagePath())
            MachineFilePaths.insertRecentFilePath(Machine.FileType.KERNEL, machine.getKernel())
            MachineFilePaths.insertRecentFilePath(Machine.FileType.INITRD, machine.getInitRd())
        }
        notifyEventListeners(Event.MachinesImported, machines)
    }

    fun getStoredMachines(): ArrayList<String> = machineDatabase.getMachineNames()

    fun deleteMachine(machine: Machine?): Boolean = machineDatabase.deleteMachine(machine)

    fun getServiceClass(): Class<*> = serviceClass

    fun onServiceStarted() {
        notifyMachineStatusChangeListeners(machine, getCurrStatus(), null)
    }

    fun updateDisplay(width: Int, height: Int, orientation: Int) {
        machineExecutor.updateDisplay(width, height, orientation)
    }

    fun onVMResolutionChanged(machineExecutor: MachineExecutor, vmWidth: Int, vmHeight: Int) {
        if (machineExecutor == this.machineExecutor) {
            notifyEventListeners(Event.MachineResolutionChanged, arrayOf(vmWidth, vmHeight))
        }
    }

    fun setFullscreen() {
        machineExecutor.setFullscreen()
        notifyEventListeners(Event.MachineFullscreen, null)
    }

    fun enableAaudio(value: Int) {
        machineExecutor.enableAaudio(value)
    }

    fun ignoreBreakpointInvalidation(value: Boolean) {
        machineExecutor.ignoreBreakpointInvalidation(if (value) 1 else 0)
    }

    enum class MachineStatus {
        Ready,
        Stopped,
        Saving,
        Paused,
        SaveCompleted,
        SaveFailed,
        Unknown,
        Running,
    }

    enum class Event {
        MachineCreated,
        MachineCreateFailed,
        MachineLoaded,
        MachineResolutionChanged,
        MachineContinued,
        MachineFullscreen,
        MachinesImported,
    }

    interface OnMachineStatusChangeListener {
        fun onMachineStatusChanged(machine: Machine?, status: MachineStatus?, value: Any?)
    }

    interface OnEventListener {
        fun onEvent(machine: Machine?, event: Event, value: Any?)
    }

    companion object {
        private const val TAG = "MachineController"

        private var mSingleton: MachineController? = null

        @JvmStatic
        fun getInstance(): MachineController {
            if (mSingleton == null) {
                mSingleton = MachineController()
            }
            return requireNotNull(mSingleton)
        }
    }
}
