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

import com.max2idea.android.limbo.links.LinksManager
import java.util.LinkedHashMap

/**
 * Configuration shared by the legacy Java UI, Kotlin helpers, and native launch path.
 */
object Config {
    const val SDL_MOUSE_LEFT = 1
    const val SDL_MOUSE_MIDDLE = 2
    const val SDL_MOUSE_RIGHT = 3
    const val SETTINGS_RETURN_CODE = 1000
    const val FILEMAN_RETURN_CODE = 1002

    const val SDL_REQUEST_CODE = 1007
    const val SDL_QUIT_RESULT_CODE = 1009

    const val OPEN_IMAGE_FILE_REQUEST_CODE = 2001
    const val OPEN_IMAGE_FILE_ASF_REQUEST_CODE = 2002

    const val OPEN_IMAGE_DIR_REQUEST_CODE = 2003
    const val OPEN_IMAGE_DIR_ASF_REQUEST_CODE = 2004

    const val OPEN_SHARED_DIR_REQUEST_CODE = 2005
    const val OPEN_SHARED_DIR_ASF_REQUEST_CODE = 2006

    const val OPEN_EXPORT_DIR_REQUEST_CODE = 2007
    const val OPEN_EXPORT_DIR_ASF_REQUEST_CODE = 2008

    const val OPEN_IMPORT_FILE_REQUEST_CODE = 2009
    const val OPEN_IMPORT_FILE_ASF_REQUEST_CODE = 2010

    const val OPEN_LOG_FILE_DIR_REQUEST_CODE = 2011
    const val OPEN_LOG_FILE_DIR_ASF_REQUEST_CODE = 2012

    const val OPEN_IMPORT_BIOS_FILE_REQUEST_CODE = 2013
    const val OPEN_IMPORT_BIOS_FILE_ASF_REQUEST_CODE = 2014

    const val STATUS_NULL = -1
    const val STATUS_CREATED = 1000
    const val STATUS_PAUSED = 1001
    const val ACTION_START = "com.max2idea.android.limbo.action.STARTVM"

    const val enable_SDL = true
    const val MAX_CPU_NUM = 8

    @JvmField
    var keyDelay = 100

    @JvmField
    var mouseButtonDelay = 100

    const val APP_NAME = "Limbo Emulator"

    const val defaultDNSServer = "8.8.8.8"
    const val downloadLink = "https://github.com/limboemu/limbo/wiki/Downloads"
    const val guidesLink = "https://github.com/limboemu/limbo/wiki/Guides"
    const val kvmLink = "https://github.com/limboemu/limbo/wiki/KVM"
    const val faqLink = "https://github.com/limboemu/limbo/wiki/FAQ"
    const val toolsLink = "https://github.com/limboemu/limbo/wiki/Tools"
    const val newVersionLink = "https://raw.githubusercontent.com/limboemu/limbo/master/VERSION"
    const val otherOSLink = "https://github.com/limboemu/limbo/wiki/Other-Operating-Systems"

    const val enableKeyboardLayoutOption = true
    const val enableMouseOption = true

    const val debug = false
    const val debugQmp = false
    const val debugStrictMode = false

    const val EXIT_SUCCESS = 1
    const val EXIT_UNKNOWN = 2

    @JvmField
    var enableSDLSound = true

    @JvmField
    var stackSize = 10L * 1024L * 1024L

    @JvmField
    var aaudioLibName = "libcompat-SDL2-addons.so"

    @JvmField
    var enableSoftwareUpdates = true

    @JvmField
    var enableImmersiveMode = false

    @JvmField
    var legacyDrives = false

    @JvmField
    var enableDefaultDevices = false

    @JvmField
    var syncFilesOnClose = true

    enum class Arch {
        x86,
        x86_64,
        arm,
        arm64,
        ppc,
        ppc64,
        sparc,
        sparc64,
    }

    @JvmField
    var enableKVM = false

    @JvmField
    var storagedir: String? = null

    @JvmField
    var enableSMPOnlyOnKVM = false

    @JvmField
    var loadNativeLibsEarly = false

    @JvmField
    var loadNativeLibsMainThread = true

    @JvmField
    var wakeLockTag = "limbo:wakelock"

    @JvmField
    var wifiLockTag = "limbo:wifilock"

    @JvmField
    var SDLHintScale = 1

    @JvmField
    var viewLogInternally = true

    @JvmField
    var enableEmulatedFloppy = true

    @JvmField
    var enableEmulatedSDCard = false

    @JvmField
    var destLogFilename = "limbolog.txt"

    @JvmField
    var notificationChannelID = "limbo"

    @JvmField
    var notificationChannelName = "limbo"

    @JvmField
    var showToast = false

    @JvmField
    var closeFileDescriptors = true

    @JvmField
    var enableSharedFolder = false

    @JvmField
    var machineFolder = "machines/"

    @JvmField
    var logFilePath: String? = null

    @JvmField
    var stateFilename = "vm.state"

    @JvmField
    var QMPServer = "127.0.0.1"

    @JvmField
    var QMPPort = 4444

    @JvmField
    var MAX_DISPLAY_REFRESH_RATE = 100

    @JvmField
    var defaultVNCHost = "127.0.0.1"

    const val defaultVNCPort = 1

    @JvmField
    var defaultKeyboardLayout = "en-us"

    @JvmField
    var collapseSections = true

    @JvmField
    var enableToggleKeyboard = false

    @JvmField
    var enableMTTCG = true

    @JvmField
    var osImages: LinkedHashMap<String, LinksManager.LinkInfo> = LinkedHashMap()

    @JvmField
    var processMouseHistoricalEvents = false

    @JvmField
    var defaultCheckNewVersion = false

    @JvmField
    var enableTracingLog = false

    const val traceDir = "/sdcard/limbo/tmp/trace"
    const val traceEventsFile = "/sdcard/limbo/tmp/events"
    const val traceLogFile = "/sdcard/limbo/log.txt"

    @JvmField
    var overrideTbSize = false

    @JvmField
    var tbSize = "32M"

    @JvmField
    var clientClass: Class<*>? = null
}
