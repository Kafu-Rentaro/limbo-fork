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

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.max2idea.android.limbo.main.Config
import java.util.LinkedHashSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders user-defined keys and delegates unmatched touches to the main VM surface.
 */
class KeySurfaceView : SurfaceView, SurfaceHolder.Callback {
    @JvmField
    var mapping: Array<Array<KeyMapper.KeyMapping>>? = null

    @JvmField
    var pointers: HashMap<Int, KeyMapper.KeyMapping> = HashMap()

    private val drawLock = Any()
    private val mPaintKey = Paint()
    private val mPaintKeyRepeat = Paint()
    private val mPaintKeySelected = Paint()
    private val mPaintKeySelectedRepeat = Paint()
    private val mPaintKeyEmpty = Paint()
    private val mPaintText = Paint()
    private val mPaintTextSmall = Paint()
    private val repeaterExecutor: ExecutorService = Executors.newFixedThreadPool(1)

    private var keySurfaceHolder: SurfaceHolder? = null
    private var keyMapManager: KeyMapManager? = null
    private var surfaceReady = false
    private var controlAreaFraction = DEFAULT_CONTROL_AREA_FRACTION
    private var controlAreaOnRight = false
    private var buttonLayoutTop = 0
    private var buttonLayoutLeft = 0
    private var buttonLayoutWidth = 0
    private var buttonLayoutHeight = 0
    private var buttonWidth = 0
    private var buttonHeight = 0
    private var minRowLeft = -1
    private var minRowRight = -1
    private var vertFontOffset = 0
    private var horizFontOffset = 0

    private val manager: KeyMapManager
        get() = keyMapManager ?: throw IllegalStateException("KeyMapManager is not attached")

    constructor(context: Context) : super(context) {
        setupPaints()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        setupSurfaceHolder()
        setupPaints()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setupSurfaceHolder()
        setupPaints()
    }

    constructor(context: Context, keyMapManager: KeyMapManager) : super(context) {
        setupSurfaceHolder()
        setupPaints()
        this.keyMapManager = keyMapManager
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        paint(true)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    override fun onConfigurationChanged(configuration: Configuration) {
        updateDimensions()
    }

    private fun setupPaints() {
        mPaintKey.color = Color.parseColor("#7768EFFF")
        mPaintKeySelected.color = Color.parseColor("#7768B0FF")
        mPaintKeyRepeat.color = Color.parseColor("#77FBC69A")
        mPaintKeySelectedRepeat.color = Color.parseColor("#77FF7A6F")
        mPaintKeyEmpty.color = Color.parseColor("#00FFFFFF")

        mPaintText.color = Color.BLACK
        mPaintText.textSize = 36f
        mPaintText.isFakeBoldText = true
        mPaintText.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        mPaintTextSmall.color = Color.BLACK
        mPaintTextSmall.textSize = 24f
        mPaintTextSmall.isFakeBoldText = true
        mPaintTextSmall.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        updateTextMetrics()
    }

    private fun updateTextMetrics() {
        val result = Rect()
        mPaintText.getTextBounds("O", 0, 1, result)
        vertFontOffset = result.height()
        horizFontOffset = result.width()
    }

    private fun setupSurfaceHolder() {
        keySurfaceHolder = holder
        keySurfaceHolder?.addCallback(this)
        keySurfaceHolder?.setFormat(PixelFormat.TRANSPARENT)
        setZOrderOnTop(true)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!manager.isKeyMapperActive()) {
            return false
        }
        val action = event.actionMasked
        val pointerCount = if (manager.isEditMode()) 1 else event.pointerCount

        var filterIndex: Int? = null
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            @Suppress("DEPRECATION")
            filterIndex = (event.action and MotionEvent.ACTION_POINTER_INDEX_MASK) shr
                MotionEvent.ACTION_POINTER_INDEX_SHIFT
        }

        for (i in 0 until pointerCount) {
            if (filterIndex != null && filterIndex != i) {
                continue
            }
            val col = getColumnFromEvent(event, i)
            val row = getRowFromEvent(event, i)
            val pointerId = event.getPointerId(i)
            val prevKeyMapping = pointers[pointerId]
            val keyPressed = isKeyPressed(row, col, action, prevKeyMapping)

            if (keyPressed) {
                processKeyPressed(pointerId, row, col, action, prevKeyMapping)
            } else {
                cancelKeyPressed(pointerId, prevKeyMapping)
                if (!manager.isEditMode() && manager.unhundledTouchEventListener != null) {
                    delegateEvent(event, i)
                }
            }
        }
        paint(true)
        return true
    }

    private fun isKeyPressed(
        row: Int,
        col: Int,
        action: Int,
        prevKeyMapping: KeyMapper.KeyMapping?,
    ): Boolean {
        val mapper = manager.keyMapper ?: return false
        return when {
            (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) && prevKeyMapping == null -> false
            isWideKeyboardLayout() &&
                row < mapper.rows && row >= 0 &&
                col < mapper.cols && col >= 0 -> true
            ((col < mapper.cols / 2 && row > minRowLeft) ||
                (col >= mapper.cols / 2 && row > minRowRight)) &&
                row < mapper.rows && row >= 0 &&
                col < mapper.cols && col >= 0 -> true
            else -> false
        }
    }

    private fun getRowFromEvent(event: MotionEvent, index: Int): Int {
        var row = -1
        if (buttonHeight > 0 && event.getY(index) >= buttonLayoutTop) {
            row = ((event.getY(index) - buttonLayoutTop) / buttonHeight).toInt()
        }
        return row
    }

    private fun getColumnFromEvent(event: MotionEvent, index: Int): Int {
        var col = -1
        val mapper = manager.keyMapper ?: return col
        if (buttonWidth <= 0) {
            return col
        }
        if (isWideKeyboardLayout()) {
            if (event.getX(index) >= buttonLayoutLeft && event.getX(index) <= buttonLayoutLeft + buttonLayoutWidth) {
                col = ((event.getX(index) - buttonLayoutLeft) / buttonWidth).toInt()
                col = min(col, mapper.cols - 1)
            }
        } else if (event.getX(index) >= width - buttonLayoutWidth) {
            col = ((event.getX(index) - (width - buttonLayoutWidth)) / buttonWidth).toInt() + mapper.cols / 2
            col = min(col, mapper.cols - 1)
        } else if (event.getX(index) >= buttonLayoutLeft && event.getX(index) < buttonLayoutLeft + buttonLayoutWidth) {
            col = ((event.getX(index) - buttonLayoutLeft) / buttonWidth).toInt()
            col = min(col, mapper.cols / 2 - 1)
        }
        return col
    }

    private fun cancelKeyPressed(pointerId: Int, keyMapping: KeyMapper.KeyMapping?) {
        if (!manager.isEditMode() && keyMapping != null) {
            sendKeyEvents(keyMapping, false)
            sendMouseEvents(keyMapping, false)
            pointers.remove(pointerId)
        }
    }

    private fun processKeyPressed(
        pointerId: Int,
        row: Int,
        col: Int,
        action: Int,
        prevKeyMapping: KeyMapper.KeyMapping?,
    ) {
        val keyMapping = mapping!![row][col]
        if (action == MotionEvent.ACTION_DOWN ||
            action == MotionEvent.ACTION_POINTER_DOWN ||
            action == MotionEvent.ACTION_MOVE
        ) {
            if (prevKeyMapping != null && pointers[pointerId] !== keyMapping) {
                sendKeyEvents(prevKeyMapping, false)
                sendMouseEvents(prevKeyMapping, false)
                pointers.remove(pointerId)
            }
            if (!pointers.containsKey(pointerId)) {
                pointers[pointerId] = keyMapping
                if (keyMapping.isRepeat()) {
                    startRepeater()
                } else {
                    sendKeyEvents(keyMapping, true)
                    sendMouseEvents(keyMapping, true)
                }
            } else if (manager.isEditMode() && action == MotionEvent.ACTION_DOWN) {
                if (pointers[pointerId] === keyMapping) {
                    pointers.remove(pointerId)
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (!manager.isEditMode() && prevKeyMapping != null) {
                if (!keyMapping.isRepeat()) {
                    sendKeyEvents(prevKeyMapping, false)
                    sendMouseEvents(prevKeyMapping, false)
                }
                pointers.remove(pointerId)
            }
        }
    }

    private fun delegateEvent(event: MotionEvent, index: Int) {
        var action = event.actionMasked
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            action = MotionEvent.ACTION_DOWN
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            action = MotionEvent.ACTION_UP
        }

        val pointerProperties = MotionEvent.PointerProperties()
        event.getPointerProperties(index, pointerProperties)
        val pointerCoordinates = MotionEvent.PointerCoords()
        event.getPointerCoords(index, pointerCoordinates)
        val delegatedEvent = MotionEvent.obtain(
            event.downTime,
            event.eventTime,
            action,
            1,
            arrayOf(MotionEvent.PointerProperties(pointerProperties)),
            arrayOf(MotionEvent.PointerCoords(pointerCoordinates)),
            event.metaState,
            event.buttonState,
            event.xPrecision,
            event.yPrecision,
            event.deviceId,
            event.edgeFlags,
            event.source,
            event.flags,
        )
        if (event.pointerCount > 1) {
            delegatedEvent.source = SOURCE_KEYMAP_MULTIPOINT
        } else {
            delegatedEvent.source = SOURCE_KEYMAP_SINGLEPOINT
        }
        manager.unhundledTouchEventListener?.OnUnhandledTouchEvent(delegatedEvent)
    }

    private fun startRepeater() {
        repeaterExecutor.submit {
            try {
                runRepeater()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun runRepeater() {
        var atLeastOneKey = true
        while (atLeastOneKey) {
            atLeastOneKey = false
            for (keyMapping in pointers.values) {
                if (!keyMapping.isRepeat()) {
                    continue
                }
                if (keyMapping.getKeyCodes().size == 0 && keyMapping.getMouseButtons().size == 0) {
                    continue
                }
                atLeastOneKey = true
                sendKeyEvents(keyMapping, true)
                sendMouseEvents(keyMapping, true)
                delay(KEY_REPEAT_MS)
                sendKeyEvents(keyMapping, false)
                sendMouseEvents(keyMapping, false)
                delay(KEY_REPEAT_MS)
            }
        }
    }

    private fun delay(keyRepeatMs: Long) {
        try {
            Thread.sleep(keyRepeatMs)
        } catch (ex: InterruptedException) {
            ex.printStackTrace()
        }
    }

    private fun sendMouseEvents(keyMapping: KeyMapper.KeyMapping, down: Boolean) {
        if (manager.isEditMode()) {
            return
        }
        for (mouseButton in keyMapping.getMouseButtons()) {
            manager.sendMouseEvent(mouseButton, down)
        }
    }

    private fun sendKeyEvents(keyMapping: KeyMapper.KeyMapping, down: Boolean) {
        if (manager.isEditMode()) {
            return
        }
        for (keyCode in keyMapping.getKeyCodes()) {
            manager.sendKeyEvent(keyCode, down)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        updateDimensions()
        paint(false)
    }

    fun updateDimensions() {
        val height = height
        val width = width
        val mapper = manager.keyMapper
        val controlAreaHeight = if (controlAreaOnRight) {
            height
        } else {
            max(1, (height * controlAreaFraction).roundToInt())
        }
        val controlAreaWidth = if (controlAreaOnRight) {
            max(1, (width * controlAreaFraction).roundToInt())
        } else {
            width
        }
        buttonLayoutTop = if (controlAreaOnRight) 0 else max(0, height - controlAreaHeight)
        buttonLayoutLeft = if (controlAreaOnRight) max(0, width - controlAreaWidth) else 0
        buttonLayoutWidth = controlAreaWidth
        buttonLayoutHeight = controlAreaHeight

        if (mapper == null) {
            buttonWidth = 1
            buttonHeight = 1
        } else if (isWideKeyboardLayout()) {
            buttonWidth = max(1, buttonLayoutWidth / mapper.cols)
            buttonHeight = max(1, buttonLayoutHeight / mapper.rows)
        } else if (controlAreaOnRight) {
            buttonLayoutWidth = max(1, controlAreaWidth / 2)
            buttonLayoutHeight = min(height, controlAreaWidth / 2)
            buttonLayoutTop = max(0, height - buttonLayoutHeight)
            buttonWidth = max(1, buttonLayoutWidth / (mapper.cols / 2))
            buttonHeight = max(1, buttonLayoutHeight / mapper.rows)
        } else {
            buttonLayoutHeight = min(controlAreaHeight, width / 2)
            buttonLayoutTop = max(0, height - buttonLayoutHeight)
            buttonLayoutWidth = buttonLayoutHeight
            buttonWidth = max(1, buttonLayoutWidth / (mapper.cols / 2))
            buttonHeight = max(1, buttonLayoutHeight / mapper.rows)
        }

        val primaryTextSize = max(10f, min(36f, min(buttonWidth, buttonHeight) * 0.42f))
        mPaintText.textSize = primaryTextSize
        mPaintTextSmall.textSize = max(8f, primaryTextSize * 0.66f)
        updateTextMetrics()

        minRowLeft = -1
        minRowRight = -1
        if (!manager.isEditMode() && mapper != null) {
            minRowLeft = getMinRow(0, mapper.cols / 2)
            minRowRight = getMinRow(mapper.cols / 2, mapper.cols)
        }
    }

    private fun getMinRow(startCol: Int, endCol: Int): Int {
        val mapper = manager.keyMapper ?: return -1
        var minRow = -1
        for (row in 0 until mapper.rows) {
            var cellsMissing = 0
            for (col in startCol until endCol) {
                if (mapper.mapping[row][col].getKeyCodes().size == 0 &&
                    mapper.mapping[row][col].getMouseButtons().size == 0
                ) {
                    cellsMissing++
                } else {
                    break
                }
            }
            if (cellsMissing == mapper.cols / 2) {
                minRow = row
            } else {
                break
            }
        }
        return minRow
    }

    @Synchronized
    fun paint(clear: Boolean) {
        if (!surfaceReady || keySurfaceHolder == null) {
            Log.w(TAG, "Cannot paint surface not ready")
            return
        }

        var canvas: Canvas? = null
        try {
            canvas = keySurfaceHolder?.lockCanvas()
            if (canvas == null) {
                return
            }
            synchronized(drawLock) {
                canvas.drawColor(Color.parseColor("#00FFFFFF"), PorterDuff.Mode.CLEAR)
                if (manager.keyMapper != null) {
                    for (keyMapping in pointers.values) {
                        drawButton(canvas, keyMapping.getX(), keyMapping.getY(), true, keyMapping.isRepeat())
                    }
                    drawButtons(canvas)
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            if (canvas != null) {
                keySurfaceHolder?.unlockCanvasAndPost(canvas)
            }
        }
    }

    fun setControlAreaFraction(fraction: Float) {
        setControlArea(fraction, false)
    }

    fun setControlArea(fraction: Float, onRight: Boolean) {
        var targetFraction = fraction
        if (targetFraction <= 0f) {
            targetFraction = DEFAULT_CONTROL_AREA_FRACTION
        }
        val clampedFraction = max(
            MIN_CONTROL_AREA_FRACTION,
            min(MAX_CONTROL_AREA_FRACTION, targetFraction),
        )
        if (abs(controlAreaFraction - clampedFraction) < 0.01f && controlAreaOnRight == onRight) {
            return
        }
        controlAreaFraction = clampedFraction
        controlAreaOnRight = onRight
        updateDimensions()
    }

    private fun isWideKeyboardLayout(): Boolean =
        keyMapManager != null && manager.isFullKeyboardActive()

    private fun getKeyboardColumnsForBlock(col: Int): Int {
        val mapper = manager.keyMapper!!
        return if (isWideKeyboardLayout()) mapper.cols else mapper.cols / 2
    }

    private fun getKeyboardBlockLeft(col: Int): Int {
        val mapper = manager.keyMapper!!
        return if (isWideKeyboardLayout() || col < mapper.cols / 2) {
            buttonLayoutLeft
        } else {
            width - buttonLayoutWidth
        }
    }

    private fun getLocalColumn(col: Int): Int {
        val mapper = manager.keyMapper!!
        return if (isWideKeyboardLayout()) col else col % (mapper.cols / 2)
    }

    private fun getCellLeft(col: Int): Int = getKeyboardBlockLeft(col) + getLocalColumn(col) * buttonWidth

    private fun getCellRight(col: Int): Int {
        val columns = getKeyboardColumnsForBlock(col)
        return if (getLocalColumn(col) == columns - 1) {
            getKeyboardBlockLeft(col) + buttonLayoutWidth
        } else {
            getCellLeft(col) + buttonWidth
        }
    }

    private fun getCellTop(row: Int): Int = buttonLayoutTop + row * buttonHeight

    private fun getCellBottom(row: Int): Int {
        val mapper = manager.keyMapper!!
        return if (row == mapper.rows - 1) {
            buttonLayoutTop + buttonLayoutHeight
        } else {
            getCellTop(row) + buttonHeight
        }
    }

    private fun drawButton(canvas: Canvas, row: Int, col: Int, isSelected: Boolean, repeat: Boolean) {
        val paint = if (isSelected) {
            if (repeat) mPaintKeySelectedRepeat else mPaintKeySelected
        } else {
            if (repeat) mPaintKeyRepeat else mPaintKey
        }

        val keyMapping = mapping!![row][col]
        val keyCodes = keyMapping.getKeyCodes()
        val mouseButtons = keyMapping.getMouseButtons()
        if (keyCodes.size > 0 || mouseButtons.size > 0 || manager.isEditMode()) {
            canvas.drawRect(
                (getCellLeft(col) + KEY_CELL_PADDING).toFloat(),
                (getCellTop(row) + KEY_CELL_PADDING).toFloat(),
                (getCellRight(col) - KEY_CELL_PADDING).toFloat(),
                (getCellBottom(row) - KEY_CELL_PADDING).toFloat(),
                paint,
            )
        }
    }

    private fun drawButtons(canvas: Canvas) {
        val mapper = manager.keyMapper ?: return
        val currentMapping = mapping ?: return
        for (row in 0 until mapper.rows) {
            for (col in 0 until mapper.cols) {
                if (!pointers.containsValue(currentMapping[row][col]) &&
                    (manager.isEditMode() ||
                        currentMapping[row][col].getKeyCodes().size > 0 ||
                        currentMapping[row][col].getMouseButtons().size > 0)
                ) {
                    drawButton(canvas, row, col, false, currentMapping[row][col].isRepeat())
                }
                drawText(canvas, row, col)
            }
        }
    }

    private fun drawText(canvas: Canvas, row: Int, col: Int) {
        val keyMapping = mapping!![row][col]
        if (keyMapping.getKeyCodes().size > 0 || keyMapping.getMouseButtons().size > 0) {
            val texts = getText(keyMapping)
            var count = 0
            for (text in texts) {
                if (text != null) {
                    val verticalOffset = getVertFontOffset(count)
                    val horizontalOffset = getHorizFontOffset(count) * 2
                    drawText(
                        canvas,
                        text,
                        row,
                        col,
                        horizontalOffset,
                        verticalOffset,
                        if (count == 0) mPaintText else mPaintTextSmall,
                        getAlignPos(count),
                    )
                }
                count++
            }
        }
    }

    private fun getHorizFontOffset(count: Int): Int =
        if (count == 1 || count == 3) {
            -horizFontOffset
        } else if (count == 2 || count == 4) {
            horizFontOffset
        } else {
            0
        }

    private fun getVertFontOffset(count: Int): Int =
        if (count == 1 || count == 2) {
            -vertFontOffset
        } else if (count == 3 || count == 4) {
            vertFontOffset
        } else {
            0
        }

    private fun getAlignPos(pos: Int): Paint.Align = Paint.Align.CENTER

    private fun drawText(
        canvas: Canvas,
        text: String,
        row: Int,
        col: Int,
        horizontalOffset: Int,
        verticalOffset: Int,
        paint: Paint,
        align: Paint.Align,
    ) {
        paint.textAlign = align
        canvas.drawText(
            text,
            (getCellLeft(col) + horizontalOffset + (getCellRight(col) - getCellLeft(col)) / 2).toFloat(),
            (getCellTop(row) + verticalOffset + (getCellBottom(row) - getCellTop(row)) / 2).toFloat(),
            paint,
        )
    }

    fun setKeyCode(event: KeyEvent) {
        for (keyMapping in pointers.values) {
            keyMapping.addKeyCode(event.keyCode, event)
            break
        }
        manager.saveKeyMappers()
    }

    fun setMouseButton(button: Int) {
        for (keyMapping in pointers.values) {
            keyMapping.addMouseButton(button)
            break
        }
        manager.saveKeyMappers()
    }

    fun getText(keyMapping: KeyMapper.KeyMapping): Array<String?> {
        val keys = LinkedHashSet<String>()
        val buttons = LinkedHashSet<String>()
        val meta = LinkedHashSet<String>()
        val keyCodes = keyMapping.getKeyCodes()
        val unicodeChars = keyMapping.getUnicodeChars()

        val keyIterator = keyCodes.iterator()
        val unicodeIterator = unicodeChars.iterator()
        while (keyIterator.hasNext() && unicodeIterator.hasNext()) {
            val keyCode = keyIterator.next()
            val unicodeChar = unicodeIterator.next()
            if (KeyMapper.KeyMapping.isKeyMeta(keyCode)) {
                if (keyMapping.hasModifiableKeys() &&
                    (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT)
                ) {
                    continue
                }
                meta.add(translateCode(keyCode, -1))
                continue
            }
            keys.add(translateCode(keyCode, unicodeChar))
        }

        for (button in keyMapping.getMouseButtons()) {
            val text = translateMouseBtn(button)
            if (text != null) {
                buttons.add(text)
            }
        }

        val textCenter = StringBuilder()
        var centerKeys = 0
        centerKeys = moveTextToCenter(textCenter, ArrayList(keys), keys, centerKeys)
        centerKeys = moveTextToCenter(textCenter, ArrayList(buttons), buttons, centerKeys)
        moveTextToCenter(textCenter, ArrayList(meta), meta, centerKeys)

        val texts = arrayOfNulls<String>(KeyMapper.KeyMapping.MAX_KEY_MOUSE_BTNS - 1)
        texts[0] = textCenter.toString()
        var count = 1
        for (metaText in meta) {
            texts[count++] = metaText
        }
        for (keyText in keys) {
            texts[count++] = "+$keyText"
        }
        for (buttonText in buttons) {
            texts[count++] = "+$buttonText"
        }
        return texts
    }

    private fun translateMouseBtn(button: Int): String? =
        when (button) {
            Config.SDL_MOUSE_LEFT -> "Mbl"
            Config.SDL_MOUSE_MIDDLE -> "Mbm"
            Config.SDL_MOUSE_RIGHT -> "Mbr"
            else -> null
        }

    private fun moveTextToCenter(
        textCenter: StringBuilder,
        texts: ArrayList<String>,
        src: MutableSet<String>,
        centerKeys: Int,
    ): Int {
        var count = centerKeys
        for (text in texts) {
            if (count >= 2) {
                break
            }
            if (textCenter.isNotEmpty()) {
                textCenter.insert(0, "+")
            }
            textCenter.insert(0, text)
            src.remove(text)
            count++
        }
        return count
    }

    private fun translateCode(keycode: Int, unicodeChar: Int): String =
        when {
            keycode == KeyEvent.KEYCODE_DPAD_UP -> 0x21E7.toChar().toString()
            keycode == KeyEvent.KEYCODE_DPAD_DOWN -> 0x21E9.toChar().toString()
            keycode == KeyEvent.KEYCODE_DPAD_RIGHT -> 0x21E8.toChar().toString()
            keycode == KeyEvent.KEYCODE_DPAD_LEFT -> 0x21E6.toChar().toString()
            keycode == KeyEvent.KEYCODE_ESCAPE -> "Esc"
            keycode == KeyEvent.KEYCODE_ENTER -> 0x23CE.toChar().toString()
            keycode == KeyEvent.KEYCODE_TAB -> "Tab"
            keycode == KeyEvent.KEYCODE_CTRL_LEFT -> "Ctrl"
            keycode == KeyEvent.KEYCODE_CTRL_RIGHT -> "Ctrl"
            keycode == KeyEvent.KEYCODE_ALT_LEFT -> "Alt"
            keycode == KeyEvent.KEYCODE_ALT_RIGHT -> "Alt"
            keycode == KeyEvent.KEYCODE_SHIFT_LEFT -> "Shft"
            keycode == KeyEvent.KEYCODE_SHIFT_RIGHT -> "Shft"
            keycode == KeyEvent.KEYCODE_DEL -> "Bksp"
            keycode == KeyEvent.KEYCODE_FORWARD_DEL -> "Del"
            keycode == KeyEvent.KEYCODE_INSERT -> "Ins"
            keycode == KeyEvent.KEYCODE_MOVE_END -> "End"
            keycode == KeyEvent.KEYCODE_SYSRQ -> "Syrq"
            keycode == KeyEvent.KEYCODE_MOVE_HOME -> "Home"
            keycode == KeyEvent.KEYCODE_SPACE -> "Spc"
            keycode == KeyEvent.KEYCODE_PAGE_UP -> "PgUp"
            keycode == KeyEvent.KEYCODE_PAGE_DOWN -> "PgDn"
            keycode == KeyEvent.KEYCODE_BREAK -> "Brk"
            keycode == KeyEvent.KEYCODE_SCROLL_LOCK -> "Scrl"
            keycode == KeyEvent.KEYCODE_NUM_LOCK -> "NumL"
            keycode == KeyEvent.KEYCODE_F1 -> "F1"
            keycode == KeyEvent.KEYCODE_F2 -> "F2"
            keycode == KeyEvent.KEYCODE_F3 -> "F3"
            keycode == KeyEvent.KEYCODE_F4 -> "F4"
            keycode == KeyEvent.KEYCODE_F5 -> "F5"
            keycode == KeyEvent.KEYCODE_F6 -> "F6"
            keycode == KeyEvent.KEYCODE_F7 -> "F7"
            keycode == KeyEvent.KEYCODE_F8 -> "F8"
            keycode == KeyEvent.KEYCODE_F9 -> "F9"
            keycode == KeyEvent.KEYCODE_F10 -> "F10"
            keycode == KeyEvent.KEYCODE_F11 -> "F11"
            keycode == KeyEvent.KEYCODE_F12 -> "F12"
            keycode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ->
                ('A'.code + keycode - KeyEvent.KEYCODE_A).toChar().toString()
            keycode == KeyEvent.KEYCODE_0 -> "0"
            keycode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 ->
                ('1'.code + keycode - KeyEvent.KEYCODE_1).toChar().toString()
            keycode == KeyEvent.KEYCODE_GRAVE -> "`"
            keycode == KeyEvent.KEYCODE_MINUS -> "-"
            keycode == KeyEvent.KEYCODE_EQUALS -> "="
            keycode == KeyEvent.KEYCODE_LEFT_BRACKET -> "["
            keycode == KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
            keycode == KeyEvent.KEYCODE_BACKSLASH -> "\\"
            keycode == KeyEvent.KEYCODE_SEMICOLON -> ";"
            keycode == KeyEvent.KEYCODE_APOSTROPHE -> "'"
            keycode == KeyEvent.KEYCODE_COMMA -> ","
            keycode == KeyEvent.KEYCODE_PERIOD -> "."
            keycode == KeyEvent.KEYCODE_SLASH -> "/"
            keycode == KeyEvent.KEYCODE_META_LEFT || keycode == KeyEvent.KEYCODE_META_RIGHT -> "Win"
            keycode == KeyEvent.KEYCODE_MENU -> "Menu"
            keycode == KeyEvent.KEYCODE_FUNCTION -> "Fn"
            keycode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
                "N" + (keycode - KeyEvent.KEYCODE_NUMPAD_0)
            keycode == KeyEvent.KEYCODE_NUMPAD_DIVIDE -> "N/"
            keycode == KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> "N*"
            keycode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> "N-"
            keycode == KeyEvent.KEYCODE_NUMPAD_ADD -> "N+"
            keycode == KeyEvent.KEYCODE_NUMPAD_ENTER -> "NEnt"
            keycode == KeyEvent.KEYCODE_NUMPAD_DOT -> "N."
            keycode == KeyEvent.KEYCODE_NUMPAD_EQUALS -> "N="
            unicodeChar > 0 -> unicodeChar.toChar().toString()
            else -> KeyEvent.keyCodeToString(keycode).replace("KEYCODE_", "")
        }

    companion object {
        const val SOURCE_KEYMAP_SINGLEPOINT = -1
        const val SOURCE_KEYMAP_MULTIPOINT = -2
        private const val TAG = "KeySurfaceView"
        private const val KEY_CELL_PADDING = 4
        private const val KEY_REPEAT_MS = 100L
        private const val DEFAULT_CONTROL_AREA_FRACTION = 0.5f
        private const val MIN_CONTROL_AREA_FRACTION = 0.2f
        private const val MAX_CONTROL_AREA_FRACTION = 0.65f
    }
}
