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

import android.content.Context
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import com.max2idea.android.limbo.machine.MachineAction
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLControllerManager
import java.util.ArrayList

/**
 * SDL surface extension for mouse, trackpad, touch-screen, and keymapper input.
 */
class LimboSDLSurface(
    private val sdlActivity: LimboSDLActivity,
    context: Context,
) : SDLActivity.ExSDLSurface(context),
    View.OnKeyListener,
    View.OnTouchListener {

    @JvmField val mouseState = MouseState()
    private var firstTouch = false

    init {
        setOnKeyListener(this)
        setOnTouchListener(this)
        setOnGenericMotionListener(SDLGenericMotionListenerApi12())
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged: ${width}x$height")
        super.surfaceChanged(holder, format, width, height)
        refreshSurfaceView()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated")
        super.surfaceCreated(holder)
        setWillNotDraw(false)
        refreshSurfaceView()
    }

    fun refreshSurfaceView() {
        Thread {
            sdlActivity.setFullscreen()
            sdlActivity.notifyAction(
                MachineAction.DISPLAY_CHANGED,
                arrayOf<Any>(width, height, resources.configuration.orientation),
            )
        }.start()
    }

    fun onTouchProcess(view: View?, event: MotionEvent): Boolean {
        val action = event.actionMasked
        mouseState.x = event.x
        mouseState.y = event.y

        processMouseMovement(action, event.getToolType(0), mouseState.x, mouseState.y)
        processMouseButton(event, action, mouseState.x, mouseState.y)
        return false
    }

    private fun processMouseMovement(action: Int, toolType: Int, x: Float, y: Float) {
        if (action == MotionEvent.ACTION_MOVE) {
            if (mouseState.mouseUp) {
                mouseState.oldX = x
                mouseState.oldY = y
                mouseState.mouseUp = false
            }

            var nx = x
            var ny = y
            if (sdlActivity.isRelativeMode(toolType)) {
                nx = x - mouseState.oldX
                ny = y - mouseState.oldY
            }
            sdlActivity.sendMouseEvent(0, MotionEvent.ACTION_MOVE, toolType, nx, ny)

            mouseState.oldX = x
            mouseState.oldY = y
        }
    }

    private fun processMouseButton(event: MotionEvent, action: Int, x: Float, y: Float) {
        processPendingMouseButtonDown(action, event.getToolType(0), x, y)
        var sdlMouseButton = getMouseButton(event)

        if (action == MotionEvent.ACTION_UP) {
            mouseState.addAction(event.getToolType(0), System.currentTimeMillis(), event.actionMasked, x, y)
            if (sdlMouseButton == Config.SDL_MOUSE_MIDDLE ||
                sdlMouseButton == Config.SDL_MOUSE_RIGHT ||
                sdlMouseButton != 0
            ) {
                if (sdlActivity.isRelativeMode(event.getToolType(0))) {
                    sdlActivity.sendMouseEvent(
                        sdlMouseButton,
                        MotionEvent.ACTION_UP,
                        event.getToolType(0),
                        0f,
                        0f,
                    )
                } else {
                    sdlActivity.sendMouseEvent(sdlMouseButton, MotionEvent.ACTION_UP, event.getToolType(0), x, y)
                }
            } else {
                guessMouseButtonUp(event.getToolType(0), x, y)
            }
            mouseState.lastMouseButtonDown = -1
            mouseState.mouseUp = true
        } else if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            mouseState.addAction(event.getToolType(0), System.currentTimeMillis(), event.actionMasked, x, y)
            if (sdlMouseButton == 0 && MotionEvent.TOOL_TYPE_FINGER == event.getToolType(0)) {
                sdlMouseButton = Config.SDL_MOUSE_LEFT
            }
            if (sdlActivity.isRelativeMode(event.getToolType(0))) {
                if (!firstTouch) {
                    sdlActivity.sendMouseEvent(
                        sdlMouseButton,
                        MotionEvent.ACTION_DOWN,
                        event.getToolType(0),
                        0f,
                        0f,
                    )
                    firstTouch = true
                } else {
                    setPendingMouseDown(x, y, sdlMouseButton)
                }
            } else {
                sdlActivity.sendMouseEvent(sdlMouseButton, MotionEvent.ACTION_DOWN, event.getToolType(0), x, y)
            }
            mouseState.lastMouseButtonDown = sdlMouseButton
        }
    }

    private fun processPendingMouseButtonDown(action: Int, toolType: Int, x: Float, y: Float) {
        val delta = System.currentTimeMillis() - mouseState.downEventTime
        if (
            mouseState.downPending &&
            sdlActivity.isRelativeMode(toolType) &&
            kotlin.math.abs(x - mouseState.downX) < 20 &&
            kotlin.math.abs(y - mouseState.downY) < 20 &&
            ((action == MotionEvent.ACTION_MOVE && delta > 400) || action == MotionEvent.ACTION_UP)
        ) {
            sdlActivity.sendMouseEvent(mouseState.downMouseButton, MotionEvent.ACTION_DOWN, toolType, 0f, 0f)
            mouseState.downPending = false
        } else if (System.currentTimeMillis() - mouseState.downEventTime > 400) {
            mouseState.downPending = false
        }
    }

    private fun getMouseButton(event: MotionEvent): Int =
        when (event.buttonState) {
            MotionEvent.BUTTON_PRIMARY -> Config.SDL_MOUSE_LEFT
            MotionEvent.BUTTON_SECONDARY -> Config.SDL_MOUSE_RIGHT
            MotionEvent.BUTTON_TERTIARY -> Config.SDL_MOUSE_MIDDLE
            else -> 0
        }

    private fun guessMouseButtonUp(toolType: Int, x: Float, y: Float) {
        if (mouseState.lastMouseButtonDown > 0) {
            if (sdlActivity.isRelativeMode(toolType)) {
                sdlActivity.sendMouseEvent(mouseState.lastMouseButtonDown, MotionEvent.ACTION_UP, toolType, 0f, 0f)
            } else {
                sdlActivity.sendMouseEvent(mouseState.lastMouseButtonDown, MotionEvent.ACTION_UP, toolType, x, y)
            }
        } else {
            if (sdlActivity.isRelativeMode(toolType)) {
                sdlActivity.sendMouseEvent(Config.SDL_MOUSE_LEFT, MotionEvent.ACTION_UP, toolType, 0f, 0f)
            } else {
                sdlActivity.sendMouseEvent(Config.SDL_MOUSE_LEFT, MotionEvent.ACTION_UP, toolType, x, y)
                sdlActivity.sendMouseEvent(Config.SDL_MOUSE_RIGHT, MotionEvent.ACTION_UP, toolType, x, y)
                sdlActivity.sendMouseEvent(Config.SDL_MOUSE_MIDDLE, MotionEvent.ACTION_UP, toolType, x, y)
            }
        }
    }

    private fun setPendingMouseDown(x: Float, y: Float, sdlMouseButton: Int) {
        mouseState.downPending = true
        mouseState.downX = x
        mouseState.downY = y
        mouseState.downMouseButton = sdlMouseButton
        mouseState.downEventTime = System.currentTimeMillis()
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean =
        processExternalMouseEvents(view, event)

    private fun processExternalMouseEvents(view: View?, event: MotionEvent): Boolean {
        if (
            event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER ||
            LimboSDLActivity.mouseMode == LimboSDLActivity.MouseMode.TOUCHSCREEN
        ) {
            onTouchProcess(view, event)
            return true
        }
        return false
    }

    override fun onKey(view: View, keyCode: Int, event: KeyEvent): Boolean {
        return if (event.source != InputDevice.SOURCE_KEYBOARD) {
            super.onKey(view, keyCode, event)
        } else {
            false
        }
    }

    class MouseState {
        @JvmField var x = 0f
        @JvmField var y = 0f
        var oldX = 0f
        var oldY = 0f
        var downX = 0f
        var downY = 0f
        var downMouseButton = 0
        var downEventTime = 0L
        @JvmField val taps = ArrayList<MouseAction>()
        var mouseUp = true
        var lastMouseButtonDown = -1
        var downPending = false

        fun addAction(toolType: Int, time: Long, actionMasked: Int, x: Float, y: Float) {
            if (taps.size > 1 && time - taps[taps.size - 2].time > 200) {
                taps.clear()
            } else if (taps.size == 4) {
                taps.clear()
            }
            taps.add(MouseAction(toolType, time, actionMasked, x.toInt(), y.toInt()))
        }

        fun isDoubleTap(): Boolean =
            LimboSDLActivity.mouseMode == LimboSDLActivity.MouseMode.TOUCHSCREEN &&
                taps.size >= 3 &&
                taps[0].toolType == MotionEvent.TOOL_TYPE_FINGER &&
                taps[0].action == MotionEvent.ACTION_DOWN &&
                taps[1].toolType == MotionEvent.TOOL_TYPE_FINGER &&
                taps[1].action == MotionEvent.ACTION_UP &&
                taps[0].x == taps[1].x &&
                taps[0].y == taps[1].y &&
                taps[2].toolType == MotionEvent.TOOL_TYPE_FINGER &&
                taps[2].action == MotionEvent.ACTION_DOWN

        class MouseAction(
            @JvmField var toolType: Int,
            val time: Long,
            @JvmField var action: Int,
            @JvmField var x: Int,
            @JvmField var y: Int,
        )
    }

    private inner class SDLGenericMotionListenerApi12 : View.OnGenericMotionListener {
        override fun onGenericMotion(view: View, event: MotionEvent): Boolean {
            when (event.source) {
                InputDevice.SOURCE_JOYSTICK,
                InputDevice.SOURCE_GAMEPAD,
                InputDevice.SOURCE_DPAD -> {
                    SDLControllerManager.handleJoystickMotionEvent(event)
                    return true
                }
                InputDevice.SOURCE_MOUSE -> {
                    val action = event.actionMasked
                    when (action) {
                        MotionEvent.ACTION_SCROLL -> {
                            val x = event.getAxisValue(MotionEvent.AXIS_HSCROLL, 0)
                            val y = event.getAxisValue(MotionEvent.AXIS_VSCROLL, 0)
                            sdlActivity.sendMouseEvent(0, action, event.getToolType(0), x, y)
                            return true
                        }
                        MotionEvent.ACTION_HOVER_MOVE -> {
                            if (Config.processMouseHistoricalEvents) {
                                val historySize = event.historySize
                                for (h in 0 until historySize) {
                                    val x = event.getHistoricalX(h)
                                    val y = event.getHistoricalY(h)
                                    sdlActivity.sendMouseEvent(0, action, event.getToolType(0), x, y)
                                }
                            }
                            sdlActivity.sendMouseEvent(0, action, event.getToolType(0), event.x, event.y)
                            return true
                        }
                        MotionEvent.ACTION_UP -> Unit
                        else -> Unit
                    }
                }
            }
            return false
        }
    }

    companion object {
        private const val TAG = "LimboSDLSurface"
    }
}
