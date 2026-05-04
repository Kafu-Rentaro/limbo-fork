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

import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboApplication
import java.util.Observable

/**
 * Holds machine properties and notifies observers when fields change.
 */
class Machine(
    private var name: String?,
    loadDefaults: Boolean,
) : Observable() {

    private var keyboard: String? = Config.defaultKeyboardLayout
    private var mouse: String? = "ps2"
    private var enableVNC = 0
    private var arch: String? = null
    private var machineType: String? = null
    private var cpu: String? = "Default"
    private var cpuNum = 1
    private var memory = 128
    private var enableMTTCG = 0
    private var enableKVM = 0
    private var disableACPI = 0
    private var disableHPET = 0
    private var disableFdBootChk = 0
    private var disableTSC = 1

    private var hdaImagePath: String? = null
    private var hdbImagePath: String? = null
    private var hdcImagePath: String? = null
    private var hddImagePath: String? = null
    private var hdaInterface: String? = "ide"
    private var hdbInterface: String? = "ide"
    private var hdcInterface: String? = "ide"
    private var hddInterface: String? = "ide"

    private var sharedFolderPath: String? = null
    private var enableCDROM = false
    private var enableFDA = false
    private var enableFDB = false
    private var enableSD = false
    private var cdImagePath: String? = null
    private var fdaImagePath: String? = null
    private var fdbImagePath: String? = null
    private var sdImagePath: String? = null
    private var cdInterface: String? = "ide"

    private var bootDevice: String? = "Default"
    private var kernel: String? = null
    private var initRd: String? = null
    private var append: String? = null
    private var network: String? = null
    private var networkCard: String? = "ne2k_pci"
    private var guestFwd: String? = null
    private var hostFwd: String? = null
    private var vga: String? = "std"
    private var soundCard: String? = null
    private var extraParams: String? = null
    private var paused = 0
    private var sharedFolderMode = 0

    init {
        if (loadDefaults) {
            setDefaults()
        }
    }

    fun getName(): String? = name

    fun setName(name: String?) {
        this.name = name
    }

    fun getEnableVNC(): Int = enableVNC

    fun setEnableVNC(enableVNC: Int) {
        update(MachineProperty.UI, enableVNC) {
            this.enableVNC = enableVNC
        }
    }

    fun getArch(): String? = arch

    fun setArch(arch: String?) {
        updateIfChanged(this.arch, arch, MachineProperty.ARCH) {
            this.arch = arch
        }
    }

    fun getMachineType(): String? = machineType

    fun setMachineType(machineType: String?) {
        updateIfChanged(this.machineType, machineType, MachineProperty.MACHINETYPE) {
            this.machineType = machineType
        }
    }

    fun getCpu(): String? = cpu

    fun setCpu(cpu: String?) {
        updateIfChanged(this.cpu, cpu, MachineProperty.CPU) {
            this.cpu = cpu
        }
    }

    fun getCpuNum(): Int = cpuNum

    fun setCpuNum(cpuNum: Int) {
        update(MachineProperty.CPUNUM, cpuNum) {
            this.cpuNum = cpuNum
        }
    }

    fun getMemory(): Int = memory

    fun setMemory(memory: Int) {
        update(MachineProperty.MEMORY, memory) {
            this.memory = memory
        }
    }

    fun getEnableMTTCG(): Int = enableMTTCG

    fun setEnableMTTCG(enableMTTCG: Int) {
        update(MachineProperty.ENABLE_MTTCG, enableMTTCG) {
            this.enableMTTCG = enableMTTCG
        }
    }

    fun getEnableKVM(): Int = enableKVM

    fun setEnableKVM(enableKVM: Int) {
        update(MachineProperty.ENABLE_KVM, enableKVM) {
            this.enableKVM = enableKVM
        }
    }

    fun getDisableACPI(): Int = disableACPI

    fun getDisableAcpi(): Int = disableACPI

    fun setDisableACPI(disableACPI: Int) {
        update(MachineProperty.DISABLE_ACPI, disableACPI) {
            this.disableACPI = disableACPI
        }
    }

    fun getDisableFdBootChk(): Int = disableFdBootChk

    fun setDisableFdBootChk(disableFdBootChk: Int) {
        update(MachineProperty.DISABLE_FD_BOOT_CHK, disableFdBootChk) {
            this.disableFdBootChk = disableFdBootChk
        }
    }

    fun getDisableTSC(): Int = disableTSC

    fun setDisableTSC(disableTSC: Int) {
        update(MachineProperty.DISABLE_TSC, disableTSC) {
            this.disableTSC = disableTSC
        }
    }

    fun getHdaImagePath(): String? = hdaImagePath

    fun setHdaImagePath(hdaImagePath: String?) {
        updateIfChanged(this.hdaImagePath, hdaImagePath, MachineProperty.HDA) {
            this.hdaImagePath = hdaImagePath
        }
    }

    fun getHdbImagePath(): String? = hdbImagePath

    fun setHdbImagePath(hdbImagePath: String?) {
        updateIfChanged(this.hdbImagePath, hdbImagePath, MachineProperty.HDB) {
            this.hdbImagePath = hdbImagePath
        }
    }

    fun getHdcImagePath(): String? = hdcImagePath

    fun setHdcImagePath(hdcImagePath: String?) {
        updateIfChanged(this.hdcImagePath, hdcImagePath, MachineProperty.HDC) {
            this.hdcImagePath = hdcImagePath
        }
    }

    fun getHddImagePath(): String? = hddImagePath

    fun setHddImagePath(hddImagePath: String?) {
        updateIfChanged(this.hddImagePath, hddImagePath, MachineProperty.HDD) {
            this.hddImagePath = hddImagePath
        }
    }

    fun getHdaInterface(): String? = hdaInterface

    fun setHdaInterface(hdInterface: String?) {
        updateIfChanged(this.hdaInterface, hdInterface, MachineProperty.HDA_INTERFACE) {
            this.hdaInterface = hdInterface
        }
    }

    fun getHdbInterface(): String? = hdbInterface

    fun setHdbInterface(hdInterface: String?) {
        updateIfChanged(this.hdbInterface, hdInterface, MachineProperty.HDB_INTERFACE) {
            this.hdbInterface = hdInterface
        }
    }

    fun getHdcInterface(): String? = hdcInterface

    fun setHdcInterface(hdInterface: String?) {
        updateIfChanged(this.hdcInterface, hdInterface, MachineProperty.HDC_INTERFACE) {
            this.hdcInterface = hdInterface
        }
    }

    fun getHddInterface(): String? = hddInterface

    fun setHddInterface(hdInterface: String?) {
        updateIfChanged(this.hddInterface, hdInterface, MachineProperty.HDD_INTERFACE) {
            this.hddInterface = hdInterface
        }
    }

    fun getSharedFolderPath(): String? = sharedFolderPath

    fun setSharedFolderPath(sharedFolderPath: String?) {
        updateIfChanged(this.sharedFolderPath, sharedFolderPath, MachineProperty.SHARED_FOLDER) {
            this.sharedFolderPath = sharedFolderPath
        }
    }

    fun isEnableCDROM(): Boolean = enableCDROM

    fun setEnableCDROM(enableCDROM: Boolean) {
        updateFlag(enableCDROM, this.enableCDROM) {
            this.enableCDROM = enableCDROM
        }
    }

    fun isEnableFDA(): Boolean = enableFDA

    fun setEnableFDA(enableFDA: Boolean) {
        updateFlag(enableFDA, this.enableFDA) {
            this.enableFDA = enableFDA
        }
    }

    fun isEnableFDB(): Boolean = enableFDB

    fun setEnableFDB(enableFDB: Boolean) {
        updateFlag(enableFDB, this.enableFDB) {
            this.enableFDB = enableFDB
        }
    }

    fun isEnableSD(): Boolean = enableSD

    fun setEnableSD(enableSD: Boolean) {
        updateFlag(enableSD, this.enableSD) {
            this.enableSD = enableSD
        }
    }

    fun getCdImagePath(): String? = cdImagePath

    fun setCdImagePath(cdImagePath: String?) {
        updateIfChanged(this.cdImagePath, cdImagePath, MachineProperty.CDROM) {
            this.cdImagePath = cdImagePath
        }
    }

    fun getFdaImagePath(): String? = fdaImagePath

    fun setFdaImagePath(fdaImagePath: String?) {
        updateIfChanged(this.fdaImagePath, fdaImagePath, MachineProperty.FDA) {
            this.fdaImagePath = fdaImagePath
        }
    }

    fun getFdbImagePath(): String? = fdbImagePath

    fun setFdbImagePath(fdbImagePath: String?) {
        updateIfChanged(this.fdbImagePath, fdbImagePath, MachineProperty.FDB) {
            this.fdbImagePath = fdbImagePath
        }
    }

    fun getSdImagePath(): String? = sdImagePath

    fun setSdImagePath(sdImagePath: String?) {
        updateIfChanged(this.sdImagePath, sdImagePath, MachineProperty.SD) {
            this.sdImagePath = sdImagePath
        }
    }

    fun getCDInterface(): String? = cdInterface

    fun setCdInterface(mediaInterface: String?) {
        updateIfChanged(this.cdInterface, mediaInterface, MachineProperty.CDROM_INTERFACE) {
            this.cdInterface = mediaInterface
        }
    }

    fun getBootDevice(): String? = bootDevice

    fun setBootDevice(bootDevice: String?) {
        updateIfChanged(this.bootDevice, bootDevice, MachineProperty.BOOT_CONFIG) {
            this.bootDevice = bootDevice
        }
    }

    fun getKernel(): String? = kernel

    fun setKernel(kernel: String?) {
        updateIfChanged(this.kernel, kernel, MachineProperty.KERNEL) {
            this.kernel = kernel
        }
    }

    fun getInitRd(): String? = initRd

    fun setInitRd(initRd: String?) {
        updateIfChanged(this.initRd, initRd, MachineProperty.INITRD) {
            this.initRd = initRd
        }
    }

    fun getAppend(): String? = append

    fun setAppend(append: String?) {
        updateIfChanged(this.append, append, MachineProperty.APPEND) {
            this.append = append
        }
    }

    fun getNetwork(): String? = network

    fun setNetwork(network: String?) {
        updateIfChanged(this.network, network, MachineProperty.NETCONFIG) {
            this.network = network
        }
    }

    fun getNetworkCard(): String? = networkCard

    fun setNetworkCard(networkCard: String?) {
        updateIfChanged(this.networkCard, networkCard, MachineProperty.NICCONFIG) {
            this.networkCard = networkCard
        }
    }

    fun getGuestFwd(): String? = guestFwd

    fun setGuestFwd(guestFwd: String?) {
        updateIfChanged(this.guestFwd, guestFwd, MachineProperty.GUESTFWD) {
            this.guestFwd = guestFwd
        }
    }

    fun getHostFwd(): String? = hostFwd

    fun setHostFwd(hostFwd: String?) {
        updateIfChanged(this.hostFwd, hostFwd, MachineProperty.HOSTFWD) {
            this.hostFwd = hostFwd
        }
    }

    fun getVga(): String? = vga

    fun setVga(vga: String?) {
        updateIfChanged(this.vga, vga, MachineProperty.VGA) {
            this.vga = vga
        }
    }

    fun getExtraParams(): String? = extraParams

    fun setExtraParams(extraParams: String?) {
        updateIfChanged(this.extraParams, extraParams, MachineProperty.EXTRA_PARAMS) {
            this.extraParams = extraParams
        }
    }

    fun getPaused(): Int = paused

    fun setPaused(value: Int) {
        update(MachineProperty.PAUSED, value) {
            paused = value
        }
    }

    fun getShared_folder_mode(): Int = sharedFolderMode

    fun setShared_folder_mode(sharedFolderMode: Int) {
        update(MachineProperty.SHARED_FOLDER_MODE, sharedFolderMode) {
            this.sharedFolderMode = sharedFolderMode
        }
    }

    fun hasRemovableDevices(): Boolean = enableCDROM || enableFDA || enableFDB || enableSD

    fun setDefaults() {
        when (LimboApplication.arch) {
            Config.Arch.x86,
            Config.Arch.x86_64 -> {
                arch = "x86"
                cpu = "n270"
                machineType = "pc"
                networkCard = "Default"
                disableTSC = 1
            }
            Config.Arch.arm,
            Config.Arch.arm64 -> {
                arch = "ARM"
                machineType = "versatilepb"
                cpu = "Default"
                networkCard = "Default"
            }
            Config.Arch.ppc,
            Config.Arch.ppc64 -> {
                arch = "PPC"
                machineType = "g3beige"
                networkCard = "Default"
            }
            Config.Arch.sparc,
            Config.Arch.sparc64 -> {
                arch = "SPARC"
                vga = "cg3"
                machineType = "Default"
                networkCard = "Default"
            }
        }
    }

    fun getSoundCard(): String? = soundCard

    fun setSoundCard(soundCard: String?) {
        updateIfChanged(this.soundCard, soundCard, MachineProperty.SOUNDCARD) {
            this.soundCard = soundCard
        }
    }

    fun getKeyboard(): String? = keyboard

    fun setKeyboard(keyboard: String?) {
        updateIfChanged(this.keyboard, keyboard, MachineProperty.KEYBOARD) {
            this.keyboard = keyboard
        }
    }

    fun getMouse(): String? = mouse

    fun setMouse(mouse: String?) {
        updateIfChanged(this.mouse, mouse, MachineProperty.MOUSE) {
            this.mouse = mouse
        }
    }

    fun getDisableHPET(): Int = disableHPET

    fun setDisableHPET(disableHPET: Int) {
        update(MachineProperty.DISABLE_HPET, disableHPET) {
            this.disableHPET = disableHPET
        }
    }

    private fun update(property: MachineProperty, value: Any?, setter: () -> Unit) {
        val changed = when (property) {
            MachineProperty.UI -> enableVNC != value
            MachineProperty.CPUNUM -> cpuNum != value
            MachineProperty.MEMORY -> memory != value
            MachineProperty.ENABLE_MTTCG -> enableMTTCG != value
            MachineProperty.ENABLE_KVM -> enableKVM != value
            MachineProperty.DISABLE_ACPI -> disableACPI != value
            MachineProperty.DISABLE_FD_BOOT_CHK -> disableFdBootChk != value
            MachineProperty.DISABLE_TSC -> disableTSC != value
            MachineProperty.PAUSED -> paused != value
            MachineProperty.SHARED_FOLDER_MODE -> sharedFolderMode != value
            MachineProperty.DISABLE_HPET -> disableHPET != value
            MachineProperty.OTHER -> true
            else -> true
        }
        if (changed) {
            setter()
            setChanged()
            notifyChanged(property, value)
        }
    }

    private fun updateIfChanged(
        current: String?,
        value: String?,
        property: MachineProperty,
        setter: () -> Unit,
    ) {
        if (current != value) {
            setter()
            setChanged()
            notifyChanged(property, value)
        }
    }

    private fun updateFlag(value: Boolean, current: Boolean, setter: () -> Unit) {
        if (current != value) {
            setter()
            setChanged()
            notifyChanged(MachineProperty.OTHER, value)
        }
    }

    private fun notifyChanged(property: MachineProperty, value: Any?) {
        notifyObservers(arrayOf<Any?>(property, value))
    }

    enum class FileType {
        CDROM,
        FDA,
        FDB,
        SD,
        HDA,
        HDB,
        HDC,
        HDD,
        SHARED_DIR,
        KERNEL,
        INITRD,
        EXPORT_DIR,
        IMAGE_DIR,
        LOG_DIR,
        IMPORT_FILE,
        IMPORT_BIOS_FILE,
    }
}
