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

package com.max2idea.android.limbo.machine

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.max2idea.android.limbo.main.Config
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Observable
import java.util.Observer

/**
 * DAO implementation for storing machines in SQLite.
 */
class MachineOpenHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION),
    IMachineDatabase,
    Observer {

    private var database: SQLiteDatabase? = null

    init {
        ensureDb()
    }

    private val db: SQLiteDatabase
        get() = ensureDb()

    @Synchronized
    private fun ensureDb(): SQLiteDatabase {
        if (database == null) {
            database = writableDatabase
        }
        return requireNotNull(database)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(MACHINE_TABLE_CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w("machineOpenHelper", "Upgrading database from version $oldVersion to $newVersion")
        if (newVersion >= 3 && oldVersion <= 2) {
            addColumn(db, MachineProperty.KERNEL, "TEXT")
            addColumn(db, MachineProperty.INITRD, "TEXT")
        }

        if (newVersion >= 4 && oldVersion <= 3) {
            addColumn(db, MachineProperty.CPUNUM, "TEXT")
            addColumn(db, MachineProperty.MACHINETYPE, "TEXT")
        }

        if (newVersion >= 5 && oldVersion <= 4) {
            addColumn(db, MachineProperty.HDC, "TEXT")
            addColumn(db, MachineProperty.HDD, "TEXT")
        }

        if (newVersion >= 6 && oldVersion <= 5) {
            addColumn(db, MachineProperty.APPEND, "TEXT")
        }

        if (newVersion >= 7 && oldVersion <= 6) {
            addColumn(db, MachineProperty.DISABLE_FD_BOOT_CHK, "INTEGER")
        }

        if (newVersion >= 8 && oldVersion <= 7) {
            addColumn(db, MachineProperty.ARCH, "TEXT")
        }

        if (newVersion >= 9 && oldVersion <= 8) {
            addColumn(db, MachineProperty.SD, "TEXT")
        }

        if (newVersion >= 10 && oldVersion <= 9) {
            addColumn(db, MachineProperty.PAUSED, "INTEGER")
        }

        if (newVersion >= 11 && oldVersion <= 10) {
            addColumn(db, MachineProperty.SHARED_FOLDER, "TEXT")
            addColumn(db, MachineProperty.SHARED_FOLDER_MODE, "INTEGER")
        }

        if (newVersion >= 12 && oldVersion <= 11) {
            addColumn(db, MachineProperty.EXTRA_PARAMS, "TEXT")
        }

        if (newVersion >= 13 && oldVersion <= 12) {
            addColumn(db, MachineProperty.HOSTFWD, "TEXT")
            addColumn(db, MachineProperty.GUESTFWD, "TEXT")
        }

        if (newVersion >= 14 && oldVersion <= 13) {
            addColumn(db, MachineProperty.UI, "TEXT")
        }

        if (newVersion >= 15 && oldVersion <= 14) {
            addColumn(db, MachineProperty.DISABLE_TSC, "INTEGER")
            addColumn(db, MachineProperty.MOUSE, "TEXT")
            addColumn(db, MachineProperty.KEYBOARD, "TEXT")
            addColumn(db, MachineProperty.ENABLE_MTTCG, "INTEGER")
            addColumn(db, MachineProperty.ENABLE_KVM, "INTEGER")
        }

        if (newVersion >= 16 && oldVersion <= 15) {
            addColumn(db, MachineProperty.HDA_INTERFACE, "TEXT")
            addColumn(db, MachineProperty.HDB_INTERFACE, "TEXT")
            addColumn(db, MachineProperty.HDC_INTERFACE, "TEXT")
            addColumn(db, MachineProperty.HDD_INTERFACE, "TEXT")
            addColumn(db, MachineProperty.CDROM_INTERFACE, "TEXT")
        }
    }

    override fun insertMachine(machine: Machine?): Int {
        if (machine == null) {
            return -1
        }
        val writableDb = writableDatabase
        Log.d(TAG, "inserting machine: ${machine.getName()}")
        val stateValues = ContentValues().apply {
            put(MachineProperty.MACHINE_NAME.name, machine.getName())
            put(MachineProperty.CPU.name, machine.getCpu())
            put(MachineProperty.CPUNUM.name, machine.getCpuNum())
            put(MachineProperty.MEMORY.name, machine.getMemory())
            put(MachineProperty.HDA.name, machine.getHdaImagePath())
            put(MachineProperty.HDA_INTERFACE.name, machine.getHdaInterface())
            put(MachineProperty.HDB.name, machine.getHdbImagePath())
            put(MachineProperty.HDB_INTERFACE.name, machine.getHdbInterface())
            put(MachineProperty.HDC.name, machine.getHdcImagePath())
            put(MachineProperty.HDC_INTERFACE.name, machine.getHdcInterface())
            put(MachineProperty.HDD.name, machine.getHddImagePath())
            put(MachineProperty.HDD_INTERFACE.name, machine.getHddInterface())
            put(MachineProperty.CDROM.name, machine.getCdImagePath())
            put(MachineProperty.CDROM_INTERFACE.name, machine.getCDInterface())
            put(MachineProperty.FDA.name, machine.getFdaImagePath())
            put(MachineProperty.FDB.name, machine.getFdbImagePath())
            put(MachineProperty.SHARED_FOLDER.name, machine.getSharedFolderPath())
            put(MachineProperty.SHARED_FOLDER_MODE.name, machine.getShared_folder_mode())
            put(MachineProperty.BOOT_CONFIG.name, machine.getBootDevice())
            put(MachineProperty.NETCONFIG.name, machine.getNetwork())
            put(MachineProperty.NICCONFIG.name, machine.getNetworkCard())
            put(MachineProperty.VGA.name, machine.getVga())
            put(MachineProperty.DISABLE_ACPI.name, machine.getDisableAcpi())
            put(MachineProperty.DISABLE_HPET.name, machine.getDisableHPET())
            put(MachineProperty.DISABLE_TSC.name, machine.getDisableTSC())
            put(MachineProperty.DISABLE_FD_BOOT_CHK.name, machine.getDisableFdBootChk())
            put(MachineProperty.SOUNDCARD.name, machine.getSoundCard())
            put(MachineProperty.KERNEL.name, machine.getKernel())
            put(MachineProperty.INITRD.name, machine.getInitRd())
            put(MachineProperty.APPEND.name, machine.getAppend())
            put(MachineProperty.MACHINETYPE.name, machine.getMachineType())
            put(MachineProperty.ARCH.name, machine.getArch())
            put(MachineProperty.EXTRA_PARAMS.name, machine.getExtraParams())
            put(MachineProperty.HOSTFWD.name, machine.getHostFwd())
            put(MachineProperty.GUESTFWD.name, machine.getGuestFwd())
            put(MachineProperty.UI.name, if (machine.getEnableVNC() == 1) "VNC" else "SDL")
            put(MachineProperty.MOUSE.name, machine.getMouse())
            put(MachineProperty.KEYBOARD.name, machine.getKeyboard())
            put(MachineProperty.ENABLE_MTTCG.name, machine.getEnableMTTCG())
            put(MachineProperty.ENABLE_KVM.name, machine.getEnableKVM())

            @SuppressLint("SimpleDateFormat")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            put(MachineProperty.LAST_UPDATED.name, dateFormat.format(Date()))
            put(MachineProperty.STATUS.name, Config.STATUS_CREATED)
        }

        return try {
            writableDb.insertOrThrow(MACHINE_TABLE_NAME, null, stateValues).toInt()
        } catch (ex: Exception) {
            Log.w(TAG, "Error while Insert machine: ${ex.message}")
            ex.printStackTrace()
            -1
        }
    }

    override fun updateMachineFieldAsync(
        machine: Machine?,
        property: MachineProperty?,
        value: String?,
    ) {
        if (property == null) {
            return
        }
        Thread {
            updateMachineField(machine, property, value)
        }.start()
    }

    fun updateMachineField(machine: Machine?, property: MachineProperty?, value: String?) {
        if (machine == null || property == null) {
            return
        }
        val stateValues = ContentValues().apply {
            put(property.name, value)
        }
        try {
            db.beginTransaction()
            db.update(
                MACHINE_TABLE_NAME,
                stateValues,
                "${MachineProperty.MACHINE_NAME.name} = ?",
                arrayOf(machine.getName()),
            )
            db.setTransactionSuccessful()
        } catch (ex: Exception) {
            Log.w(TAG, "Error while Updating value: ${ex.message}")
            if (Config.debug) {
                ex.printStackTrace()
            }
        } finally {
            db.endTransaction()
        }
    }

    override fun getMachine(value: String?): Machine? {
        if (value == null) {
            return null
        }
        val query = "select ${MACHINE_COLUMNS.joinToString(" , ")} " +
            " from $MACHINE_TABLE_NAME" +
            " where ${MachineProperty.STATUS} in ( ${Config.STATUS_CREATED} , ${Config.STATUS_PAUSED} ) " +
            " and ${MachineProperty.MACHINE_NAME} = ?;"

        db.rawQuery(query, arrayOf(value)).use { cursor ->
            cursor.moveToFirst()
            if (cursor.isAfterLast) {
                return null
            }

            return Machine(cursor.getString(0), false).apply {
                setCpu(cursor.getString(1))
                setMemory(cursor.getInt(2))
                setCdImagePath(cursor.getString(3))
                if (getCdImagePath() != null) {
                    setEnableCDROM(true)
                }

                setFdaImagePath(cursor.getString(4))
                if (getFdaImagePath() != null) {
                    setEnableFDA(true)
                }
                setFdbImagePath(cursor.getString(5))
                if (getFdbImagePath() != null) {
                    setEnableFDB(true)
                }

                setHdaImagePath(cursor.getString(6))
                setHdbImagePath(cursor.getString(7))
                setHdcImagePath(cursor.getString(8))
                setHddImagePath(cursor.getString(9))

                setNetwork(cursor.getString(10))
                setNetworkCard(cursor.getString(11))
                setVga(cursor.getString(12))
                setSoundCard(cursor.getString(13))
                setDisableACPI(cursor.getInt(15))
                setDisableHPET(cursor.getInt(16))
                setBootDevice(cursor.getString(19))
                setKernel(cursor.getString(20))
                setInitRd(cursor.getString(21))
                setAppend(cursor.getString(22))
                setCpuNum(cursor.getInt(23))
                setMachineType(cursor.getString(24))
                setDisableFdBootChk(cursor.getInt(25))
                setArch(cursor.getString(26))
                setPaused(cursor.getInt(27))

                setSdImagePath(cursor.getString(28))
                if (getSdImagePath() != null) {
                    setEnableSD(true)
                }

                setSharedFolderPath(cursor.getString(29))
                setShared_folder_mode(1)
                setExtraParams(cursor.getString(31))
                setHostFwd(cursor.getString(32))
                setGuestFwd(cursor.getString(33))
                setEnableVNC(if (cursor.getString(34) == "VNC") 1 else 0)
                setDisableTSC(cursor.getInt(35))
                setMouse(cursor.getString(36))
                setKeyboard(cursor.getString(37))
                setEnableMTTCG(cursor.getInt(38))
                setEnableKVM(cursor.getInt(39))
                setHdaInterface(cursor.getString(40))
                setHdbInterface(cursor.getString(41))
                setHdcInterface(cursor.getString(42))
                setHddInterface(cursor.getString(43))
                setCdInterface(cursor.getString(44))
            }
        }
    }

    override fun getMachineNames(): ArrayList<String> {
        val query = "select ${MachineProperty.MACHINE_NAME} from $MACHINE_TABLE_NAME" +
            " where ${MachineProperty.STATUS} in ( ${Config.STATUS_CREATED} , ${Config.STATUS_PAUSED} )" +
            " order by 1;"
        val machineNames = ArrayList<String>()
        db.rawQuery(query, null).use { cursor ->
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                machineNames.add(cursor.getString(0))
                cursor.moveToNext()
            }
        }
        return machineNames
    }

    override fun deleteMachine(machine: Machine?): Boolean {
        if (machine == null) {
            return false
        }
        val rowsAffected = try {
            db.delete(
                MACHINE_TABLE_NAME,
                "${MachineProperty.MACHINE_NAME} = ?",
                arrayOf(machine.getName()),
            )
        } catch (ex: Exception) {
            Log.w(TAG, "Error while deleting VM: ${ex.message}")
            if (Config.debug) {
                ex.printStackTrace()
            }
            0
        }
        return rowsAffected > 0
    }

    fun exportMachines(): String {
        val query = "select ${MACHINE_COLUMNS.joinToString(" , ")} from $MACHINE_TABLE_NAME order by 1;"
        val output = StringBuilder()
        db.rawQuery(query, null).use { cursor ->
            cursor.moveToFirst()
            val headerLine = StringBuilder()
            for (index in 0 until cursor.columnCount) {
                headerLine.append("\"").append(cursor.getColumnName(index)).append("\"")
                if (index < cursor.columnCount - 1) {
                    headerLine.append(",")
                }
            }
            output.append(headerLine).append("\n")
            while (!cursor.isAfterLast) {
                val line = StringBuilder()
                for (index in 0 until cursor.columnCount) {
                    line.append("\"").append(cursor.getString(index)).append("\"")
                    if (index < cursor.columnCount - 1) {
                        line.append(",")
                    }
                }
                output.append(line).append("\n")
                cursor.moveToNext()
            }
        }
        return output.toString()
    }

    override fun update(observable: Observable?, arg: Any?) {
        val machine = observable as? Machine ?: return
        val params = arg as? Array<*> ?: return
        val property = params[0] as? MachineProperty ?: return
        val value = params.getOrNull(1)
        when (property) {
            MachineProperty.UI -> {
                val uiValue = if ((value as Number).toInt() == 1) "VNC" else "SDL"
                updateMachineField(machine, property, uiValue)
            }
            MachineProperty.OTHER -> return
            else -> {
                val dbValue = when (value) {
                    is Number -> value.toInt().toString()
                    else -> value as String?
                }
                updateMachineField(machine, property, dbValue)
            }
        }
    }

    override fun close() {
        database?.close()
        database = null
        super.close()
    }

    companion object {
        private const val TAG = "MachineOpenHelper"
        private const val DATABASE_VERSION = 16
        private const val DATABASE_NAME = "LIMBO"
        private const val MACHINE_TABLE_NAME = "machines"

        private val MACHINE_COLUMNS = listOf(
            MachineProperty.MACHINE_NAME,
            MachineProperty.CPU,
            MachineProperty.MEMORY,
            MachineProperty.CDROM,
            MachineProperty.FDA,
            MachineProperty.FDB,
            MachineProperty.HDA,
            MachineProperty.HDB,
            MachineProperty.HDC,
            MachineProperty.HDD,
            MachineProperty.NETCONFIG,
            MachineProperty.NICCONFIG,
            MachineProperty.VGA,
            MachineProperty.SOUNDCARD,
            MachineProperty.HDCONFIG,
            MachineProperty.DISABLE_ACPI,
            MachineProperty.DISABLE_HPET,
            MachineProperty.ENABLE_USBMOUSE,
            MachineProperty.SNAPSHOT_NAME,
            MachineProperty.BOOT_CONFIG,
            MachineProperty.KERNEL,
            MachineProperty.INITRD,
            MachineProperty.APPEND,
            MachineProperty.CPUNUM,
            MachineProperty.MACHINETYPE,
            MachineProperty.DISABLE_FD_BOOT_CHK,
            MachineProperty.ARCH,
            MachineProperty.PAUSED,
            MachineProperty.SD,
            MachineProperty.SHARED_FOLDER,
            MachineProperty.SHARED_FOLDER_MODE,
            MachineProperty.EXTRA_PARAMS,
            MachineProperty.HOSTFWD,
            MachineProperty.GUESTFWD,
            MachineProperty.UI,
            MachineProperty.DISABLE_TSC,
            MachineProperty.MOUSE,
            MachineProperty.KEYBOARD,
            MachineProperty.ENABLE_MTTCG,
            MachineProperty.ENABLE_KVM,
            MachineProperty.HDA_INTERFACE,
            MachineProperty.HDB_INTERFACE,
            MachineProperty.HDC_INTERFACE,
            MachineProperty.HDD_INTERFACE,
            MachineProperty.CDROM_INTERFACE,
        )

        private val MACHINE_TABLE_CREATE =
            "CREATE TABLE IF NOT EXISTS $MACHINE_TABLE_NAME (" +
                "${MachineProperty.MACHINE_NAME.name} TEXT , " +
                "${MachineProperty.SNAPSHOT_NAME.name} TEXT , " +
                "${MachineProperty.CPU.name} TEXT, " +
                "${MachineProperty.ARCH.name} TEXT, " +
                "${MachineProperty.MEMORY.name} TEXT, " +
                "${MachineProperty.FDA.name} TEXT, " +
                "${MachineProperty.FDB.name} TEXT, " +
                "${MachineProperty.CDROM.name} TEXT, " +
                "${MachineProperty.HDA.name} TEXT, " +
                "${MachineProperty.HDB.name} TEXT, " +
                "${MachineProperty.HDC.name} TEXT, " +
                "${MachineProperty.HDD.name} TEXT, " +
                "${MachineProperty.BOOT_CONFIG.name} TEXT, " +
                "${MachineProperty.NETCONFIG.name} TEXT, " +
                "${MachineProperty.NICCONFIG.name} TEXT, " +
                "${MachineProperty.VGA.name} TEXT, " +
                "${MachineProperty.SOUNDCARD.name} TEXT, " +
                "${MachineProperty.HDCONFIG.name} TEXT, " +
                "${MachineProperty.DISABLE_ACPI.name} INTEGER, " +
                "${MachineProperty.DISABLE_HPET.name} INTEGER, " +
                "${MachineProperty.ENABLE_USBMOUSE.name} INTEGER, " +
                "${MachineProperty.STATUS.name} TEXT, " +
                "${MachineProperty.LAST_UPDATED.name} DATE, " +
                "${MachineProperty.KERNEL.name} INTEGER, " +
                "${MachineProperty.INITRD.name} TEXT, " +
                "${MachineProperty.APPEND.name} TEXT, " +
                "${MachineProperty.CPUNUM.name} INTEGER, " +
                "${MachineProperty.MACHINETYPE.name} TEXT, " +
                "${MachineProperty.DISABLE_FD_BOOT_CHK.name} INTEGER, " +
                "${MachineProperty.SD.name} TEXT, " +
                "${MachineProperty.PAUSED.name} INTEGER, " +
                "${MachineProperty.SHARED_FOLDER.name} TEXT, " +
                "${MachineProperty.SHARED_FOLDER_MODE.name} INTEGER, " +
                "${MachineProperty.EXTRA_PARAMS.name} TEXT, " +
                "${MachineProperty.HOSTFWD.name} TEXT, " +
                "${MachineProperty.GUESTFWD.name} TEXT, " +
                "${MachineProperty.UI.name} TEXT, " +
                "${MachineProperty.DISABLE_TSC.name} INTEGER, " +
                "${MachineProperty.MOUSE.name} TEXT, " +
                "${MachineProperty.KEYBOARD.name} TEXT, " +
                "${MachineProperty.ENABLE_MTTCG.name} INTEGER, " +
                "${MachineProperty.ENABLE_KVM.name} INTEGER , " +
                "${MachineProperty.HDA_INTERFACE.name} TEXT, " +
                "${MachineProperty.HDB_INTERFACE.name} TEXT, " +
                "${MachineProperty.HDC_INTERFACE.name} TEXT, " +
                "${MachineProperty.HDD_INTERFACE.name} TEXT , " +
                "${MachineProperty.CDROM_INTERFACE.name} TEXT " +
                ");"

        private var sInstance: MachineOpenHelper? = null

        @JvmStatic
        fun getInstance(): MachineOpenHelper = requireNotNull(sInstance)

        @JvmStatic
        @Synchronized
        fun initialize(context: Context) {
            if (sInstance == null) {
                sInstance = MachineOpenHelper(context.applicationContext).also { helper ->
                    helper.setWriteAheadLoggingEnabled(true)
                }
            }
        }

        private fun addColumn(db: SQLiteDatabase, property: MachineProperty, type: String) {
            db.execSQL("ALTER TABLE $MACHINE_TABLE_NAME ADD COLUMN $property $type;")
        }
    }
}
