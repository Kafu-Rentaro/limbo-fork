package jp.oso.limbofork.fuwa.ppc

import android.os.Bundle
import com.max2idea.android.limbo.links.LinksManager
import com.max2idea.android.limbo.log.Logger
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboActivity
import com.max2idea.android.limbo.main.LimboApplication

class LimboEmuActivity : LimboActivity() {
    override fun onCreate(bundle: Bundle?) {
        LimboApplication.arch = Config.Arch.ppc64
        Config.clientClass = javaClass
        Config.enableKVM = false
        Config.enableMTTCG = LimboApplication.isHost64Bit() && Config.enableMTTCG
        Config.enableEmulatedSDCard = false
        Config.machineFolder = Config.machineFolder + "other/ppc_machines/"
        Config.osImages.put(
            getString(R.string.DebianPowerPCLinux),
            LinksManager.LinkInfo(
                getString(R.string.DebianPowerPCLinux),
                getString(R.string.DebianPowerPCLinuxDescr),
                "https://github.com/limboemu/limbo/wiki/Debian-PowerPC-Linux",
                LinksManager.LinkType.ISO
            )
        )
        super.onCreate(bundle)
        Logger.setupLogFile("/limbo/limbo-ppc-log.txt")
    }

    override fun loadQEMULib() {
        try {
            System.loadLibrary("qemu-system-ppc")
        } catch (ex: Error) {
            System.loadLibrary("qemu-system-ppc64")
        }
    }
}
