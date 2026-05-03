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
package com.max2idea.android.limbo.network

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboSettingsManager
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.SocketException
import java.net.URL

object NetworkUtils {
    @JvmStatic
    fun openURL(activity: Activity, url: String?) {
        try {
            val fileIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
            }
            activity.startActivity(fileIntent)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @JvmStatic
    fun getVNCAddress(context: Context): String {
        val address = if (LimboSettingsManager.getEnableExternalVNC(context)) {
            getExternalIpAddress()
        } else {
            null
        }
        return address ?: getLocalIpAddress()
    }

    @JvmStatic
    fun getLocalIpAddress(): String = Config.defaultVNCHost

    @JvmStatic
    fun getExternalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    val hostAddress = address.hostAddress
                    if (!address.isLoopbackAddress && hostAddress != null && hostAddress.contains(".")) {
                        return hostAddress
                    }
                }
            }
        } catch (ex: SocketException) {
            ex.printStackTrace()
        }
        return null
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getContentFromUrl(urlPath: String): ByteArray {
        val connection = URL(urlPath).openConnection() as HttpURLConnection
        try {
            connection.connect()
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) {
                        break
                    }
                    output.write(buffer, 0, read)
                }
                return output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}
