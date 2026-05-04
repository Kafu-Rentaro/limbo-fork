package jp.oso.limbofork.fuwa.arm

import android.os.Bundle
import com.max2idea.android.limbo.links.LinksManager
import com.max2idea.android.limbo.log.Logger
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboActivity
import com.max2idea.android.limbo.main.LimboApplication

class LimboEmuActivity : LimboActivity() {
    override fun onCreate(bundle: Bundle?) {
        LimboApplication.arch = Config.Arch.arm64
        Config.clientClass = javaClass
        Config.enableKVM = true
        Config.enableEmulatedFloppy = false
        Config.enableEmulatedSDCard = true
        Config.enableMTTCG = LimboApplication.isHost64Bit() && Config.enableMTTCG
        Config.machineFolder = Config.machineFolder + "other/arm_machines/"
        Config.osImages.put(
            getString(R.string.DebianArmLinux),
            LinksManager.LinkInfo(
                getString(R.string.DebianArmLinux),
                getString(R.string.DebianArmLinuxDescr),
                "https://github.com/limboemu/limbo/wiki/Debian-ARM-Linux",
                LinksManager.LinkType.ISO
            )
        )
        super.onCreate(bundle)
        Logger.setupLogFile("/limbo/limbo-arm-log.txt")
    }

    override fun loadQEMULib() {
        try {
            System.loadLibrary("qemu-system-arm")
        } catch (ex: Error) {
            System.loadLibrary("qemu-system-aarch64")
        }
    }
}
