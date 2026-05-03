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

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.dialog.DialogUtils
import com.max2idea.android.limbo.files.FileUtils
import com.max2idea.android.limbo.help.Help
import com.max2idea.android.limbo.install.Installer
import com.max2idea.android.limbo.machine.Machine
import com.max2idea.android.limbo.machine.MachineAction
import com.max2idea.android.limbo.network.NetworkUtils
import com.max2idea.android.limbo.toast.ToastUtils
import java.io.IOException
import java.util.ArrayList

object LimboActivityCommon {
    @JvmStatic
    fun promptStopVM(activity: Activity, viewListener: ViewListener) {
        activity.runOnUiThread {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ShutdownVM)
                .setMessage(R.string.ShutdownVMWarning)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewListener.onAction(MachineAction.STOP_VM, null)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    @JvmStatic
    fun promptPausedErrorVM(activity: Activity, msg: String?, viewListener: ViewListener) {
        activity.runOnUiThread {
            val message = msg ?: activity.getString(R.string.CouldNotPauseVMViewLogFileDetails)
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.Error)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    Thread {
                        viewListener.onAction(MachineAction.CONTINUE_VM, 0)
                    }.start()
                }
                .show()
        }
    }

    @JvmStatic
    fun promptPause(activity: Activity, viewListener: ViewListener) {
        if (!LimboSettingsManager.getEnableQmp(activity)) {
            ToastUtils.toastShort(activity, activity.getString(R.string.EnableQMPForSavingVMState))
            return
        }

        activity.runOnUiThread {
            val stateView = TextView(activity).apply {
                setText(R.string.pauseVMWarning)
                setPadding(20, 20, 20, 20)
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.PauseVM)
                .setView(stateView)
                .setPositiveButton(R.string.Pause) { _, _ ->
                    viewListener.onAction(MachineAction.PAUSE_VM, null)
                }
                .show()
        }
    }

    @JvmStatic
    fun promptResetVM(activity: Activity, viewListener: ViewListener) {
        activity.runOnUiThread {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ResetVM)
                .setMessage(R.string.ResetVMWarning)
                .setPositiveButton(R.string.Yes) { _, _ ->
                    viewListener.onAction(MachineAction.RESET_VM, null)
                }
                .setNegativeButton(R.string.No, null)
                .show()
        }
    }

    @JvmStatic
    fun promptPausedVM(activity: Activity, viewListener: ViewListener) {
        activity.runOnUiThread {
            MaterialAlertDialogBuilder(activity)
                .setCancelable(false)
                .setTitle(R.string.Paused)
                .setMessage(R.string.VMPausedPressOkToExit)
                .setPositiveButton(R.string.Ok) { _, _ ->
                    viewListener.onAction(MachineAction.STOP_VM, null)
                }
                .show()
        }
    }

    @JvmStatic
    fun promptVNCServer(context: Context, msg: String?, viewListener: ViewListener) {
        Handler(Looper.getMainLooper()).post {
            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.vncServer))
                .setMessage(
                    context.getString(R.string.vncServer) + ": " +
                        NetworkUtils.getVNCAddress(context) +
                        ":" + Config.defaultVNCPort +
                        "\n\n" + msg.orEmpty(),
                )
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewListener.onAction(MachineAction.START_VM, null)
                }
                .setNegativeButton(R.string.Cancel, null)
                .show()
        }
    }

    @JvmStatic
    fun promptMachinesImported(activity: Activity, machines: ArrayList<Machine>) {
        activity.runOnUiThread {
            DialogUtils.UIAlert(
                activity,
                activity.getString(R.string.importMachines),
                activity.getString(R.string.machinesImported) +
                    ": ${machines.size}\n" +
                    activity.getString(R.string.reassignDiskFilesInstructions),
                16,
                false,
                activity.getString(android.R.string.ok),
                DialogInterface.OnClickListener { _, _ -> },
                null,
                null,
                null,
                null,
            )
        }
    }

    @JvmStatic
    fun promptLicense(activity: Activity, title: String?, body: String?) {
        activity.runOnUiThread {
            val textView = TextView(activity).apply {
                text = body.orEmpty()
                textSize = 10f
                setPadding(20, 20, 20, 20)
            }
            val scrollView = ScrollView(activity).apply {
                addView(textView)
            }
            val alertDialog = MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton(R.string.IAcknowledge) { _, _ ->
                    if (LimboSettingsManager.isFirstLaunch(activity)) {
                        Installer.installFiles(activity, true)
                        Help.showHelp(activity)
                        showChangelog(activity)
                    }
                    LimboSettingsManager.setFirstLaunch(activity)
                }
                .create()
            alertDialog.setOnCancelListener {
                if (LimboSettingsManager.isFirstLaunch(activity)) {
                    finishParentOrActivity(activity)
                }
            }
            alertDialog.show()
        }
    }

    @JvmStatic
    fun tapNotSupported(activity: Activity, userid: String?) {
        activity.runOnUiThread {
            DialogUtils.UIAlert(
                activity,
                activity.getString(R.string.tapUserId) + ": " + userid.orEmpty(),
                activity.getString(R.string.tapNotSupportInstructions),
            )
        }
    }

    @JvmStatic
    fun promptTap(activity: Activity, userid: String?) {
        val okListener = DialogInterface.OnClickListener { _, _ -> }
        val helpListener = DialogInterface.OnClickListener { _, _ ->
            goToURL(activity, Config.faqLink)
        }

        activity.runOnUiThread {
            DialogUtils.UIAlert(
                activity,
                activity.getString(R.string.TapDeviceFound),
                activity.getString(R.string.tunDeviceWarning) + ": " + userid.orEmpty() + "\n",
                16,
                false,
                activity.getString(android.R.string.ok),
                okListener,
                null,
                null,
                activity.getString(R.string.TAPHelp),
                helpListener,
            )
        }
    }

    @JvmStatic
    fun goToURL(context: Context, url: String?) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        }
        context.startActivity(intent)
    }

    @JvmStatic
    fun onNetworkUser(activity: Activity) {
        val okListener = DialogInterface.OnClickListener { _, _ -> }
        val helpListener = DialogInterface.OnClickListener { _, _ ->
            goToURL(activity, Config.faqLink)
        }

        activity.runOnUiThread {
            DialogUtils.UIAlert(
                activity,
                activity.getString(R.string.network),
                activity.getString(R.string.externalNetworkWarning),
                16,
                false,
                activity.getString(android.R.string.ok),
                okListener,
                null,
                null,
                activity.getString(R.string.faq),
                helpListener,
            )
        }
    }

    @JvmStatic
    fun showChangelog(activity: Activity) {
        try {
            val textView = TextView(activity).apply {
                setPadding(20, 20, 20, 20)
                text = FileUtils.LoadFile(activity, "CHANGELOG", false)
                setBackgroundColor(Color.WHITE)
                setTextColor(Color.BLACK)
            }
            val scrollView = ScrollView(activity).apply {
                addView(textView)
            }
            val alertDialog = MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.CHANGELOG))
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .create()
            alertDialog.setCanceledOnTouchOutside(false)
            alertDialog.show()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION")
    private fun finishParentOrActivity(activity: Activity) {
        val parent = activity.parent
        if (parent != null) {
            parent.finish()
        } else {
            activity.finish()
        }
    }
}
