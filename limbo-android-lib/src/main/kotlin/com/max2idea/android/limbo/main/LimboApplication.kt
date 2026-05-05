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
package com.max2idea.android.limbo.main

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import com.google.android.material.color.DynamicColors
import com.max2idea.android.limbo.files.FileUtils
import com.max2idea.android.limbo.machine.Dispatcher
import com.max2idea.android.limbo.machine.FavOpenHelper
import com.max2idea.android.limbo.machine.MachineOpenHelper
import com.max2idea.android.limbo.toast.ToastUtils
import java.io.File

/**
 * We use the application context for the initialization of some of the storage
 * and controller implementations.
 */
class LimboApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        DynamicColors.applyToActivitiesIfAvailable(this)
        try {
            Class.forName("android.os.AsyncTask")
        } catch (_: Throwable) {
            // ignored
        }
        MachineOpenHelper.initialize(this)
        FavOpenHelper.initialize(this)
        setupFolders()
    }

    private fun setupFolders() {
        Config.storagedir = Environment.getExternalStorageDirectory().toString()
        val folder = File(getTmpFolder())
        if (!folder.exists()) {
            val result = folder.mkdirs()
            if (!result) {
                Log.e(TAG, "Could not create temp folder: ${folder.path}")
            }
        }
    }

    companion object {
        private const val TAG = "LimboApplication"

        @JvmField
        var arch: Config.Arch = Config.Arch.x86

        private var instance: Context? = null
        private var qemuVersionString: String? = null
        private var qemuVersion = 0
        private var limboVersionString: String? = null
        private var limboVersion = 0

        @JvmStatic
        fun getInstance(): Context = requireNotNull(instance) {
            "LimboApplication has not been created yet"
        }

        @JvmStatic
        fun setupEnv(context: Context) {
            val appContext = context.applicationContext ?: context
            var lastPackageError: Exception? = null
            val packageNames = linkedSetOf(appContext.packageName, context.packageName)
            appContext.applicationInfo?.packageName?.let { packageNames.add(it) }

            for (packageName in packageNames) {
                try {
                    val packageInfo = appContext.packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_META_DATA,
                    )
                    limboVersion = getPackageVersionCode(packageInfo)
                    limboVersionString = packageInfo.versionName
                    Log.d(TAG, "Limbo Version: $limboVersion")
                    Log.d(TAG, "Limbo Version Code: $limboVersionString")
                    lastPackageError = null
                    break
                } catch (e: PackageManager.NameNotFoundException) {
                    lastPackageError = e
                }
            }

            if (lastPackageError != null) {
                Log.w(TAG, "Could not resolve app package version; using fallback", lastPackageError)
                limboVersion = 1
                limboVersionString = "0.0.1"
            }

            try {
                qemuVersionString = FileUtils.LoadFile(appContext, "QEMU_VERSION", false)
                val qemuVersionParts = requireNotNull(qemuVersionString).trim().split(".")
                qemuVersion = qemuVersionParts[0].toInt() * 10000 +
                    qemuVersionParts[1].toInt() * 100 +
                    qemuVersionParts[2].toInt()
                Log.d(TAG, "Qemu Version: $qemuVersionString")
                Log.d(TAG, "Qemu Version Number: $qemuVersion")
                HostCapabilities.logHostCapabilities()
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.toastShort(context, "Could not load QEMU version information: $e")
            }
        }

        @JvmStatic
        fun getUserId(context: Context): String {
            var userId = "None"
            try {
                val packageName = context.packageName
                val appInfo = context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.GET_META_DATA,
                )
                userId = appInfo.uid.toString()
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
            return userId
        }

        @JvmStatic
        fun isHost64Bit(): Boolean = Build.SUPPORTED_64_BIT_ABIS?.isNotEmpty() == true

        @JvmStatic
        fun isHostX86_64(): Boolean = Build.SUPPORTED_64_BIT_ABIS?.contains("x86_64") == true

        @JvmStatic
        fun isHostX86(): Boolean = Build.SUPPORTED_32_BIT_ABIS?.contains("x86") == true

        @JvmStatic
        fun isHostArm(): Boolean = Build.SUPPORTED_32_BIT_ABIS?.contains("armeabi-v7a") == true

        @JvmStatic
        fun isHostArmv8(): Boolean = Build.SUPPORTED_64_BIT_ABIS?.contains("arm64-v8a") == true

        @JvmStatic
        fun getViewListener(): ViewListener = Dispatcher.getInstance()

        @JvmStatic
        fun getBasefileDir(): String = "${getInstance().cacheDir}/limbo/"

        @JvmStatic
        fun getTmpFolder(): String = "${getBasefileDir()}var/tmp"

        @JvmStatic
        fun getMachineDir(): String = getBasefileDir() + Config.machineFolder

        @JvmStatic
        fun getLocalQMPSocketPath(): String = "${getInstance().cacheDir}/qmpsocket"

        @JvmStatic
        fun getQemuVersionString(): String? = qemuVersionString

        @JvmStatic
        fun getQemuVersion(): Int = qemuVersion

        @JvmStatic
        fun getLimboVersionString(): String? = limboVersionString

        @JvmStatic
        fun getLimboVersion(): Int = limboVersion

        @Suppress("DEPRECATION")
        private fun getPackageVersionCode(packageInfo: PackageInfo): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                packageInfo.versionCode
            }
        }
    }
}
