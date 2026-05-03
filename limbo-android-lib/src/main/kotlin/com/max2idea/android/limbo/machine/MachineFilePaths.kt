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

import java.util.ArrayList
import java.util.Locale

object MachineFilePaths {
    @JvmStatic
    fun insertRecentFilePath(fileType: Machine.FileType?, filePath: String?) {
        if (fileType == null || filePath.isNullOrEmpty()) {
            return
        }
        if (!isRecentFilePathStored(fileType, filePath)) {
            FavOpenHelper.getInstance().insertFav(fileType.name.lowercase(Locale.US), filePath)
        }
    }

    @JvmStatic
    fun isRecentFilePathStored(type: Machine.FileType, filePath: String): Boolean =
        FavOpenHelper.getInstance().getFavSeq(type.toString().lowercase(Locale.US), filePath) >= 0

    @JvmStatic
    @Synchronized
    fun getRecentFilePaths(fileType: Machine.FileType): ArrayList<String> =
        FavOpenHelper.getInstance().getFav(fileType.toString().lowercase(Locale.US))
}
