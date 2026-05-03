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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.files.FileUtils
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboSettingsManager
import com.max2idea.android.limbo.network.NetworkUtils
import com.max2idea.android.limbo.toast.ToastUtils

/**
 * Owns the foreground service and background thread that execute the native VM.
 *
 * Android keeps the process alive through this service while VM control stays routed through
 * MachineController and MachineExecutor.
 */
class MachineService : Service() {
    @JvmField
    var limboThread: Thread? = null

    private var builder: NotificationCompat.Builder? = null
    private var notification: Notification? = null
    private var wifiLock: WifiLock? = null
    private var wakeLock: WakeLock? = null

    override fun onCreate() {
        Log.d(TAG, "Creating Service")
        service = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        if (limboThread != null) {
            return START_NOT_STICKY
        }

        if (action == Config.ACTION_START) {
            if (MachineController.getInstance().getMachine() == null) {
                return START_NOT_STICKY
            }

            var text = "${MachineController.getInstance().getMachine().getName()}: VM Running"
            if (MachineController.getInstance().isVNCEnabled()) {
                text += " - ${getString(R.string.vncServer)}"
                text += ": ${NetworkUtils.getVNCAddress(this)}:${Config.defaultVNCPort}"
            }

            setUpAsForeground(text)
            FileUtils.startLogging()

            val group = ThreadGroup("threadGroup")
            limboThread = Thread(
                group,
                { startVM() },
                "LimboThread",
                Config.stackSize,
            ).also { thread ->
                if (LimboSettingsManager.getPrio(this)) {
                    thread.priority = Thread.MAX_PRIORITY
                }
                thread.start()
            }
        }

        return START_NOT_STICKY
    }

    private fun startVM() {
        try {
            Thread.sleep(1000)
        } catch (ex: InterruptedException) {
            ex.printStackTrace()
        }

        Log.d(TAG, "Starting VM: ${MachineController.getInstance().getMachine().getName()}")
        setupLocks()

        MachineController.getInstance().onServiceStarted()
        LimboSettingsManager.setExitCode(this, Config.EXIT_UNKNOWN)

        val result = MachineController.getInstance().start()
        if (result != null) {
            if (result != "VM shutdown") {
                ToastUtils.toastLong(this, result)
                Log.e(TAG, result)
            } else {
                Log.d(TAG, result)
                LimboSettingsManager.setExitCode(this, Config.EXIT_SUCCESS)
            }
            try {
                Thread.sleep(2000)
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
            }
            ToastUtils.toastLong(this, result)
        }

        cleanUp()
        stopService()

        Log.d(TAG, "Exiting Limbo")
        System.exit(0)
    }

    fun cleanUp() {
        FileUtils.close_fds()
    }

    private fun setUpAsForeground(text: String) {
        if (MachineController.getInstance().getMachine() == null) {
            Log.w(TAG, "No Machine selected")
            return
        }

        val intent = Intent(applicationContext, Config.clientClass)
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, pendingFlags)

        builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Config.notificationChannelID,
                Config.notificationChannelName,
                NotificationManager.IMPORTANCE_NONE,
            )
            val notificationService = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationService.createNotificationChannel(channel)
            NotificationCompat.Builder(this, Config.notificationChannelID)
        } else {
            NotificationCompat.Builder(this, "")
        }

        notification = builder
            ?.setContentIntent(pendingIntent)
            ?.setContentTitle(getString(R.string.app_name))
            ?.setContentText(text)
            ?.setSmallIcon(R.drawable.limbo)
            ?.setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.limbo))
            ?.build()
            ?.apply {
                tickerText = text
                flags = flags or Notification.FLAG_ONGOING_EVENT
            }

        notification?.let { startForeground(notifID, it) }
    }

    fun updateServiceNotification(text: String) {
        val currentBuilder = builder ?: return
        currentBuilder.setContentText(text)
        notification = currentBuilder.build()
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notification?.let { notificationManager.notify(notifID, it) }
    }

    private fun setupLocks() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, Config.wifiLockTag).apply {
            setReferenceCounted(false)
            if (!isHeld) {
                acquire()
            }
        }

        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Config.wakeLockTag).apply {
            setReferenceCounted(false)
            if (!isHeld) {
                acquire()
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    private fun releaseLocks() {
        wifiLock?.takeIf { it.isHeld }?.let {
            Log.d(TAG, "Release Wifi lock...")
            it.release()
        }

        wakeLock?.takeIf { it.isHeld }?.let {
            Log.d(TAG, "Release Wake lock...")
            it.release()
        }
    }

    private fun stopService() {
        releaseLocks()
        service?.stopForeground(true)
        service?.stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        LimboSettingsManager.setExitCode(this, Config.EXIT_SUCCESS)
    }

    interface OnStatusChangedListener {
        fun onStatusChanged(
            service: MachineService,
            machine: Machine,
            status: MachineController.MachineStatus,
        )
    }

    companion object {
        const val notifID: Int = 1000

        private const val TAG = "MachineService"
        private var service: MachineService? = null

        @JvmStatic
        fun getService(): MachineService? = service
    }
}
