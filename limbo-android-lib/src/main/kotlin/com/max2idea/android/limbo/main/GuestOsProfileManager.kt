package com.max2idea.android.limbo.main

import android.app.Activity
import android.content.DialogInterface
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.machine.Machine
import com.max2idea.android.limbo.machine.MachineProperty
import com.max2idea.android.limbo.toast.ToastUtils

object GuestOsProfileManager {
    private val x86WindowsProfiles = listOf(
        GuestOsProfile(
            title = "Windows 98 / Me",
            description = "i440fx, IDE, SB16, Cirrus VGA",
            machineType = "pc",
            cpu = "pentium2",
            memoryMb = 128,
            vga = "cirrus",
            soundCard = "sb16",
            network = "User",
            nic = "ne2k_pci",
            mouse = "ps2",
            hdaInterface = "ide",
            cdInterface = "ide",
            disableTsc = true,
            notes = listOf("Stable Win9x baseline. 3D acceleration is not enabled in this profile."),
        ),
        GuestOsProfile(
            title = "Windows 98 / Me + 3D ready",
            description = "VGA 64MB, SDL, BOXV9x/SoftGPU/qemu-3dfx path",
            machineType = "pc,hpet=off,usb=off",
            cpu = "pentium2",
            memoryMb = 256,
            vga = "VGA,vgamem_mb=64",
            soundCard = "ac97",
            network = "User",
            nic = "pcnet",
            mouse = "ps2",
            hdaInterface = "ide",
            cdInterface = "ide",
            disableTsc = true,
            ui = "SDL",
            extraParams = "-rtc base=localtime",
            notes = listOf(
                "Uses legacy VGA 64MB, not virtio/virgl.",
                "Requires guest-side BOXV9x/SoftGPU/qemu-3dfx style driver or wrapper setup.",
            ),
        ),
        GuestOsProfile(
            title = "Windows 2000 / XP",
            description = "i440fx, IDE, AC97, VMware SVGA",
            machineType = "pc",
            cpu = "pentium3",
            memoryMb = 512,
            vga = "vmware",
            soundCard = "ac97",
            network = "User",
            nic = "rtl8139",
            mouse = "usb-tablet",
            hdaInterface = "ide",
            cdInterface = "ide",
            disableTsc = true,
            notes = listOf("VMware SVGA is a 2D compatibility path for this profile."),
        ),
        GuestOsProfile(
            title = "Windows 7",
            description = "i440fx, SATA-like IDE baseline, e1000, std VGA",
            machineType = "pc",
            cpu = "core2duo",
            cpuCores = 2,
            memoryMb = 2048,
            vga = "std",
            soundCard = "hda",
            network = "User",
            nic = "e1000",
            mouse = "usb-tablet",
            hdaInterface = "ide",
            cdInterface = "ide",
            disableTsc = false,
            notes = listOf("Install matching guest drivers before switching storage or GPU to virtio."),
        ),
        GuestOsProfile(
            title = "Windows 10",
            description = "q35, virtio storage/network, virtio GPU",
            machineType = "q35",
            cpu = "qemu64",
            cpuCores = 2,
            memoryMb = 4096,
            vga = "virtio-gpu-pci",
            soundCard = "hda",
            network = "User",
            nic = "virtio",
            mouse = "usb-tablet",
            hdaInterface = "virtio",
            cdInterface = "ide",
            disableTsc = false,
            ui = "SDL",
            notes = listOf("Requires virtio guest drivers for storage/network devices."),
        ),
        GuestOsProfile(
            title = "Windows 11 + 3D",
            description = "q35, virtio, virgl/SDL GL path",
            machineType = "q35",
            cpu = "qemu64",
            cpuCores = 4,
            memoryMb = 4096,
            vga = "virtio-gpu-pci,virgl=on",
            soundCard = "hda",
            network = "User",
            nic = "virtio",
            mouse = "usb-tablet",
            hdaInterface = "virtio",
            cdInterface = "ide",
            disableTsc = false,
            ui = "SDL",
            notes = listOf(
                "Requires a native build with USE_VIRGL=true and OpenGL/virglrenderer dependencies.",
                "Requires guest driver support for virtio GPU/virgl.",
            ),
        ),
    )

    @JvmStatic
    fun promptApply(activity: Activity, machine: Machine?, viewListener: ViewListener?) {
        if (machine == null || viewListener == null) {
            ToastUtils.toastShort(activity, activity.getString(R.string.SelectAMachineFirst))
            return
        }
        if (LimboApplication.arch != Config.Arch.x86 && LimboApplication.arch != Config.Arch.x86_64) {
            ToastUtils.toastShort(activity, activity.getString(R.string.guest_profile_x86_only))
            return
        }

        val labels = x86WindowsProfiles.map { "${it.title}\n${it.description}" }
        val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, labels)
        AlertDialog.Builder(activity)
            .setTitle(R.string.guest_profile)
            .setAdapter(adapter) { dialog: DialogInterface, which: Int ->
                val profile = x86WindowsProfiles[which]
                dialog.dismiss()
                confirmApply(activity, profile, viewListener)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmApply(activity: Activity, profile: GuestOsProfile, viewListener: ViewListener) {
        AlertDialog.Builder(activity)
            .setTitle(profile.title)
            .setMessage(profile.summary())
            .setPositiveButton(R.string.Apply) { dialog: DialogInterface, _: Int ->
                profile.applyTo(viewListener)
                ToastUtils.toastShort(
                    activity,
                    activity.getString(R.string.guest_profile_applied, profile.title)
                )
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private data class GuestOsProfile(
        val title: String,
        val description: String,
        val machineType: String,
        val cpu: String,
        val cpuCores: Int = 1,
        val memoryMb: Int,
        val vga: String,
        val soundCard: String,
        val network: String,
        val nic: String,
        val mouse: String,
        val hdaInterface: String,
        val cdInterface: String,
        val disableTsc: Boolean,
        val ui: String? = null,
        val extraParams: String? = null,
        val notes: List<String> = emptyList(),
    ) {
        fun summary(): String {
            return buildString {
                appendLine(description)
                appendLine()
                appendLine("Machine: $machineType")
                appendLine("CPU: $cpu, cores: $cpuCores")
                appendLine("Memory: ${memoryMb}MB")
                appendLine("GPU: $vga")
                appendLine("Audio: $soundCard")
                appendLine("Network: $network / $nic")
                appendLine("Disk: HDA=$hdaInterface, CD=$cdInterface")
                if (ui != null) {
                    appendLine("UI: $ui")
                }
                if (extraParams != null) {
                    appendLine("Extra params: $extraParams")
                }
                if (notes.isNotEmpty()) {
                    appendLine()
                    appendLine("Notes:")
                    notes.forEach { appendLine("- $it") }
                }
            }.trim()
        }

        fun applyTo(viewListener: ViewListener) {
            viewListener.onFieldChange(MachineProperty.MACHINETYPE, machineType)
            viewListener.onFieldChange(MachineProperty.CPU, cpu)
            viewListener.onFieldChange(MachineProperty.CPUNUM, cpuCores)
            viewListener.onFieldChange(MachineProperty.MEMORY, memoryMb)
            viewListener.onFieldChange(MachineProperty.VGA, vga)
            viewListener.onFieldChange(MachineProperty.SOUNDCARD, soundCard)
            viewListener.onFieldChange(MachineProperty.NETCONFIG, network)
            viewListener.onFieldChange(MachineProperty.NICCONFIG, nic)
            viewListener.onFieldChange(MachineProperty.MOUSE, mouse)
            viewListener.onFieldChange(MachineProperty.DISABLE_TSC, disableTsc)
            viewListener.onFieldChange(
                MachineProperty.MEDIA_INTERFACE,
                arrayOf<Any>(MachineProperty.HDA, hdaInterface)
            )
            viewListener.onFieldChange(
                MachineProperty.MEDIA_INTERFACE,
                arrayOf<Any>(MachineProperty.CDROM, cdInterface)
            )
            if (ui != null && Config.enable_SDL) {
                viewListener.onFieldChange(MachineProperty.UI, ui)
            }
            if (extraParams != null) {
                viewListener.onFieldChange(MachineProperty.EXTRA_PARAMS, extraParams)
            }
        }
    }
}
