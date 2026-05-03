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
package com.max2idea.android.limbo.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.files.FileUtils
import java.util.ArrayList
import kotlin.math.max

class SpinnerAdapter(
    context: Context,
    layout: Int,
    items: ArrayList<String>,
    private val index: Int,
) : ArrayAdapter<String>(context, layout, items) {
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        if (position >= index) {
            val textView = view.findViewById<TextView>(R.id.customSpinnerDropDownItem)
            textView.text = FileUtils.convertFilePath(textView.text.toString(), position)
        }
        return view
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        if (position >= index) {
            val textView = view.findViewById<TextView>(R.id.customSpinnerItem)
            textView.text = FileUtils.convertFilePath(textView.text.toString(), position)
        }
        return view
    }

    companion object {
        @JvmStatic
        fun getItemPosition(spinner: Spinner, value: String): Int {
            for (i in 0 until spinner.count) {
                if (spinner.getItemAtPosition(i) == value) {
                    return i
                }
            }
            return -1
        }

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun addItem(spinner: Spinner, value: String) {
            (spinner.adapter as ArrayAdapter<String>).add(value)
        }

        @JvmStatic
        fun getPositionFromSpinner(spinner: Spinner, value: String): Int {
            for (i in 0 until spinner.count) {
                if (spinner.getItemAtPosition(i) == value) {
                    return i
                }
            }
            return -1
        }

        @JvmStatic
        fun setDiskAdapterValue(spinner: Spinner, value: String?) {
            spinner.post {
                if (value != null) {
                    val position = getPositionFromSpinner(spinner, value)
                    spinner.setSelection(max(position, 0))
                } else {
                    spinner.setSelection(0)
                }
            }
        }
    }
}
