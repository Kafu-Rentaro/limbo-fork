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
package com.max2idea.android.limbo.qmp

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboApplication
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

/**
 * Small QMP client used for QEMU control commands such as VNC password changes,
 * removable-media changes, migration state, and VM power operations.
 */
object QmpClient {
    private const val TAG = "QmpClient"
    private const val REQUEST_COMMAND_MODE = "{ \"execute\": \"qmp_capabilities\" }"
    private const val SOCKET_TIMEOUT_MS = 5000
    private const val RESPONSE_TRIES = 3
    private const val RESPONSE_RETRY_DELAY_MS = 1000L

    private var external = false

    @JvmStatic
    fun setExternal(value: Boolean) {
        external = value
    }

    @JvmStatic
    @Synchronized
    fun sendCommand(command: String): String? {
        var response: String? = null
        var tcpSocket: Socket? = null
        var localSocket: LocalSocket? = null
        var out: PrintWriter? = null
        var input: BufferedReader? = null

        try {
            if (external) {
                tcpSocket = Socket(Config.QMPServer, Config.QMPPort).apply {
                    soTimeout = SOCKET_TIMEOUT_MS
                }
                out = PrintWriter(tcpSocket.getOutputStream(), true)
                input = BufferedReader(InputStreamReader(tcpSocket.getInputStream()))
            } else {
                localSocket = LocalSocket().apply {
                    val address = LocalSocketAddress(
                        LimboApplication.getLocalQMPSocketPath(),
                        LocalSocketAddress.Namespace.FILESYSTEM,
                    )
                    connect(address)
                    soTimeout = SOCKET_TIMEOUT_MS
                }
                out = PrintWriter(localSocket.getOutputStream(), true)
                input = BufferedReader(InputStreamReader(localSocket.getInputStream()))
            }

            val writer = requireNotNull(out)
            val reader = requireNotNull(input)
            sendRequest(writer, REQUEST_COMMAND_MODE)
            response = tryGetResponse(reader)
            sendRequest(writer, command)
            response = tryGetResponse(reader)
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            out?.close()
            try {
                input?.close()
                tcpSocket?.close()
                localSocket?.close()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        if (Config.debugQmp) {
            Log.d(TAG, "Response: $response")
        }
        return response
    }

    private fun tryGetResponse(input: BufferedReader): String {
        var response = getResponse(input)
        var trial = 0
        while (response.isEmpty() && trial < RESPONSE_TRIES) {
            Thread.sleep(RESPONSE_RETRY_DELAY_MS)
            trial++
            response = getResponse(input)
        }
        return response
    }

    private fun sendRequest(out: PrintWriter, request: String) {
        if (Config.debugQmp) {
            Log.d(TAG, "QMP request$request")
        }
        out.println(request)
    }

    private fun getResponse(input: BufferedReader): String {
        val stringBuilder = StringBuilder()
        try {
            while (true) {
                val line = input.readLine() ?: break
                if (Config.debugQmp) {
                    Log.d(TAG, "QMP response: $line")
                }
                val json = JSONObject(line)
                val hasReturn = line.contains("return") && json.has("return")
                val hasError = line.contains("error") && json.has("error")
                stringBuilder.append(line).append('\n')
                if (hasReturn || hasError) {
                    break
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Could not get Response: ${ex.message}")
            if (Config.debugQmp) {
                ex.printStackTrace()
            }
        }
        return stringBuilder.toString()
    }

    @Suppress("unused")
    private fun getQueryMigrateResponse(input: BufferedReader): String {
        val stringBuilder = StringBuilder()
        try {
            while (true) {
                val line = input.readLine() ?: break
                if (Config.debugQmp) {
                    Log.d(TAG, "QMP query-migrate response: $line")
                }
                val json = JSONObject(line)
                val hasReturn = json.has("return")
                val hasError = json.has("error")
                if (hasReturn) {
                    break
                }
                stringBuilder.append(line).append('\n')
                if (hasError) {
                    break
                }
            }
        } catch (_: Exception) {
        }
        return stringBuilder.toString()
    }

    @JvmStatic
    fun getMigrateCommand(block: Boolean, inc: Boolean, uri: String): String =
        "{\"execute\":\"migrate\",\"arguments\":{\"blk\":$block,\"inc\":$inc,\"uri\":\"$uri\"},\"id\":\"limbo\"}"

    @JvmStatic
    fun getChangeVncPasswdCommand(passwd: String): String =
        "{\"execute\": \"change\", \"arguments\": { \"device\": \"vnc\", \"target\": \"password\", \"arg\": \"$passwd\" } }"

    @JvmStatic
    fun getEjectDeviceCommand(dev: String): String =
        "{ \"execute\": \"eject\", \"arguments\": { \"device\": \"$dev\" } }"

    @JvmStatic
    fun getChangeDeviceCommand(dev: String, value: String): String =
        "{ \"execute\": \"change\", \"arguments\": { \"device\": \"$dev\", \"target\": \"$value\" } }"

    @JvmStatic
    fun getQueryMigrationCommand(): String = "{ \"execute\": \"query-migrate\" }"

    @JvmStatic
    fun getStopVMCommand(): String = "{ \"execute\": \"stop\" }"

    @JvmStatic
    fun getContinueVMCommand(): String = "{ \"execute\": \"cont\" }"

    @JvmStatic
    fun getPowerDownCommand(): String = "{ \"execute\": \"system_powerdown\" }"

    @JvmStatic
    fun getResetCommand(): String = "{ \"execute\": \"system_reset\" }"

    @JvmStatic
    fun getStateCommand(): String = "{ \"execute\": \"query-status\" }"
}
