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

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.SimpleAdapter
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limbo.emu.lib.R
import com.max2idea.android.limbo.keyboard.KeyboardUtils
import com.max2idea.android.limbo.main.Config
import com.max2idea.android.limbo.main.LimboSettingsManager
import com.max2idea.android.limbo.screen.ScreenUtils
import com.max2idea.android.limbo.toast.ToastUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Manages loading, editing, storing, and viewing the keymapper layouts.
 */
class KeyMapManager @Throws(Exception::class) constructor(
    private val activity: Activity,
    private val view: View,
    rows: Int,
    cols: Int,
) {
    private val keyMappers = ArrayList<HashMap<String, Any?>>()
    private val defaultRows = rows
    private val defaultCols: Int

    @JvmField
    var mapperEditLayout: RelativeLayout? = null

    @JvmField
    var mapperButtons: RelativeLayout? = null

    @JvmField
    var keySurfaceView: KeySurfaceView? = null

    @JvmField
    var keyMapper: KeyMapper? = null

    @JvmField
    var sendKeyEventListener: OnSendKeyEventListener? = null

    @JvmField
    var sendMouseEventListener: OnSendMouseEventListener? = null

    @JvmField
    var unhundledTouchEventListener: OnUnhandledTouchEventListener? = null

    private lateinit var mAddKeyMapper: ImageButton
    private lateinit var mRemoveKeyMapper: ImageButton
    private lateinit var mAddSpecialKeysButtons: ImageButton
    private lateinit var mUseKeyMapper: ImageButton
    private lateinit var mClearKey: ImageButton
    private lateinit var mRepeatKey: ImageButton
    private lateinit var mKeyMapperList: ListView
    private lateinit var keyMapperAdapter: SimpleAdapter
    private lateinit var mKeyMapperName: EditText
    private var selectedMap: HashMap<String, Any?>? = null
    private var lastOrientation = -1

    init {
        if (cols % 2 != 0) {
            throw Exception("Cols should be even number!")
        }
        defaultCols = cols
        setupWidgets()
        setupKeyMapper()
    }

    private fun setupWidgets() {
        mapperEditLayout = activity.findViewById(R.id.mapperEditLayout)
        mapperEditLayout?.visibility = View.GONE
        mapperButtons = activity.findViewById(R.id.mapperButtons)
        mapperButtons?.visibility = View.GONE
        mKeyMapperList = activity.findViewById(R.id.keyMapperList)

        mAddKeyMapper = activity.findViewById(R.id.addKeyMapper)
        mKeyMapperName = activity.findViewById(R.id.key_mapper_name)

        mRemoveKeyMapper = activity.findViewById(R.id.removeKeyMapper)
        mAddSpecialKeysButtons = activity.findViewById(R.id.addSpecialKeysButtons)
        mUseKeyMapper = activity.findViewById(R.id.useKeyMapper)
        mClearKey = activity.findViewById(R.id.clearKey)
        mRepeatKey = activity.findViewById(R.id.repeatKey)
    }

    fun toggleKeyMapper(): Boolean {
        return if (isEditMode() || isActive()) {
            mapperEditLayout?.visibility = View.GONE
            mapperButtons?.visibility = View.GONE
            clearKeyMapper()
            ScreenUtils.updateOrientation(activity, lastOrientation)
            false
        } else {
            mapperEditLayout?.visibility = View.VISIBLE
            mapperButtons?.visibility = View.VISIBLE
            lastOrientation = activity.resources.configuration.orientation
            true
        }
    }

    private fun isActive(): Boolean =
        mapperButtons != null && mapperButtons?.visibility == View.VISIBLE

    fun processKeyMap(event: KeyEvent?): Boolean {
        val surfaceView = keySurfaceView ?: return false
        if (isEditMode() && surfaceView.pointers.size > 0) {
            if (event != null) {
                surfaceView.setKeyCode(event)
                surfaceView.paint(true)
            }
            return true
        }
        return false
    }

    fun processMouseMap(button: Int, action: Int): Boolean {
        val surfaceView = keySurfaceView ?: return false
        if (isEditMode() &&
            surfaceView.pointers.size > 0 &&
            (button == Config.SDL_MOUSE_LEFT ||
                button == Config.SDL_MOUSE_MIDDLE ||
                button == Config.SDL_MOUSE_RIGHT)
        ) {
            surfaceView.setMouseButton(button)
            surfaceView.paint(true)
            return true
        }
        return false
    }

    fun sendKeyEvent(keyCode: Int, down: Boolean) {
        sendKeyEventListener?.onSendKeyEvent(keyCode, down)
    }

    fun sendMouseEvent(button: Int, down: Boolean) {
        sendMouseEventListener?.onSendMouseEvent(button, down)
    }

    @Throws(Exception::class)
    private fun setupKeyMapper() {
        setupKeyMapperButtons()
        setupKeyMapperList()
        setupKeyMapperLayout()
    }

    @Throws(Exception::class)
    private fun setupKeyMapperLayout() {
        mapperButtons?.removeAllViews()
        keySurfaceView = KeySurfaceView(activity, this)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
        )
        mapperButtons?.addView(keySurfaceView, params)
    }

    private fun setupKeyMapperList() {
        keyMapperAdapter = KeyMapperListAdapter(
            activity,
            keyMappers,
            android.R.layout.simple_list_item_1,
            arrayOf("keymapper_name"),
            intArrayOf(android.R.id.text1),
        )
        mKeyMapperList.adapter = keyMapperAdapter
        mKeyMapperList.setOnItemClickListener { _, _, position, _ ->
            loadKeyMapper(position)
        }
        loadKeyMappers()
    }

    private fun setupKeyMapperButtons() {
        mAddKeyMapper.setOnClickListener { promptKeyMapperName() }
        mRemoveKeyMapper.setOnClickListener { promptDeleteKeyMapper() }
        mAddSpecialKeysButtons.setOnClickListener {
            if (isKeyMapperActive()) {
                advancedKey()
            }
        }
        mUseKeyMapper.setOnClickListener {
            if (isKeyMapperActive()) {
                useKeyMapper()
            }
        }
        mClearKey.setOnClickListener {
            if (isKeyMapperActive()) {
                clearKey()
            }
        }
        mRepeatKey.setOnClickListener {
            if (isKeyMapperActive()) {
                repeatKey()
            }
        }
        mKeyMapperName.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {
                    val mapper = keyMapper ?: return
                    try {
                        mapper.name = charSequence.toString()
                        selectedMap?.put("keymapper_name", mapper.name)
                        saveKeyMappers()
                        keyMapperAdapter.notifyDataSetChanged()
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }

                override fun afterTextChanged(editable: Editable) = Unit
            },
        )
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun getKeyMapperObj(keyMapper: String): KeyMapper {
        val bytes = Base64.decode(keyMapper, Base64.DEFAULT)
        ObjectInputStream(ByteArrayInputStream(bytes)).use { stream ->
            return stream.readObject() as KeyMapper
        }
    }

    @Throws(IOException::class)
    private fun getKeyMapperString(keyMapperObj: KeyMapper): String {
        val output = ByteArrayOutputStream()
        ObjectOutputStream(output).use { stream ->
            stream.writeObject(keyMapperObj)
            stream.flush()
        }
        return String(Base64.encode(output.toByteArray(), Base64.DEFAULT))
    }

    private fun loadKeyMapper(position: Int) {
        selectedMap = keyMappers[position]
        val mapper = selectedMap?.get("key_mapper") as? KeyMapper
        if (mapper != null) {
            loadKeyMapper(mapper)
        }
    }

    private fun loadKeyMapper(keyMapper: KeyMapper) {
        this.keyMapper = keyMapper
        keySurfaceView?.mapping = keyMapper.mapping
        keySurfaceView?.updateDimensions()
        keySurfaceView?.paint(true)
        mKeyMapperName.setText(keyMapper.name)
    }

    fun promptDeleteKeyMapper() {
        if (keyMapper == null) {
            return
        }
        val alertDialog = MaterialAlertDialogBuilder(activity).create()
        alertDialog.setTitle(activity.getString(R.string.KeyMapper))
        alertDialog.setMessage(activity.getString(R.string.DeleteKeyMapper))
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getString(R.string.Delete)) { dialog, _ ->
            removeKeyMapper()
            dialog.dismiss()
        }
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getString(R.string.Cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        alertDialog.show()
    }

    private fun removeKeyMapper() {
        val mapper = keyMapper ?: return
        val keyMapMap = keyMappers.firstOrNull { map ->
            map.containsKey("key_mapper") && map["key_mapper"] === mapper
        }
        if (keyMapMap != null) {
            keyMappers.remove(keyMapMap)
            saveKeyMappers()
            loadKeyMappers()
            clearKeyMapper()
        }
    }

    private fun clearKeyMapper() {
        keyMapper = null
        mKeyMapperName.setText("")
        keySurfaceView?.updateDimensions()
        keySurfaceView?.paint(true)
    }

    private fun advancedKey() {
        promptAdvancedKey()
    }

    private fun promptAdvancedKey() {
        val alertDialogBuilder = MaterialAlertDialogBuilder(activity)
        alertDialogBuilder.setTitle(R.string.SpecialKeysButtons)
        val items = arrayOf<CharSequence>(
            "Left Ctrl",
            "Right Ctrl",
            "Left Alt",
            "Right Alt",
            "Left Shift",
            "Right Shift",
            "Fn",
            "Mouse Btn Left",
            "Mouse Btn Middle",
            "Mouse Btn Right",
        )
        val itemsKeyCodes = intArrayOf(
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_FUNCTION,
            Config.SDL_MOUSE_LEFT,
            Config.SDL_MOUSE_MIDDLE,
            Config.SDL_MOUSE_RIGHT,
        )
        val itemsEnabled = BooleanArray(items.size)
        alertDialogBuilder.setMultiChoiceItems(items, itemsEnabled) { _, index, checked ->
            var selected = 0
            for (value in itemsEnabled) {
                if (value) {
                    selected++
                }
            }
            if (selected == KeyMapper.KeyMapping.MAX_KEY_MOUSE_BTNS) {
                ToastUtils.toastShort(activity, activity.getString(R.string.TooManyKeysButtons))
            }
            itemsEnabled[index] = checked
        }
        val alertDialog = alertDialogBuilder.create()
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getString(android.R.string.ok)) { dialog, _ ->
            for (k in itemsEnabled.indices) {
                if (itemsEnabled[k]) {
                    val surfaceView = keySurfaceView
                    if (surfaceView != null && surfaceView.pointers.size > 0) {
                        for (keyMapping in surfaceView.pointers.values) {
                            if (k < 7) {
                                keyMapping.addKeyCode(itemsKeyCodes[k], null)
                            } else {
                                keyMapping.addMouseButton(itemsKeyCodes[k])
                            }
                            break
                        }
                        saveKeyMappers()
                        surfaceView.paint(false)
                    }
                }
            }
            dialog.dismiss()
        }
        alertDialog.show()
    }

    fun promptKeyMapperName() {
        val alertDialog: AlertDialog = MaterialAlertDialogBuilder(activity).create()
        alertDialog.setTitle(activity.getString(R.string.KeyMapperName))
        val keyMapperName = EditText(activity)
        keyMapperName.setText("")
        keyMapperName.isEnabled = true
        keyMapperName.visibility = View.VISIBLE
        keyMapperName.setSingleLine()
        alertDialog.setView(keyMapperName)

        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getString(R.string.Create)) { _, _ ->
            val keyMapperNameStr = keyMapperName.text.toString()
            for (keyMapperKey in keyMappers) {
                if (keyMapperKey["keymapper_name"] == keyMapperNameStr) {
                    ToastUtils.toastShort(activity, activity.getString(R.string.MapperExistsChooseAnotherName))
                    return@setButton
                }
            }
            val newKeyMapper = KeyMapper(keyMapperNameStr, defaultRows, defaultCols)
            val map = HashMap<String, Any?>()
            map["keymapper_name"] = keyMapperNameStr
            map["key_mapper"] = newKeyMapper
            keyMappers.add(map)
            keyMapperAdapter.notifyDataSetChanged()
            loadKeyMapper(newKeyMapper)
            saveKeyMappers()
        }
        alertDialog.show()
    }

    private fun clearKey() {
        val surfaceView = keySurfaceView ?: return
        if (surfaceView.pointers.size > 0) {
            for (keyMapping in surfaceView.pointers.values) {
                keyMapping.clear()
                ToastUtils.toastShort(activity, activity.getString(R.string.ClearedKey))
                break
            }
        }
        surfaceView.paint(false)
        saveKeyMappers()
    }

    private fun repeatKey() {
        val surfaceView = keySurfaceView ?: return
        for (keyMapping in surfaceView.pointers.values) {
            keyMapping.toggleRepeat()
            if (keyMapping.isRepeat()) {
                ToastUtils.toastShort(activity, activity.getString(R.string.SetKeyRepeat))
            } else {
                ToastUtils.toastShort(activity, activity.getString(R.string.RemovedKeyRepeat))
            }
            break
        }
        surfaceView.paint(false)
        saveKeyMappers()
    }

    fun useKeyMapper() {
        keySurfaceView?.pointers?.clear()
        KeyboardUtils.hideKeyboard(activity, view)
        mapperEditLayout?.visibility = View.GONE
        mapperButtons?.visibility = View.VISIBLE
        ScreenUtils.updateOrientation(activity, lastOrientation)
        Handler(Looper.getMainLooper()).postDelayed(
            {
                keySurfaceView?.paint(true)
                keySurfaceView?.updateDimensions()
                val mapper = keyMapper
                if (mapper != null) {
                    ToastUtils.toastShort(
                        activity,
                        activity.getString(R.string.UsingKeyMapper) + ": " + mapper.name,
                    )
                }
            },
            500,
        )
    }

    private fun loadKeyMappers() {
        keyMappers.clear()
        keyMapper = null
        keySurfaceView?.updateDimensions()
        val keyMappersSet = LimboSettingsManager.getKeyMappers(activity)
        for (keyMapperString in keyMappersSet.toTypedArray()) {
            try {
                val keyMapperObj = getKeyMapperObj(keyMapperString)
                val map = HashMap<String, Any?>()
                map["keymapper_name"] = keyMapperObj.name
                map["key_mapper"] = keyMapperObj
                keyMappers.add(map)
            } catch (ex: IOException) {
                ex.printStackTrace()
            } catch (ex: ClassNotFoundException) {
                ex.printStackTrace()
            }
        }
        val addedBuiltIn = addBuiltInFullKeyboardIfMissing()
        keyMappers.sortBy { it["keymapper_name"] as? String ?: "" }
        if (addedBuiltIn) {
            saveKeyMappers()
        }
        keyMapperAdapter.notifyDataSetChanged()
    }

    private fun addBuiltInFullKeyboardIfMissing(): Boolean {
        for (map in keyMappers) {
            if (BUILT_IN_FULL_KEYBOARD_NAME == map["keymapper_name"]) {
                return false
            }
        }
        val mapper = createBuiltInFullKeyboard()
        val map = HashMap<String, Any?>()
        map["keymapper_name"] = mapper.name
        map["key_mapper"] = mapper
        keyMappers.add(map)
        return true
    }

    private fun createBuiltInFullKeyboard(): KeyMapper {
        val mapper = KeyMapper(BUILT_IN_FULL_KEYBOARD_NAME, 8, 16)
        val keys = arrayOf(
            intArrayOf(
                KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_F1,
                KeyEvent.KEYCODE_F2,
                KeyEvent.KEYCODE_F3,
                KeyEvent.KEYCODE_F4,
                KeyEvent.KEYCODE_F5,
                KeyEvent.KEYCODE_F6,
                KeyEvent.KEYCODE_F7,
                KeyEvent.KEYCODE_F8,
                KeyEvent.KEYCODE_F9,
                KeyEvent.KEYCODE_F10,
                KeyEvent.KEYCODE_F11,
                KeyEvent.KEYCODE_F12,
                KeyEvent.KEYCODE_SYSRQ,
                KeyEvent.KEYCODE_SCROLL_LOCK,
                KeyEvent.KEYCODE_BREAK,
            ),
            intArrayOf(
                KeyEvent.KEYCODE_GRAVE,
                KeyEvent.KEYCODE_1,
                KeyEvent.KEYCODE_2,
                KeyEvent.KEYCODE_3,
                KeyEvent.KEYCODE_4,
                KeyEvent.KEYCODE_5,
                KeyEvent.KEYCODE_6,
                KeyEvent.KEYCODE_7,
                KeyEvent.KEYCODE_8,
                KeyEvent.KEYCODE_9,
                KeyEvent.KEYCODE_0,
                KeyEvent.KEYCODE_MINUS,
                KeyEvent.KEYCODE_EQUALS,
                KeyEvent.KEYCODE_DEL,
                KeyEvent.KEYCODE_NUM_LOCK,
                KeyEvent.KEYCODE_NUMPAD_DIVIDE,
            ),
            intArrayOf(
                KeyEvent.KEYCODE_TAB,
                KeyEvent.KEYCODE_Q,
                KeyEvent.KEYCODE_W,
                KeyEvent.KEYCODE_E,
                KeyEvent.KEYCODE_R,
                KeyEvent.KEYCODE_T,
                KeyEvent.KEYCODE_Y,
                KeyEvent.KEYCODE_U,
                KeyEvent.KEYCODE_I,
                KeyEvent.KEYCODE_O,
                KeyEvent.KEYCODE_P,
                KeyEvent.KEYCODE_LEFT_BRACKET,
                KeyEvent.KEYCODE_RIGHT_BRACKET,
                KeyEvent.KEYCODE_BACKSLASH,
                KeyEvent.KEYCODE_NUMPAD_MULTIPLY,
                KeyEvent.KEYCODE_NUMPAD_SUBTRACT,
            ),
            intArrayOf(
                KeyEvent.KEYCODE_CTRL_LEFT,
                KeyEvent.KEYCODE_A,
                KeyEvent.KEYCODE_S,
                KeyEvent.KEYCODE_D,
                KeyEvent.KEYCODE_F,
                KeyEvent.KEYCODE_G,
                KeyEvent.KEYCODE_H,
                KeyEvent.KEYCODE_J,
                KeyEvent.KEYCODE_K,
                KeyEvent.KEYCODE_L,
                KeyEvent.KEYCODE_SEMICOLON,
                KeyEvent.KEYCODE_APOSTROPHE,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_FORWARD_DEL,
                KeyEvent.KEYCODE_NUMPAD_7,
                KeyEvent.KEYCODE_NUMPAD_8,
            ),
            intArrayOf(
                KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.KEYCODE_Z,
                KeyEvent.KEYCODE_X,
                KeyEvent.KEYCODE_C,
                KeyEvent.KEYCODE_V,
                KeyEvent.KEYCODE_B,
                KeyEvent.KEYCODE_N,
                KeyEvent.KEYCODE_M,
                KeyEvent.KEYCODE_COMMA,
                KeyEvent.KEYCODE_PERIOD,
                KeyEvent.KEYCODE_SLASH,
                KeyEvent.KEYCODE_SHIFT_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_MOVE_HOME,
                KeyEvent.KEYCODE_NUMPAD_9,
                KeyEvent.KEYCODE_NUMPAD_ADD,
            ),
            intArrayOf(
                KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_META_LEFT,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_ALT_RIGHT,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_MOVE_END,
                KeyEvent.KEYCODE_NUMPAD_4,
                KeyEvent.KEYCODE_NUMPAD_5,
            ),
            intArrayOf(
                KeyEvent.KEYCODE_INSERT,
                KeyEvent.KEYCODE_FORWARD_DEL,
                KeyEvent.KEYCODE_MOVE_HOME,
                KeyEvent.KEYCODE_MOVE_END,
                KeyEvent.KEYCODE_PAGE_UP,
                KeyEvent.KEYCODE_PAGE_DOWN,
                KeyEvent.KEYCODE_BREAK,
                KeyEvent.KEYCODE_SYSRQ,
                KeyEvent.KEYCODE_SCROLL_LOCK,
                KeyEvent.KEYCODE_NUM_LOCK,
                KeyEvent.KEYCODE_CTRL_RIGHT,
                KeyEvent.KEYCODE_FUNCTION,
                KeyEvent.KEYCODE_TAB,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_6,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
            ),
            intArrayOf(
                KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_CTRL_LEFT,
                KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_META_LEFT,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_ALT_RIGHT,
                KeyEvent.KEYCODE_CTRL_RIGHT,
                KeyEvent.KEYCODE_PAGE_UP,
                KeyEvent.KEYCODE_PAGE_DOWN,
                KeyEvent.KEYCODE_NUMPAD_1,
                KeyEvent.KEYCODE_NUMPAD_2,
                KeyEvent.KEYCODE_NUMPAD_3,
                KeyEvent.KEYCODE_NUMPAD_0,
            ),
        )
        for (row in keys.indices) {
            for (col in keys[row].indices) {
                addKey(mapper, row, col, keys[row][col])
            }
        }
        return mapper
    }

    private fun addKey(mapper: KeyMapper, row: Int, col: Int, keyCode: Int) {
        mapper.mapping[row][col].addKeyCode(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    }

    fun saveKeyMappers() {
        val keyMappersSet = HashSet<String>()
        for (map in keyMappers) {
            val mapper = map["key_mapper"] as? KeyMapper ?: continue
            try {
                keyMappersSet.add(getKeyMapperString(mapper))
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
        LimboSettingsManager.setKeyMappers(activity, keyMappersSet)
    }

    fun isKeyMapperActive(): Boolean = keyMapper != null

    fun isFullKeyboardActive(): Boolean = keyMapper != null && BUILT_IN_FULL_KEYBOARD_NAME == keyMapper?.name

    fun isEditMode(): Boolean =
        mapperEditLayout != null && mapperEditLayout?.visibility == View.VISIBLE

    fun setOnSendKeyEventListener(listener: OnSendKeyEventListener?) {
        sendKeyEventListener = listener
    }

    fun setOnSendMouseEventListener(listener: OnSendMouseEventListener?) {
        sendMouseEventListener = listener
    }

    fun setOnUnhandledTouchEventListener(listener: OnUnhandledTouchEventListener?) {
        unhundledTouchEventListener = listener
    }

    fun interface OnSendKeyEventListener {
        fun onSendKeyEvent(keyCode: Int, down: Boolean)
    }

    fun interface OnSendMouseEventListener {
        fun onSendMouseEvent(keyCode: Int, down: Boolean)
    }

    fun interface OnUnhandledTouchEventListener {
        fun OnUnhandledTouchEvent(event: MotionEvent)
    }

    internal class KeyMapperListAdapter(
        context: Context,
        data: List<Map<String, Any?>>,
        resource: Int,
        from: Array<String>,
        to: IntArray,
    ) : SimpleAdapter(context, data, resource, from, to)

    companion object {
        private const val BUILT_IN_FULL_KEYBOARD_NAME = "Full Desktop Keyboard (F1-F12 + Numpad)"
    }
}
