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
@file:Suppress("FunctionName")

package com.max2idea.android.limbo.jni

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.files.FileUtils
import com.max2idea.android.limbo.machine.GraphicsCapabilities
import com.max2idea.android.limbo.machine.Machine
import com.max2idea.android.limbo.machine.MachineAction
import com.max2idea.android.limbo.machine.MachineController
import com.max2idea.android.limbo.machine.MachineExecutor
import com.max2idea.android.limbo.machine.MachineProperty
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboApplication
import com.max2idea.android.limbo.main.LimboSDLActivity
import com.max2idea.android.limbo.main.LimboSettingsManager
import com.max2idea.android.limbo.qmp.QmpClient
import com.max2idea.android.limbo.toast.ToastUtils
import java.io.File
import org.json.JSONException
import org.json.JSONObject

/**
 * Starts and stops QEMU, and bridges file descriptors plus SDL input events to native code.
 */
class VMExecutor(machineController: MachineController) : MachineExecutor(machineController) {
    init {
        instance = this
    }

    private val currentMachine: Machine
        get() = requireNotNull(getMachine()) { "No machine selected" }

    private external fun start(
        storage_dir: String?,
        base_dir: String,
        lib_filename: String,
        lib_path: String,
        sdl_scale_hint: Int,
        params: Array<Any?>,
    ): String?

    private external fun stop(restart: Int): String?

    external fun setSDLRefreshRateDefault(value: Int)

    external fun setSDLRefreshRateIdle(value: Int)

    external fun getSDLRefreshRateDefault(): Int

    external fun getSDLRefreshRateIdle(): Int

    external fun nativeIgnoreBreakpointInvalidate(value: Int)

    external fun nativeMouseEvent(button: Int, action: Int, relative: Int, x: Int, y: Int)

    external fun nativeMouseBounds(xmin: Int, xmax: Int, ymin: Int, ymax: Int)

    external fun nativeFullscreen()

    external fun nativeRefreshScreen(value: Int)

    external fun nativeEnableAaudio(value: Int, aaudioLibName: String, aaudioLibPath: String)

    fun printParams(params: Array<Any?>) {
        Log.d(TAG, "Params:")
        params.forEachIndexed { index, param ->
            Log.d(TAG, "$index: $param")
        }
    }

    private fun getSoundCard(): String? {
        val soundCard = currentMachine.getSoundCard()
        return if (Config.enableSDLSound && soundCard != null && soundCard.lowercase() != "none") {
            soundCard
        } else {
            null
        }
    }

    private fun getEffectiveGuestArch(): Config.Arch {
        if (LimboApplication.arch != Config.Arch.x86 && LimboApplication.arch != Config.Arch.x86_64) {
            return LimboApplication.arch
        }
        return when (currentMachine.getArch()?.trim()?.lowercase()) {
            "x86", "i386", "i486", "i586", "i686" -> Config.Arch.x86
            "x86_64", "x64", "amd64" -> Config.Arch.x86_64
            else -> LimboApplication.arch
        }
    }

    private fun getQemuLibrary(): String =
        when (getEffectiveGuestArch()) {
            Config.Arch.x86 -> "libqemu-system-i386.so"
            Config.Arch.x86_64 -> "libqemu-system-x86_64.so"
            Config.Arch.arm -> "libqemu-system-arm.so"
            Config.Arch.arm64 -> "libqemu-system-aarch64.so"
            Config.Arch.ppc -> "libqemu-system-ppc.so"
            Config.Arch.ppc64 -> "libqemu-system-ppc64.so"
            Config.Arch.sparc -> "libqemu-system-sparc.so"
            Config.Arch.sparc64 -> "libqemu-system-sparc64.so"
        }

    private fun getSaveStateName(): String {
        val machineSaveDirectory = MachineController.getInstance().getMachineSaveDir()
        return "$machineSaveDirectory/${Config.stateFilename}"
    }

    @Throws(Exception::class)
    private fun prepareParams(context: Context): Array<Any?> {
        val paramsList = ArrayList<String>()
        paramsList.add(getQemuLibrary())
        validateNativeRuntime(context)
        validateGraphicsOptions(context)
        addUIOptions(context, paramsList)
        addCpuBoardOptions(paramsList)
        addDrives(paramsList)
        addRemovableDrives(paramsList)
        addBootOptions(paramsList)
        addGraphicsOptions(paramsList)
        addAudioOptions(paramsList)
        addNetworkOptions(paramsList)
        addGenericOptions(context, paramsList)
        addStateOptions(paramsList)
        addAdvancedOptions(paramsList)
        addAccelerationOptions(paramsList)
        return paramsList.map { it as Any? }.toTypedArray()
    }

    private fun validateNativeRuntime(context: Context) {
        val nativeLibDir = FileUtils.getNativeLibDir(context)
        val missingLibraries = GraphicsCapabilities.getQemu11RuntimeLibraries(getQemuLibrary()).filter {
            !File(nativeLibDir, it).isFile
        }
        if (missingLibraries.isNotEmpty()) {
            throw IllegalStateException(
                context.getString(
                    R.string.missing_native_libraries_runtime,
                    missingLibraries.joinToString(", "),
                ),
            )
        }
    }

    private fun validateGraphicsOptions(context: Context) {
        val vga = currentMachine.getVga()
        if (GraphicsCapabilities.requiresSdlDisplay(vga) && MachineController.getInstance().isVNCEnabled()) {
            throw IllegalStateException(context.getString(R.string.virgl_requires_sdl_runtime))
        }
    }

    private fun addStateOptions(paramsList: ArrayList<String>) {
        if (MachineController.getInstance().isPaused && getSaveStateName().isNotEmpty()) {
            val fdTmp = FileUtils.get_fd(getSaveStateName())
            if (fdTmp < 0) {
                Log.e(TAG, "Error while getting fd for: ${getSaveStateName()}")
            } else {
                Log.d(TAG, "Retrieved fd: $fdTmp for: ${getSaveStateName()}")
                paramsList.add("-incoming")
                paramsList.add("fd:$fdTmp")
            }
        }
    }

    private fun addUIOptions(context: Context, paramsList: ArrayList<String>) {
        if (MachineController.getInstance().isVNCEnabled()) {
            paramsList.add("-vnc")
            var vncParam = ""
            vncParam += if (LimboSettingsManager.getVNCEnablePassword(context)) {
                ":1"
            } else {
                Config.defaultVNCHost + ":" + Config.defaultVNCPort
            }
            if (LimboSettingsManager.getVNCEnablePassword(context)) {
                vncParam += ",password"
            }
            paramsList.add(vncParam)

            paramsList.add("-monitor")
            paramsList.add("vc")
        } else {
            paramsList.add("-monitor")
            paramsList.add("none")
            paramsList.add("-serial")
            paramsList.add("none")
            paramsList.add("-parallel")
            paramsList.add("none")
        }

        currentMachine.getKeyboard()?.let {
            paramsList.add("-k")
            paramsList.add(it)
        }

        val mouse = currentMachine.getMouse()
        if (mouse != null && mouse != "ps2") {
            paramsList.add("-usb")
            paramsList.add("-device")
            paramsList.add(mouse)
        }
    }

    private fun addAdvancedOptions(paramsList: ArrayList<String>) {
        val extraParams = currentMachine.getExtraParams()
        if (!extraParams.isNullOrBlank()) {
            paramsList.addAll(extraParams.split(" "))
        }
    }

    private fun addAudioOptions(paramsList: ArrayList<String>) {
        val soundCard = getSoundCard() ?: return
        if (!usesModernAudioOptions()) {
            paramsList.add("-soundhw")
            paramsList.add(soundCard)
            return
        }
        if (soundCard == "pcspk") {
            paramsList.add("-audiodev")
            paramsList.add("driver=sdl,id=$AUDIO_DEVICE_ID")
            return
        }
        paramsList.add("-audio")
        paramsList.add("driver=sdl,model=${getAudioModel(soundCard)},id=$AUDIO_DEVICE_ID")
    }

    private fun getAudioModel(soundCard: String): String =
        if (soundCard == "all") "hda" else soundCard

    private fun usesModernAudioOptions(): Boolean =
        LimboApplication.getQemuVersion() >= 70100

    private fun addGenericOptions(context: Context, paramsList: ArrayList<String>) {
        paramsList.add("-L")
        paramsList.add(LimboApplication.getBasefileDir())
        if (LimboSettingsManager.getEnableQmp(context)) {
            paramsList.add("-qmp")
            if (getQMPAllowExternal()) {
                paramsList.add("tcp::${Config.QMPPort},server,nowait")
            } else {
                paramsList.add("unix:${LimboApplication.getLocalQMPSocketPath()},server,nowait")
            }
        }

        if (Config.enableTracingLog) {
            paramsList.add("-D")
            paramsList.add(Config.traceLogFile)
            paramsList.add("--trace")
            paramsList.add("events=${Config.traceEventsFile}")
            paramsList.add("--trace")
            paramsList.add("file=${Config.traceDir}")
        }

        if (Config.overrideTbSize) {
            paramsList.add("-tb-size")
            paramsList.add(Config.tbSize)
        }

        if (LimboApplication.getQemuVersion() == 20901) {
            paramsList.add("-realtime")
            paramsList.add("mlock=off")
        } else {
            paramsList.add("-overcommit")
            paramsList.add("mem-lock=off")
        }

        paramsList.add("-rtc")
        paramsList.add("base=localtime")

        if (!Config.enableDefaultDevices) {
            paramsList.add("-nodefaults")
        }
    }

    private fun addCpuBoardOptions(paramsList: ArrayList<String>) {
        if (currentMachine.getCpuNum() > 1) {
            paramsList.add("-smp")
            paramsList.add(currentMachine.getCpuNum().toString())
        }
        val machineType = getMachineTypeWithRuntimeProperties()
        if (machineType != null && machineType != "Default") {
            paramsList.add("-M")
            paramsList.add(machineType)
        }

        var cpu = currentMachine.getCpu()
        if (cpu != null && cpu.contains(" ")) {
            cpu = "'$cpu'"
        }

        val guestArch = getEffectiveGuestArch()
        if (currentMachine.getDisableTSC() == 1 && (guestArch == Config.Arch.x86 || guestArch == Config.Arch.x86_64)) {
            if (cpu == null || cpu == "Default") {
                cpu = if (guestArch == Config.Arch.x86) {
                    "qemu32"
                } else {
                    "qemu64"
                }
            }
            cpu += ",-tsc"
        }

        if (!usesMachineRuntimeProperties()) {
            addLegacyMachineToggles(paramsList)
        }

        if (cpu != null && cpu != "Default") {
            paramsList.add("-cpu")
            paramsList.add(cpu)
        }

        paramsList.add("-m")
        paramsList.add(currentMachine.getMemory().toString())
    }

    private fun addAccelerationOptions(paramsList: ArrayList<String>) {
        if (currentMachine.getEnableKVM() != 0) {
            paramsList.add("-enable-kvm")
        } else {
            paramsList.add("-accel")
            var tcgParams = "tcg"
            tcgParams += if (currentMachine.getEnableMTTCG() != 0) {
                ",thread=multi"
            } else {
                ",thread=single"
            }
            paramsList.add(tcgParams)
        }
    }

    private fun getMachineType(): String? {
        val guestArch = getEffectiveGuestArch()
        var machineType = currentMachine.getMachineType()
        if (
            (guestArch == Config.Arch.x86 || guestArch == Config.Arch.x86_64) &&
            machineType == null
        ) {
            machineType = "pc"
        } else if (
            (LimboApplication.arch == Config.Arch.ppc || LimboApplication.arch == Config.Arch.ppc64) &&
            machineType == "Default"
        ) {
            machineType = null
        } else if (
            (LimboApplication.arch == Config.Arch.sparc || LimboApplication.arch == Config.Arch.sparc64) &&
            machineType == "Default"
        ) {
            machineType = null
        }
        return machineType
    }

    private fun getMachineTypeWithRuntimeProperties(): String? {
        var machineType = getMachineType()
        if (!supportsPcMachineProperties(machineType)) {
            return machineType
        }
        if (usesMachineRuntimeProperties() && currentMachine.getDisableAcpi() != 0) {
            machineType = appendMachineProperty(machineType, "acpi", "off")
        }
        if (usesMachineRuntimeProperties() && currentMachine.getDisableHPET() != 0) {
            machineType = appendMachineProperty(machineType, "hpet", "off")
        }
        if (usesModernAudioOptions() && getSoundCard() == "pcspk") {
            machineType = appendMachineProperty(machineType, "pcspk-audiodev", AUDIO_DEVICE_ID)
        }
        return machineType
    }

    private fun addLegacyMachineToggles(paramsList: ArrayList<String>) {
        if (currentMachine.getDisableAcpi() != 0) {
            paramsList.add("-no-acpi")
        }
        if (currentMachine.getDisableHPET() != 0) {
            paramsList.add("-no-hpet")
        }
    }

    private fun usesMachineRuntimeProperties(): Boolean =
        LimboApplication.getQemuVersion() >= 90000

    private fun supportsPcMachineProperties(machineType: String?): Boolean {
        val guestArch = getEffectiveGuestArch()
        if (guestArch == Config.Arch.x86 || guestArch == Config.Arch.x86_64) {
            return true
        }
        return machineType != null && (machineType.startsWith("pc") || machineType.startsWith("q35"))
    }

    private fun appendMachineProperty(machineType: String?, property: String, value: String): String {
        val baseType = if (machineType == null || machineType == "Default") "pc" else machineType
        if (baseType.contains("$property=")) {
            return baseType
        }
        return "$baseType,$property=$value"
    }

    @Throws(Exception::class)
    private fun addNetworkOptions(paramsList: ArrayList<String>) {
        val network = getNetCfg()
        if (network == null || network == "none") {
            paramsList.add("-nic")
            paramsList.add("none")
            return
        }

        val networkCard = getNicCard() ?: return

        paramsList.add("-netdev")
        paramsList.add(getNetdevParams(network))
        paramsList.add("-device")
        paramsList.add(getNetworkDeviceParams(networkCard))
    }

    @Throws(Exception::class)
    private fun getNetdevParams(network: String): String =
        when (network) {
            "user" -> "user,id=$NETDEV_ID${getHostForwardParams()}"
            "tap" -> "tap,id=$NETDEV_ID,ifname=tap0,script=no,downscript=no"
            else -> throw Exception("Unsupported network backend: $network")
        }

    @Throws(Exception::class)
    private fun getHostForwardParams(): String {
        val hostFwd = getHostFwd() ?: return ""
        if (hostFwd.startsWith("hostfwd")) {
            throw Exception("Invalid format for Host Forward, should be: tcp:hostport1:guestport1,udp:hostport2:guestport2,...")
        }
        val params = StringBuilder()
        val hostFwdParams = hostFwd.split(",")
        for (hostFwdParam in hostFwdParams) {
            val parts = hostFwdParam.trim().split(":")
            if (parts.size != 3) {
                throw Exception("Invalid format for Host Forward, should be: tcp:hostport1:guestport1,udp:hostport2:guestport2,...")
            }
            params
                .append(",hostfwd=")
                .append(parts[0])
                .append("::")
                .append(parts[1])
                .append("-:")
                .append(parts[2])
        }
        return params.toString()
    }

    private fun getNetworkDeviceParams(networkCard: String): String {
        val device = getNetworkDeviceName(networkCard)
        var params = "$device,netdev=$NETDEV_ID"
        if (device == "pcnet" && !networkCard.contains("rombar=")) {
            params += ",rombar=0"
        }
        return params
    }

    private fun getNetworkDeviceName(networkCard: String): String {
        if (networkCard == "Default") {
            return getDefaultNetworkDeviceName()
        }
        if (networkCard == "virtio") {
            if (
                (LimboApplication.arch == Config.Arch.arm || LimboApplication.arch == Config.Arch.arm64) &&
                getMachineType()?.startsWith("virt") == true
            ) {
                return "virtio-net-device"
            }
            return "virtio-net-pci"
        }
        return networkCard
    }

    private fun getDefaultNetworkDeviceName(): String {
        if (LimboApplication.arch == Config.Arch.arm || LimboApplication.arch == Config.Arch.arm64) {
            val machineType = getMachineType()
            if (machineType != null && machineType.startsWith("virt")) {
                return "virtio-net-device"
            }
            return "smc91c111"
        }
        if (LimboApplication.arch == Config.Arch.sparc || LimboApplication.arch == Config.Arch.sparc64) {
            return "lance"
        }
        return "e1000"
    }

    private fun getHostFwd(): String? {
        if (currentMachine.getNetwork() == "User") {
            val hostFwd = currentMachine.getHostFwd()
            if (!hostFwd.isNullOrEmpty()) {
                return hostFwd
            }
        }
        return null
    }

    private fun getNicCard(): String? =
        when (currentMachine.getNetwork()) {
            null, "None" -> null
            "User", "TAP" -> currentMachine.getNetworkCard()
            else -> null
        }

    private fun getNetCfg(): String? =
        when (currentMachine.getNetwork()) {
            null, "None" -> "none"
            "User" -> "user"
            "TAP" -> "tap"
            else -> null
        }

    private fun addGraphicsOptions(paramsList: ArrayList<String>) {
        val vga = currentMachine.getVga() ?: return
        when {
            vga == "Default" -> Unit
            GraphicsCapabilities.isDeviceBackedGpu(vga) -> {
                val displayBackend = GraphicsCapabilities.getDisplayBackend(vga)
                if (displayBackend != null && !MachineController.getInstance().isVNCEnabled()) {
                    paramsList.add("-display")
                    paramsList.add(displayBackend)
                }
                paramsList.add("-device")
                paramsList.add(vga)
            }
            vga == "nographic" -> paramsList.add("-nographic")
            else -> {
                paramsList.add("-vga")
                paramsList.add(vga)
            }
        }
    }

    private fun addBootOptions(paramsList: ArrayList<String>) {
        getBootDevice()?.let {
            paramsList.add("-boot")
            paramsList.add(it)
        }

        val kernel = getKernel()
        if (!kernel.isNullOrEmpty()) {
            paramsList.add("-kernel")
            paramsList.add(kernel)
        }

        val initrd = getInitRd()
        if (!initrd.isNullOrEmpty()) {
            paramsList.add("-initrd")
            paramsList.add(initrd)
        }

        val append = currentMachine.getAppend()
        if (!append.isNullOrEmpty()) {
            paramsList.add("-append")
            paramsList.add(append)
        }
    }

    private fun getBootDevice(): String? {
        val bootDevice = currentMachine.getBootDevice()
        if (LimboApplication.arch == Config.Arch.arm || LimboApplication.arch == Config.Arch.arm64) {
            return null
        }
        return when (bootDevice) {
            "Default" -> null
            "CDROM" -> "d"
            "Floppy" -> "a"
            "Hard Disk" -> "c"
            else -> null
        }
    }

    private fun getInitRd(): String? = FileUtils.encodeDocumentFilePath(currentMachine.getInitRd())

    private fun getKernel(): String? = FileUtils.encodeDocumentFilePath(currentMachine.getKernel())

    fun getDriveFilePath(driveFilePath: String?): String? {
        var imgPath = driveFilePath
        if (imgPath == null || imgPath == "None") {
            return null
        }
        imgPath = FileUtils.encodeDocumentFilePath(imgPath)
        return imgPath
    }

    fun addDrives(paramsList: ArrayList<String>) {
        addHardDisk(paramsList, getDriveFilePath(currentMachine.getHdaImagePath()), 0, currentMachine.getHdaInterface())
        addHardDisk(paramsList, getDriveFilePath(currentMachine.getHdbImagePath()), 1, currentMachine.getHdbInterface())
        addHardDisk(paramsList, getDriveFilePath(currentMachine.getHdcImagePath()), 2, currentMachine.getHdcInterface())
        addHardDisk(paramsList, getDriveFilePath(currentMachine.getHddImagePath()), 3, currentMachine.getHddInterface())
        addSharedFolder(paramsList, getDriveFilePath(currentMachine.getSharedFolderPath()))
    }

    fun addHardDisk(paramsList: ArrayList<String>, imagePath: String?, index: Int, hdInterface: String?) {
        if (imagePath.isNullOrBlank()) {
            return
        }
        if (Config.legacyDrives) {
            when (index) {
                0 -> paramsList.add("-hda")
                1 -> paramsList.add("-hdb")
                2 -> paramsList.add("-hdc")
                3 -> paramsList.add("-hdd")
            }
            paramsList.add(imagePath)
        } else {
            when {
                isVirtioDiskInterface(hdInterface) -> addModernHardDisk(paramsList, imagePath, index, getVirtioBlockDeviceName())
                isScsiDiskInterface(hdInterface) -> {
                    ensureScsiController(paramsList)
                    addModernHardDisk(paramsList, imagePath, index, "scsi-hd")
                }
                else -> {
                    paramsList.add("-drive")
                    var param = "index=$index"
                    param += ",if="
                    param += hdInterface
                    param += ",media=disk"
                    if (imagePath.isNotEmpty()) {
                        param += ",file=$imagePath"
                    }
                    param += getDriveCacheParams()
                    paramsList.add(param)
                }
            }
        }
    }

    private fun addModernHardDisk(paramsList: ArrayList<String>, imagePath: String, index: Int, deviceName: String) {
        val driveId = "limbo-hd$index"
        paramsList.add("-drive")
        var param = "if=none,id=$driveId,media=disk"
        if (imagePath.isNotEmpty()) {
            param += ",file=$imagePath"
        }
        param += getDriveCacheParams()
        paramsList.add(param)

        paramsList.add("-device")
        paramsList.add("$deviceName,drive=$driveId")
    }

    private fun ensureScsiController(paramsList: ArrayList<String>) {
        val controllerParams = "lsi53c895a,id=$SCSI_CONTROLLER_ID"
        if (paramsList.contains(controllerParams)) {
            return
        }
        paramsList.add("-device")
        paramsList.add(controllerParams)
    }

    private fun getDriveCacheParams(): String {
        val cache = LimboSettingsManager.getDiskCache(LimboApplication.getInstance())
        if (cache != "default") {
            return ",cache=$cache"
        }
        return ""
    }

    private fun isVirtioDiskInterface(diskInterface: String?): Boolean =
        diskInterface == "virtio"

    private fun isScsiDiskInterface(diskInterface: String?): Boolean =
        diskInterface == "scsi"

    private fun getVirtioBlockDeviceName(): String {
        if (
            (LimboApplication.arch == Config.Arch.arm || LimboApplication.arch == Config.Arch.arm64) &&
            getMachineType()?.startsWith("virt") == true
        ) {
            return "virtio-blk-device"
        }
        return "virtio-blk-pci"
    }

    fun addSharedFolder(paramsList: ArrayList<String>, sharedFolderPath: String?) {
        if (Config.enableSharedFolder && sharedFolderPath != null) {
            paramsList.add("-drive")
            var driveParams = "index=3"
            driveParams += ",media=disk"
            driveParams += ",if=ide"
            driveParams += ",format=raw"
            driveParams += ",file=fat:"
            driveParams += "rw:"
            driveParams += sharedFolderPath
            paramsList.add(driveParams)
        }
    }

    fun addRemovableDrives(paramsList: ArrayList<String>) {
        val cdImagePath = getDriveFilePath(currentMachine.getCdImagePath())
        if (cdImagePath != null) {
            if (Config.legacyDrives) {
                paramsList.add("-cdrom")
                paramsList.add(cdImagePath)
            } else {
                paramsList.add("-drive")
                var param = "index=2"
                param += ",if="
                param += currentMachine.getCDInterface()
                param += ",media=cdrom"
                if (cdImagePath.isNotEmpty()) {
                    param += ",file=$cdImagePath"
                }
                paramsList.add(param)
            }
        }

        val fdaImagePath = getDriveFilePath(currentMachine.getFdaImagePath())
        if (Config.enableEmulatedFloppy && fdaImagePath != null) {
            if (Config.legacyDrives) {
                paramsList.add("-fda")
                paramsList.add(fdaImagePath)
            } else {
                paramsList.add("-drive")
                var param = "index=0,if=floppy"
                if (fdaImagePath.isNotEmpty()) {
                    param += ",file=$fdaImagePath"
                }
                paramsList.add(param)
            }
        }

        val fdbImagePath = getDriveFilePath(currentMachine.getFdbImagePath())
        if (Config.enableEmulatedFloppy && fdbImagePath != null) {
            if (Config.legacyDrives) {
                paramsList.add("-fdb")
                paramsList.add(fdbImagePath)
            } else {
                paramsList.add("-drive")
                var param = "index=1,if=floppy"
                if (fdbImagePath.isNotEmpty()) {
                    param += ",file=$fdbImagePath"
                }
                paramsList.add(param)
            }
        }

        val sdImagePath = getDriveFilePath(currentMachine.getSdImagePath())
        if (Config.enableEmulatedSDCard && sdImagePath != null) {
            if (Config.legacyDrives) {
                paramsList.add("-sd")
                paramsList.add(sdImagePath)
            } else {
                paramsList.add("-device")
                paramsList.add("sd-card,drive=sd0,bus=sd-bus")
                paramsList.add("-drive")
                var param = "if=none,id=sd0"
                if (sdImagePath.isNotEmpty()) {
                    param += ",file=$sdImagePath"
                }
                paramsList.add(param)
            }
        }
    }

    @Throws(Exception::class)
    protected fun vncchangepassword(vncPassword: String?) {
        val res = QmpClient.sendCommand(QmpClient.getChangeVncPasswdCommand(vncPassword.orEmpty()))
        if (!res.isNullOrEmpty()) {
            val resObj = JSONObject(res)
            if (res.contains("error")) {
                val resInfo = resObj.getString("error")
                if (resInfo.isNotEmpty()) {
                    val desc = JSONObject(resInfo).getString("desc")
                    Log.e(TAG, desc)
                }
            }
        }
    }

    protected fun changedev(dev: String?, value: String?): String? {
        val response = QmpClient.sendCommand(QmpClient.getChangeDeviceCommand(dev.orEmpty(), value.orEmpty()))
        val displayDevValue = FileUtils.getFullPathFromDocumentFilePath(value.orEmpty())
        if (Config.debug) {
            ToastUtils.toastLong(
                LimboApplication.getInstance(),
                Gravity.BOTTOM,
                LimboApplication.getInstance().getString(R.string.ChangedDevice) + ": " + dev + ": " + displayDevValue,
            )
        }
        return response
    }

    protected fun ejectdev(dev: String?): String? {
        val response = QmpClient.sendCommand(QmpClient.getEjectDeviceCommand(dev.orEmpty()))
        if (Config.debug) {
            ToastUtils.toastLong(
                LimboApplication.getInstance(),
                Gravity.BOTTOM,
                LimboApplication.getInstance().getString(R.string.EjectedDevice) + ": " + dev,
            )
        }
        return response
    }

    override fun startService() {
        val intent = Intent(
            Config.ACTION_START,
            null,
            LimboApplication.getInstance(),
            MachineController.getInstance().getServiceClass(),
        )
        intent.putExtras(Bundle())
        Log.d(TAG, "Starting VM service")
        LimboApplication.getInstance().startService(intent)
    }

    override fun start(): String? {
        var res: String? = null
        try {
            val params = prepareParams(LimboApplication.getInstance())
            printParams(params)
            if (currentMachine.getPaused() == 1 && MachineController.getInstance().isVNCEnabled()) {
                continueVM(5000)
            }

            if (
                MachineController.getInstance().isVNCEnabled() &&
                LimboSettingsManager.getVNCEnablePassword(LimboApplication.getInstance())
            ) {
                changeVncPass(LimboApplication.getInstance(), 2000)
            }

            ignoreBreakpointInvalidation(
                if (LimboSettingsManager.getIgnoreBreakpointInvalidation(LimboApplication.getInstance())) 1 else 0,
                2000,
            )
            QmpClient.setExternal(LimboSettingsManager.getEnableExternalQMP(LimboApplication.getInstance()))
            val libFilename = getQemuLibrary()
            res = start(
                Config.storagedir,
                LimboApplication.getBasefileDir(),
                libFilename,
                FileUtils.getNativeLibDir(LimboApplication.getInstance()) + "/" + libFilename,
                Config.SDLHintScale,
                params,
            )
        } catch (ex: Exception) {
            ToastUtils.toastLong(LimboApplication.getInstance(), ex.message)
            return res
        }
        return res
    }

    private fun changeVncPass(context: Context, delay: Long) {
        Thread {
            try {
                Thread.sleep(delay)
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
            }
            try {
                vncchangepassword(LimboSettingsManager.getVNCPass(context))
            } catch (ex: Exception) {
                ToastUtils.toastLong(
                    LimboApplication.getInstance(),
                    context.getString(R.string.CouldNotSetVNCPass) + ": " + ex.message,
                )
                ex.printStackTrace()
            }
        }.start()
    }

    private fun continueVM(delay: Int) {
        LimboApplication.getViewListener().onAction(MachineAction.CONTINUE_VM, delay)
    }

    override fun stopvm(restart: Int) {
        Thread {
            if (restart != 0) {
                QmpClient.sendCommand(QmpClient.getResetCommand())
            } else {
                stop(restart)
            }
        }.start()
    }

    override fun getSdlRefreshRate(idle: Boolean): Int =
        if (idle) getSDLRefreshRateIdle() else getSDLRefreshRateDefault()

    override fun setSdlRefreshRate(refreshMs: Int, idle: Boolean) {
        if (idle) {
            setSDLRefreshRateIdle(refreshMs)
        } else {
            setSDLRefreshRateDefault(refreshMs)
        }
    }

    override fun getDeviceName(driveProperty: MachineProperty?): String? =
        when (driveProperty) {
            MachineProperty.CDROM -> CD_DEVICE_NAME
            MachineProperty.FDA -> FDA_DEVICE_NAME
            MachineProperty.FDB -> FDB_DEVICE_NAME
            MachineProperty.SD -> SD_DEVICE_NAME
            else -> null
        }

    @Synchronized
    override fun updateDisplay(width: Int, height: Int, orientation: Int) {
        if (!LimboSettingsManager.getPreventMouseOutOfBounds(LimboApplication.getInstance())) {
            return
        }
        val mouse = currentMachine.getMouse()
        if (mouse == "usb-tablet" && vmWidth > 0 && vmHeight > 0) {
            var xmin = 0
            var xmax = width
            var ymin = 0
            var ymax = height
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                ymin = ((height - width * vmHeight / vmWidth.toFloat()) / 2).toInt()
                ymax = ((height + width * vmHeight / vmWidth.toFloat()) / 2).toInt()
            } else {
                xmin = ((width - height * vmWidth / vmHeight.toFloat()) / 2).toInt()
                xmax = ((width + height * vmWidth / vmHeight.toFloat()) / 2).toInt()
            }
            nativeMouseBounds(xmin, xmax, ymin, ymax)
        }
    }

    override fun setFullscreen() {
        nativeFullscreen()
        if (
            LimboApplication.arch == Config.Arch.x86 ||
            LimboApplication.arch == Config.Arch.x86_64 ||
            LimboApplication.arch == Config.Arch.arm ||
            LimboApplication.arch == Config.Arch.arm64 ||
            LimboApplication.arch == Config.Arch.ppc ||
            LimboApplication.arch == Config.Arch.ppc64
        ) {
            nativeRefreshScreen(1)
        }
    }

    override fun enableAaudio(value: Int) {
        nativeEnableAaudio(
            value,
            Config.aaudioLibName,
            FileUtils.getNativeLibDir(LimboApplication.getInstance()) + "/" + Config.aaudioLibName,
        )
    }

    override fun ignoreBreakpointInvalidation(value: Int) {
        ignoreBreakpointInvalidation(value, 0)
    }

    private fun ignoreBreakpointInvalidation(value: Int, delay: Long) {
        Thread {
            try {
                Thread.sleep(delay)
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
            }
            nativeIgnoreBreakpointInvalidate(value)
        }.start()
    }

    fun getVmState(): String {
        val res = QmpClient.sendCommand(QmpClient.getStateCommand())
        var state = ""
        if (!res.isNullOrEmpty()) {
            try {
                val resObj = JSONObject(res)
                val resInfo = resObj.getString("return")
                val resInfoObj = JSONObject(resInfo)
                state = resInfoObj.getString("status")
            } catch (ex: JSONException) {
                ex.printStackTrace()
            }
        }
        return state
    }

    override fun changeRemovableDevice(drive: MachineProperty?, diskValue: String?): Boolean {
        if (!LimboSettingsManager.getEnableQmp(LimboApplication.getInstance())) {
            ToastUtils.toastShort(
                LimboApplication.getInstance(),
                LimboApplication.getInstance().getString(R.string.EnableQMPForChangingDrives),
            )
            return false
        }
        val dev = getDeviceName(drive)

        ejectdev(dev)

        if (diskValue.isNullOrBlank()) {
            return true
        }

        val imagePathConverted = FileUtils.encodeDocumentFilePath(diskValue)

        if (!FileUtils.fileValid(imagePathConverted)) {
            val msg = LimboApplication.getInstance().getString(R.string.CouldNotOpenDocFile) + " " +
                FileUtils.getFullPathFromDocumentFilePath(imagePathConverted.orEmpty()) +
                "\n" + LimboApplication.getInstance().getString(R.string.PleaseReassingYourDiskFiles)
            ToastUtils.toastLong(LimboApplication.getInstance(), msg)
            return false
        }
        val response = changedev(dev, imagePathConverted)
        if (response == null) {
            return false
        }

        return true
    }

    fun get_fd(path: String?): Int = FileUtils.get_fd(path)

    fun close_fd(fd: Int): Int = FileUtils.close_fd(fd)

    override fun saveVM(): String? {
        val file = File(getSaveStateName())
        if (file.exists()) {
            if (!file.delete()) {
                return LimboApplication.getInstance().getString(R.string.CannotDeletePreviousStateFile)
            }
        }

        if (Config.showToast) {
            ToastUtils.toastShort(
                LimboApplication.getInstance(),
                LimboApplication.getInstance().getString(R.string.PleaseWaitSavingVMState),
            )
        }

        val currentFd = get_fd(getSaveStateName())
        val uri = "fd:$currentFd"
        var command = QmpClient.getStopVMCommand()
        QmpClient.sendCommand(command)
        command = QmpClient.getMigrateCommand(false, false, uri)
        val msg = QmpClient.sendCommand(command)
        if (msg != null) {
            return processMigrationResponse(msg)
        }
        return null
    }

    override fun continueVM() {
        val command = QmpClient.getContinueVMCommand()
        QmpClient.sendCommand(command)
    }

    override fun getSaveVMStatus(): MachineController.MachineStatus {
        var pauseState = ""
        val command = QmpClient.getQueryMigrationCommand()
        val res = QmpClient.sendCommand(command)

        if (!res.isNullOrEmpty()) {
            try {
                val resObj = JSONObject(res)
                val resInfo = resObj.getString("return")
                val resInfoObj = JSONObject(resInfo)
                pauseState = resInfoObj.getString("status")
            } catch (ex: JSONException) {
                if (Config.debug) {
                    Log.e(TAG, "Error while checking saving vm: ${ex.message}")
                }
            }
            if (pauseState.uppercase() == "FAILED") {
                Log.e(TAG, "Error: $res")
            }
        }
        return when (pauseState.uppercase()) {
            "ACTIVE" -> MachineController.MachineStatus.Saving
            "COMPLETED" -> MachineController.MachineStatus.SaveCompleted
            "FAILED" -> MachineController.MachineStatus.SaveFailed
            else -> MachineController.MachineStatus.Unknown
        }
    }

    private fun processMigrationResponse(response: String): String? {
        var errorStr: String? = null
        try {
            val obj = JSONObject(response)
            errorStr = obj.getString("error")
        } catch (ex: Exception) {
            if (Config.debug) {
                ex.printStackTrace()
            }
        }
        if (errorStr != null) {
            var descStr: String? = null
            try {
                val descObj = JSONObject(errorStr)
                descStr = descObj.getString("desc")
            } catch (ex: Exception) {
                if (Config.debug) {
                    ex.printStackTrace()
                }
            }
            return descStr
        }
        return null
    }

    override fun sendMouseEvent(button: Int, action: Int, relative: Int, x: Float, y: Float) {
        if (LimboSDLActivity.isResizing) {
            return
        }

        nativeMouseEvent(button, action, relative, x.toInt(), y.toInt())
    }

    fun getQMPAllowExternal(): Boolean =
        LimboSettingsManager.getEnableExternalQMP(LimboApplication.getInstance())

    companion object {
        private const val TAG = "VMExecutor"
        private const val AUDIO_DEVICE_ID = "limbo-audio0"
        private const val NETDEV_ID = "limbo-net0"
        private const val SCSI_CONTROLLER_ID = "limbo-scsi0"

        private const val CD_DEVICE_NAME = "ide1-cd0"
        private const val FDA_DEVICE_NAME = "floppy0"
        private const val FDB_DEVICE_NAME = "floppy1"
        private const val SD_DEVICE_NAME = "sd0"

        private var vmWidth = 0
        private var vmHeight = 0
        private var instance: VMExecutor? = null

        /**
         * Called from SDL compatibility extensions when the guest resolution changes.
         */
        @JvmStatic
        fun onVMResolutionChanged(width: Int, height: Int) {
            vmWidth = width
            vmHeight = height
            instance?.onResolutionChanged(vmWidth, vmHeight)
        }
    }
}
