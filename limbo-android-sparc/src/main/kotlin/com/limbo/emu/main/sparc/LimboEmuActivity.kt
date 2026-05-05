package jp.oso.limbofork.fuwa.sparc

import android.os.Bundle
import com.max2idea.android.limbo.links.LinksManager
import com.max2idea.android.limbo.log.Logger
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboActivity
import com.max2idea.android.limbo.main.LimboApplication

class LimboEmuActivity : LimboActivity() {
    override fun onCreate(bundle: Bundle?) {
        LimboApplication.arch = Config.Arch.sparc
        Config.clientClass = javaClass
        Config.enableKVM = false
        Config.enableMTTCG = false
        Config.enableSDLSound = false
        Config.enableEmulatedSDCard = false
        Config.machineFolder = Config.machineFolder + "other/sparc_machines/"
        Config.osImages.put(
            getString(R.string.DebianSparcLinux),
            LinksManager.LinkInfo(
                getString(R.string.DebianSparcLinux),
                getString(R.string.DebianSparcLinuxDescr),
                "https://github.com/limboemu/limbo/wiki/Debian-Sparc-Linux",
                LinksManager.LinkType.ISO
            )
        )
        super.onCreate(bundle)
        Logger.setupLogFile("/limbo/limbo-sparc-log.txt")
    }

    override fun loadQEMULib() {
        try {
            System.loadLibrary("qemu-system-sparc")
        } catch (ex: Error) {
            System.loadLibrary("qemu-system-sparc64")
        }
    }
}
