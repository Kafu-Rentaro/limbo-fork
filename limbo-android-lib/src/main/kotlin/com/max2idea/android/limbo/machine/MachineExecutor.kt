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

/**
 * Emulation bridge base class. It can be extended for native emulators that support SDL.
 */
abstract class MachineExecutor(private val machineController: MachineController) {
    fun getMachine(): Machine? = machineController.machine

    protected fun onResolutionChanged(vmWidth: Int, vmHeight: Int) {
        machineController.onVMResolutionChanged(this, vmWidth, vmHeight)
    }

    abstract fun startService()

    // TODO: create int success code instead of string.
    abstract fun start(): String?

    abstract fun stopvm(restart: Int)

    abstract fun getSdlRefreshRate(idle: Boolean): Int

    abstract fun setSdlRefreshRate(refreshMs: Int, idle: Boolean)

    abstract fun sendMouseEvent(button: Int, action: Int, relative: Int, x: Float, y: Float)

    abstract fun saveVM(): String?

    abstract fun continueVM()

    abstract fun getSaveVMStatus(): MachineController.MachineStatus?

    abstract fun enableAaudio(value: Int)

    abstract fun changeRemovableDevice(drive: MachineProperty?, diskValue: String?): Boolean

    abstract fun getDeviceName(driveProperty: MachineProperty?): String?

    abstract fun updateDisplay(width: Int, height: Int, orientation: Int)

    abstract fun setFullscreen()

    abstract fun ignoreBreakpointInvalidation(value: Int)
}
