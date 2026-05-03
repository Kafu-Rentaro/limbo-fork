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

import android.content.Context
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.install.Installer
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboApplication
import java.util.ArrayList

/**
 * A simple utility class to retrieve often long architecture attribute lists.
 */
object ArchDefinitions {
    @JvmStatic
    fun getSoundcards(context: Context): ArrayList<String> {
        return arrayListFromResource(context, R.raw.common_soundcards)
    }

    @JvmStatic
    fun getNetworkDevices(context: Context): ArrayList<String> {
        val commonNetworkCards = arrayListFromResource(context, R.raw.common_nic_cards)
        val networkCards = ArrayList<String>()
        when (LimboApplication.arch) {
            Config.Arch.x86,
            Config.Arch.x86_64,
            Config.Arch.ppc,
            Config.Arch.ppc64 -> {
                networkCards.add("Default")
                networkCards.addAll(commonNetworkCards)
            }
            Config.Arch.arm,
            Config.Arch.arm64 -> {
                networkCards.add("Default")
                networkCards.addAll(commonNetworkCards)
                networkCards.addAll(Installer.getAttrs(context, R.raw.arm_nic_cards).asList())
            }
            Config.Arch.sparc,
            Config.Arch.sparc64 -> {
                networkCards.add("Default")
                networkCards.addAll(Installer.getAttrs(context, R.raw.sparc_nic_cards).asList())
            }
        }
        return networkCards
    }

    @JvmStatic
    fun getVGAValues(context: Context): ArrayList<String> {
        val vgaValues = ArrayList<String>()
        when (LimboApplication.arch) {
            Config.Arch.x86,
            Config.Arch.x86_64,
            Config.Arch.arm,
            Config.Arch.arm64,
            Config.Arch.ppc,
            Config.Arch.ppc64 -> vgaValues.add("std")
            else -> Unit
        }

        if (LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64) {
            vgaValues.add("cirrus")
            vgaValues.add("vmware")
            vgaValues.add("virtio-vga")
            vgaValues.add("virtio-vga-gl")
            vgaValues.add("virtio-gpu-pci")
            vgaValues.add("virtio-gpu-pci,virgl=on")
        }

        if (LimboApplication.arch == Config.Arch.sparc || LimboApplication.arch == Config.Arch.sparc64) {
            vgaValues.add("cg3")
        }

        if (LimboApplication.arch == Config.Arch.arm || LimboApplication.arch == Config.Arch.arm64) {
            vgaValues.add("virtio-gpu-pci")
            vgaValues.add("virtio-gpu-pci,virgl=on")
        }

        vgaValues.add("nographic")
        return vgaValues
    }

    @JvmStatic
    fun getKeyboardValues(context: Context): ArrayList<String> {
        return arrayListOf("en-us")
    }

    @JvmStatic
    fun getMouseValues(context: Context): ArrayList<String> {
        return arrayListOf(
            "ps2",
            "usb-mouse",
            "usb-tablet ${context.getString(R.string.fixesMouseParen)}"
        )
    }

    @JvmStatic
    fun getUIValues(): ArrayList<String> {
        val values = arrayListOf("VNC")
        if (Config.enable_SDL) {
            values.add("SDL")
        }
        return values
    }

    @JvmStatic
    fun getMachineValues(context: Context): ArrayList<String> {
        return arrayListOf("None", "New")
    }

    @JvmStatic
    fun getCpuValues(context: Context): ArrayList<String> {
        val values = ArrayList<String>()
        when (LimboApplication.arch) {
            Config.Arch.x86,
            Config.Arch.x86_64 -> {
                values.add("Default")
                values.addAll(Installer.getAttrs(context, R.raw.x86_cpu).asList())
            }
            Config.Arch.arm,
            Config.Arch.arm64 -> {
                values.add("Default")
                values.addAll(Installer.getAttrs(context, R.raw.arm_cpu).asList())
            }
            Config.Arch.ppc,
            Config.Arch.ppc64 -> {
                values.add("Default")
                values.addAll(Installer.getAttrs(context, R.raw.ppc_cpu).asList())
            }
            Config.Arch.sparc,
            Config.Arch.sparc64 -> {
                values.add("Default")
                values.addAll(Installer.getAttrs(context, R.raw.arm_cpu).asList())
            }
        }

        when (LimboApplication.arch) {
            Config.Arch.x86,
            Config.Arch.x86_64,
            Config.Arch.arm,
            Config.Arch.arm64 -> values.add("host")
            else -> Unit
        }
        return values
    }

    @JvmStatic
    fun getMachineTypeValues(context: Context): ArrayList<String> {
        val values = ArrayList<String>()
        when (LimboApplication.arch) {
            Config.Arch.x86,
            Config.Arch.x86_64 -> {
                values.add("Default")
                values.addAll(Installer.getAttrs(context, R.raw.x86_machine_types).asList())
            }
            Config.Arch.arm,
            Config.Arch.arm64 -> values.addAll(Installer.getAttrs(context, R.raw.arm_machine_types).asList())
            Config.Arch.ppc,
            Config.Arch.ppc64 -> {
                values.add("Default")
                values.addAll(Installer.getAttrs(context, R.raw.ppc_machine_types).asList())
            }
            Config.Arch.sparc,
            Config.Arch.sparc64 -> {
                values.add("Default")
                values.addAll(Installer.getAttrs(context, R.raw.sparc_machine_types).asList())
            }
        }
        return values
    }

    private fun arrayListFromResource(context: Context, resourceId: Int): ArrayList<String> {
        return ArrayList(Installer.getAttrs(context, resourceId).asList())
    }
}
