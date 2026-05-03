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
package com.max2idea.android.limbo.machine

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.util.ArrayList

/**
 * Storage implementation for recent file paths.
 */
class FavOpenHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private var database: SQLiteDatabase? = null

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(TABLE_NAME_FAV_FILES_CREATE)
    }

    override fun close() {
        database?.close()
        database = null
        super.close()
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (newVersion > 2) {
            db.execSQL(TABLE_NAME_FAV_FILES_CREATE)
        }
    }

    @Synchronized
    fun getFavSeq(favType: String, favPath: String): Int {
        val query = "select $FAVSEQ from $TABLE_NAME_FAV_FILES where $FAVFILE = ? and $FAVFILETYPE = ?;"
        readableDatabase.rawQuery(query, arrayOf(favPath, favType)).use { cursor ->
            var result = -1
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                result = cursor.getInt(0)
                cursor.moveToNext()
            }
            return result
        }
    }

    @Synchronized
    fun insertFav(favtype: String, favpath: String): Boolean {
        val stateValues = ContentValues().apply {
            put(FAVFILE, favpath)
            put(FAVFILETYPE, favtype)
        }
        val row = try {
            writableDatabase.insertOrThrow(TABLE_NAME_FAV_FILES, null, stateValues)
        } catch (ex: Exception) {
            Log.w(TAG, "Error while Insert Fav Path: ${ex.message}")
            0L
        }
        return row > 0
    }

    @Synchronized
    fun getFav(favType: String): ArrayList<String> {
        val query = "select $FAVFILE from $TABLE_NAME_FAV_FILES where $FAVFILETYPE = ?;"
        val results = ArrayList<String>()
        readableDatabase.rawQuery(query, arrayOf(favType)).use { cursor ->
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                results.add(cursor.getString(0))
                cursor.moveToNext()
            }
        }
        return results
    }

    private fun openDatabase() {
        if (database == null) {
            database = writableDatabase
        }
    }

    companion object {
        private const val TAG = "FAVS"
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "FAVS"
        private const val FAVFILE = "FAVFILE"
        private const val FAVFILETYPE = "FAVFILETYPE"
        private const val FAVSEQ = "FAVSEQ"
        private const val TABLE_NAME_FAV_FILES = "favorites"
        private const val TABLE_NAME_FAV_FILES_CREATE =
            "CREATE TABLE IF NOT EXISTS $TABLE_NAME_FAV_FILES (" +
                "$FAVSEQ INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$FAVFILETYPE TEXT, " +
                "$FAVFILE TEXT);"

        private var sInstance: FavOpenHelper? = null

        @JvmStatic
        fun getInstance(): FavOpenHelper = requireNotNull(sInstance)

        @JvmStatic
        @Synchronized
        fun initialize(context: Context) {
            if (sInstance == null) {
                sInstance = FavOpenHelper(context.applicationContext).also { helper ->
                    helper.setWriteAheadLoggingEnabled(true)
                    helper.openDatabase()
                }
            }
        }
    }
}
