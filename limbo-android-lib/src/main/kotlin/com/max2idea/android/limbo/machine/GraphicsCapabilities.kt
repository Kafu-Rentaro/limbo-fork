package com.max2idea.android.limbo.machine

object GraphicsCapabilities {
    const val LEGACY_VGA_64MB = "VGA,vgamem_mb=64"
    const val VIRTIO_VGA = "virtio-vga"
    const val VIRTIO_VGA_GL = "virtio-vga-gl"
    const val VIRTIO_GPU_PCI = "virtio-gpu-pci"
    const val VIRTIO_GPU_PCI_VIRGL = "virtio-gpu-pci,virgl=on"

    @JvmStatic
    fun isDeviceBackedGpu(vga: String?): Boolean {
        return vga != null && (
            vga.startsWith("virtio-gpu") ||
                vga.startsWith("virtio-vga") ||
                vga.startsWith("VGA") ||
                vga.startsWith("isa-vga") ||
                vga.startsWith("secondary-vga")
            )
    }

    @JvmStatic
    fun isVirglGpu(vga: String?): Boolean {
        return vga != null && (vga.contains("virgl=on") || vga.endsWith("-gl"))
    }
}
