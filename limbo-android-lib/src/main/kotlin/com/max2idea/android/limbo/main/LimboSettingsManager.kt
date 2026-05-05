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
@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.max2idea.android.limbo.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.Preference
import android.preference.PreferenceActivity
import android.preference.PreferenceManager
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.network.NetworkUtils
import com.max2idea.android.limbo.toast.ToastUtils
import java.util.HashSet

class LimboSettingsManager : PreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Config.SETTINGS_RETURN_CODE, Intent())
        addPrefs()
        preferenceManager.findPreference("enableVNCPassword")?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    promptVNCPass(this)
                }
                true
            }
        preferenceManager.findPreference("vncPass")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                promptVNCPass(this)
                true
            }
    }

    fun addPrefs() {
        addPreferencesFromResource(R.xml.settings)
        if (Config.enableSoftwareUpdates) {
            addPreferencesFromResource(R.xml.software_updates)
        }
        if (Config.enableImmersiveMode) {
            addPreferencesFromResource(R.xml.immersive)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            addPreferencesFromResource(R.xml.aaudio)
        }
    }

    fun promptVNCPass(activity: Activity) {
        val alertDialog = MaterialAlertDialogBuilder(activity).create()
        alertDialog.setTitle(getString(R.string.VNCPassword))

        val textView = TextView(activity).apply {
            visibility = View.VISIBLE
            setPadding(20, 20, 20, 20)
            text = getString(R.string.vncServer) + ": " + NetworkUtils.getVNCAddress(activity) +
                ":" + Config.defaultVNCPort + "\n" + getString(R.string.externalVNCWarning)
        }

        val passwdView = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSelection(text.length)
            setHint(R.string.Password)
            isEnabled = true
            visibility = View.VISIBLE
            setSingleLine()
        }

        val layout = LinearLayout(this).apply {
            setPadding(20, 20, 20, 20)
            orientation = LinearLayout.VERTICAL
            addView(
                textView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                passwdView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        alertDialog.setView(layout)
        passwdView.tag = false
        passwdView.transformationMethod = PasswordTransformationMethod()
        passwdView.setSelection(passwdView.text.length)

        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(android.R.string.ok)) { _, _ ->
            val password = passwdView.text.toString()
            if (password.trim().isEmpty()) {
                ToastUtils.toastShort(applicationContext, getString(R.string.passwordCannotBeEmpty))
            } else {
                setVNCPass(activity, password)
            }
        }
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.Cancel)) { _, _ ->
            setVNCPass(activity, null)
        }
        alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, "", null as DialogInterface.OnClickListener?)

        alertDialog.setOnCancelListener {
            setVNCPass(activity, null)
        }
        alertDialog.show()

        try {
            val passwordButton = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            passwordButton.visibility = View.VISIBLE
            passwordButton.setBackgroundResource(android.R.drawable.ic_menu_view)
            passwordButton.setOnClickListener {
                val isVisible = passwdView.tag as Boolean
                passwdView.transformationMethod = if (isVisible) {
                    PasswordTransformationMethod()
                } else {
                    HideReturnsTransformationMethod()
                }
                passwdView.tag = !isVisible
                passwdView.setSelection(passwdView.text.length)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    companion object {
        private fun prefs(context: Context): SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)

        @JvmStatic
        fun getDNSServer(context: Context): String =
            prefs(context).getString("dnsServer", Config.defaultDNSServer) ?: Config.defaultDNSServer

        @JvmStatic
        fun setDNSServer(context: Context, dnsServer: String?) {
            prefs(context).edit().putString("dnsServer", dnsServer).apply()
        }

        @JvmStatic
        fun getOrientationSetting(context: Context): Int =
            prefs(context).getString("orientationPref", "0")?.toIntOrNull() ?: 0

        @JvmStatic
        fun getAlwaysShowMenuToolbar(activity: Context): Boolean =
            prefs(activity).getBoolean("AlwaysShowMenuToolbar", false)

        @JvmStatic
        fun getFullscreen(activity: Context): Boolean =
            prefs(activity).getBoolean("ShowFullscreen", true)

        @JvmStatic
        fun getPromptUpdateVersion(context: Context): Boolean {
            if (!Config.enableSoftwareUpdates) {
                return false
            }
            return prefs(context).getBoolean("updateVersionPrompt", Config.defaultCheckNewVersion)
        }

        @JvmStatic
        fun setPromptUpdateVersion(context: Context, value: Boolean) {
            prefs(context).edit().putBoolean("updateVersionPrompt", value).apply()
        }

        @JvmStatic
        fun getPrio(activity: Context): Boolean =
            prefs(activity).getBoolean("HighPrio", false)

        @JvmStatic
        fun getEnableLegacyFileManager(context: Context): Boolean =
            prefs(context).getBoolean("EnableLegacyFileManager", false)

        @JvmStatic
        fun getLastDir(context: Context): String? =
            prefs(context).getString("lastDir", null)

        @JvmStatic
        fun setLastDir(context: Context, imagesPath: String?) {
            prefs(context).edit().putString("lastDir", imagesPath).apply()
        }

        @JvmStatic
        fun getImagesDir(context: Context): String? =
            prefs(context).getString("imagesDir", null)

        @JvmStatic
        fun setImagesDir(context: Context, imagesPath: String?) {
            prefs(context).edit().putString("imagesDir", imagesPath).apply()
        }

        @JvmStatic
        fun getExportDir(context: Context): String? =
            prefs(context).getString("exportDir", null)

        @JvmStatic
        fun setExportDir(context: Context, imagesPath: String?) {
            prefs(context).edit().putString("exportDir", imagesPath).apply()
        }

        @JvmStatic
        fun getSharedDir(context: Context): String {
            val lastDir = Environment.getExternalStorageDirectory().path
            return prefs(context).getString("sharedDir", lastDir) ?: lastDir
        }

        @JvmStatic
        fun setSharedDir(context: Context, lastDir: String?) {
            prefs(context).edit().putString("sharedDir", lastDir).apply()
        }

        @JvmStatic
        fun getExitCode(context: Context): Int =
            prefs(context).getInt("exitCode", Config.EXIT_SUCCESS)

        @JvmStatic
        @SuppressLint("ApplySharedPref")
        fun setExitCode(context: Context, exitCode: Int) {
            prefs(context).edit().putInt("exitCode", exitCode).commit()
        }

        @JvmStatic
        fun isFirstLaunch(context: Context): Boolean =
            prefs(context).getBoolean("firstTime" + LimboApplication.getLimboVersionString(), true)

        @JvmStatic
        fun setFirstLaunch(context: Context) {
            prefs(context).edit()
                .putBoolean("firstTime" + LimboApplication.getLimboVersionString(), false)
                .apply()
        }

        @JvmStatic
        fun getKeyMappers(context: Context): Set<String> =
            prefs(context).getStringSet("keyMappers", HashSet<String>()) ?: HashSet()

        @JvmStatic
        fun setKeyMappers(context: Context, keyMappers: Set<String>?) {
            prefs(context).edit().putStringSet("keyMappers", keyMappers).apply()
        }

        @JvmStatic
        fun getKeyMapperSize(context: Context): Int =
            prefs(context).getString("keyMapperSize", "3")?.toIntOrNull() ?: 3

        @JvmStatic
        fun getVNCEnablePassword(context: Context): Boolean =
            prefs(context).getBoolean("enableVNCPassword", false)

        @JvmStatic
        fun getVNCPass(context: Context): String? =
            prefs(context).getString("vncPass", "")

        @JvmStatic
        fun setVNCPass(context: Context, value: String?) {
            prefs(context).edit().putString("vncPass", value).apply()
        }

        @JvmStatic
        fun getEnableExternalVNC(context: Context): Boolean =
            prefs(context).getBoolean("enableExternalVNC", false)

        @JvmStatic
        fun getEnableQmp(context: Context): Boolean =
            prefs(context).getBoolean("enableQMP", true)

        @JvmStatic
        fun getEnableExternalQMP(context: Context): Boolean =
            prefs(context).getBoolean("enableExternalQMP", false)

        @JvmStatic
        fun getImmersiveMode(context: Context): Boolean =
            prefs(context).getBoolean("immersiveMode", false)

        @JvmStatic
        fun getKeyPressDelay(context: Context): Int =
            prefs(context).getString("keyPressDelay", "100")?.toIntOrNull() ?: 100

        @JvmStatic
        fun getMouseButtonDelay(context: Context): Int =
            prefs(context).getString("mouseButtonDelay", "100")?.toIntOrNull() ?: 100

        @JvmStatic
        fun getEnableAaudio(activity: Context): Boolean =
            prefs(activity).getBoolean("enableAaudio", false)

        @JvmStatic
        fun getDiskCache(context: Context): String =
            prefs(context).getString("diskCachePref", context.getString(R.string.Default))
                ?: context.getString(R.string.Default)

        @JvmStatic
        fun getPreventMouseOutOfBounds(context: Context): Boolean =
            prefs(context).getBoolean("preventMouseOutOfBounds", false)

        @JvmStatic
        fun getIgnoreBreakpointInvalidation(context: Context): Boolean =
            prefs(context).getBoolean("ignoreBreakpointInvalidation", false)
    }
}
