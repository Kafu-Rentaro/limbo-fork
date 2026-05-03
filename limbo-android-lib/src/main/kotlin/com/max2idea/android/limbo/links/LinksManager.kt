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
package com.max2idea.android.limbo.links

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.main.Config
import java.util.ArrayList

class LinksManager private constructor() {
    enum class LinkType {
        ISO,
        TOOL,
        OTHER,
    }

    class LinkInfo(
        @JvmField val title: String,
        @JvmField val descr: String,
        @JvmField val url: String?,
        @JvmField val type: LinkType,
    )

    class FileAdapter(
        context: Context,
        layout: Int,
        private val files: ArrayList<LinkInfo>,
    ) : ArrayAdapter<LinkInfo>(context, layout, files) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rowView = convertView ?: LayoutInflater.from(context).inflate(R.layout.link_row, parent, false)
            val textView = rowView.findViewById<TextView>(R.id.LINK_NAME)
            val descrView = rowView.findViewById<TextView>(R.id.LINK_DESCR)
            val imageView = rowView.findViewById<ImageView>(R.id.LINK_ICON)
            val linkInfo = files[position]

            textView.text = linkInfo.title
            descrView.text = linkInfo.descr
            imageView.setImageResource(
                when (linkInfo.type) {
                    LinkType.ISO,
                    LinkType.OTHER,
                    -> R.drawable.cd
                    LinkType.TOOL -> R.drawable.advanced
                },
            )

            return rowView
        }
    }

    companion object {
        @JvmStatic
        fun show(context: Context) {
            val contentView = LayoutInflater.from(context).inflate(R.layout.os_links, null, false)
            val listView = contentView.findViewById<ListView>(R.id.os_list)
            val items = ArrayList(Config.osImages.values)
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.DownloadISOAndVirtualHDDImages)
                .setView(contentView)
                .setNegativeButton(android.R.string.cancel, null)
                .create()

            listView.adapter = FileAdapter(context, R.layout.link_row, items)
            listView.setOnItemClickListener { _, _, position, _ ->
                items.getOrNull(position)?.url?.let { goToURL(context, it) }
                dialog.dismiss()
            }

            dialog.show()
        }

        @JvmStatic
        fun goToURL(context: Context, url: String) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
            }
            context.startActivity(intent)
        }
    }
}
