package jp.oso.limbofork.fuwa

import android.os.Bundle
import com.max2idea.android.limbo.links.LinksManager
import com.max2idea.android.limbo.log.Logger
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboActivity
import com.max2idea.android.limbo.main.LimboApplication

class LimboEmuActivity : LimboActivity() {
    override fun onCreate(bundle: Bundle?) {
        LimboApplication.arch = Config.Arch.x86_64
        Config.clientClass = javaClass
        Config.enableKVM = true
        Config.enableMTTCG = LimboApplication.isHost64Bit() && Config.enableMTTCG
        Config.osImages.put(
            getString(R.string.SlaxLinux),
            LinksManager.LinkInfo(
                getString(R.string.SlaxLinux),
                getString(R.string.SlaxLinuxDescr),
                "https://github.com/limboemu/limbo/wiki/Slax",
                LinksManager.LinkType.ISO
            )
        )
        Config.osImages.put(
            getString(R.string.SlitazLinux),
            LinksManager.LinkInfo(
                getString(R.string.SlitazLinux),
                getString(R.string.SlitazLinuxDescr),
                "https://github.com/limboemu/limbo/wiki/Slitaz",
                LinksManager.LinkType.ISO
            )
        )
        Config.osImages.put(
            getString(R.string.DSLLinux),
            LinksManager.LinkInfo(
                getString(R.string.DSLLinux),
                getString(R.string.DSLLinuxDescr),
                "https://github.com/limboemu/limbo/wiki/DSL-linux",
                LinksManager.LinkType.ISO
            )
        )
        Config.osImages.put(
            getString(R.string.DebianLinux),
            LinksManager.LinkInfo(
                getString(R.string.DebianLinux),
                getString(R.string.DebianLinuxDescr),
                "https://github.com/limboemu/limbo/wiki/Debian-Linux",
                LinksManager.LinkType.ISO
            )
        )
        Config.osImages.put(
            "Trinux",
            LinksManager.LinkInfo(
                getString(R.string.Trinux),
                getString(R.string.TrinuxDescr),
                "https://github.com/limboemu/limbo/wiki/Trinux",
                LinksManager.LinkType.ISO
            )
        )
        Config.osImages.put(
            getString(R.string.Freedos),
            LinksManager.LinkInfo(
                getString(R.string.Freedos),
                getString(R.string.FreedosDescr),
                "https://github.com/limboemu/limbo/wiki/FreeDOS",
                LinksManager.LinkType.ISO
            )
        )
        Config.osImages.put(
            getString(R.string.KolibriOS),
            LinksManager.LinkInfo(
                getString(R.string.KolibriOS),
                getString(R.string.KolibriOSDescr),
                "https://github.com/limboemu/limbo/wiki/KolibriOS",
                LinksManager.LinkType.ISO
            )
        )
        super.onCreate(bundle)
        Logger.setupLogFile("/limbo/limbo-x86-log.txt")
    }

    override fun loadQEMULib() {
        try {
            System.loadLibrary("qemu-system-i386")
        } catch (ex: Error) {
            System.loadLibrary("qemu-system-x86_64")
        }
    }
}
