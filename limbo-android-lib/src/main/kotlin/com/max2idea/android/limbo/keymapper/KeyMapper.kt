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
package com.max2idea.android.limbo.keymapper

import android.view.KeyEvent
import java.io.Serializable
import java.util.ArrayList

/**
 * Definition of the key mapper. It supports a 2D set of mapped touch keys.
 */
class KeyMapper(
    @JvmField var name: String,
    @JvmField var rows: Int,
    @JvmField var cols: Int,
) : Serializable {
    @JvmField
    var mapping: Array<Array<KeyMapping>> = Array(rows) { row ->
        Array(cols) { col -> KeyMapping(row, col) }
    }

    class KeyMapping(
        private val x: Int,
        private val y: Int,
    ) : Serializable {
        private val keyCodes = ArrayList<Int>()
        private val mouseButtons = ArrayList<Int>()
        private val unicodeChars = ArrayList<Int>()
        private var repeat = false
        private var modifiableKeys = false

        fun addKeyCode(keyCode: Int, event: KeyEvent?) {
            var unicodeChar = -1
            if (event != null) {
                unicodeChar = event.unicodeChar
                if (keyCodes.contains(KeyEvent.KEYCODE_SHIFT_LEFT) ||
                    keyCodes.contains(KeyEvent.KEYCODE_SHIFT_RIGHT)
                ) {
                    val newUnicodeChar = event.getUnicodeChar(KeyEvent.META_SHIFT_ON)
                    if (newUnicodeChar != event.unicodeChar) {
                        modifiableKeys = true
                        unicodeChar = newUnicodeChar
                    }
                }
            }
            if (!availKeysButtons() || keyCodes.contains(keyCode)) {
                return
            }
            keyCodes.add(keyCode)
            unicodeChars.add(unicodeChar)
        }

        fun addMouseButton(button: Int) {
            if (!availKeysButtons() || mouseButtons.contains(button)) {
                return
            }
            mouseButtons.add(button)
        }

        private fun availKeysButtons(): Boolean = keyCodes.size + mouseButtons.size < MAX_KEY_MOUSE_BTNS

        fun clear() {
            keyCodes.clear()
            unicodeChars.clear()
            mouseButtons.clear()
            modifiableKeys = false
        }

        fun toggleRepeat() {
            repeat = !repeat
        }

        fun hasModifiableKeys(): Boolean = modifiableKeys

        fun getKeyCodes(): ArrayList<Int> = ArrayList(keyCodes)

        fun isRepeat(): Boolean = repeat

        fun getMouseButtons(): ArrayList<Int> = ArrayList(mouseButtons)

        fun getX(): Int = x

        fun getY(): Int = y

        fun getUnicodeChars(): ArrayList<Int> = ArrayList(unicodeChars)

        companion object {
            private const val serialVersionUID = 7937229682428314497L
            const val MAX_KEY_MOUSE_BTNS = 6

            @JvmStatic
            fun isKeyMeta(keyCode: Int): Boolean =
                keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
                    keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
                    keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
                    keyCode == KeyEvent.KEYCODE_ALT_RIGHT ||
                    keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
                    keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
        }
    }

    companion object {
        private const val serialVersionUID = 9114128818551070254L
    }
}
