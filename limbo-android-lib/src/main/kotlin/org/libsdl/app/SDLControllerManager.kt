@file:Suppress("DEPRECATION")

package org.libsdl.app

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View

class SDLControllerManager private constructor() {
    companion object {
        @JvmField
        var mJoystickHandler: SDLJoystickHandler? = null

        @JvmField
        var mHapticHandler: SDLHapticHandler? = null

        @JvmStatic
        external fun nativeSetupJNI(): Int

        @JvmStatic
        external fun nativeAddJoystick(
            device_id: Int,
            name: String?,
            desc: String?,
            is_accelerometer: Int,
            nbuttons: Int,
            naxes: Int,
            nhats: Int,
            nballs: Int,
        ): Int

        @JvmStatic
        external fun nativeRemoveJoystick(device_id: Int): Int

        @JvmStatic
        external fun nativeAddHaptic(device_id: Int, name: String?): Int

        @JvmStatic
        external fun nativeRemoveHaptic(device_id: Int): Int

        @JvmStatic
        external fun onNativePadDown(device_id: Int, keycode: Int): Int

        @JvmStatic
        external fun onNativePadUp(device_id: Int, keycode: Int): Int

        @JvmStatic
        external fun onNativeJoy(device_id: Int, axis: Int, value: Float)

        @JvmStatic
        external fun onNativeHat(device_id: Int, hat_id: Int, x: Int, y: Int)

        @JvmStatic
        fun initialize() {
            mJoystickHandler = null
            mHapticHandler = null
            setup()
        }

        @JvmStatic
        fun setup() {
            mJoystickHandler = when {
                Build.VERSION.SDK_INT >= 16 -> SDLJoystickHandler_API16()
                Build.VERSION.SDK_INT >= 12 -> SDLJoystickHandler_API12()
                else -> SDLJoystickHandler()
            }
            mHapticHandler = SDLHapticHandler()
        }

        @JvmStatic
        fun handleJoystickMotionEvent(event: MotionEvent): Boolean =
            mJoystickHandler?.handleMotionEvent(event) == true

        @JvmStatic
        fun pollInputDevices() {
            mJoystickHandler?.pollInputDevices()
        }

        @JvmStatic
        fun pollHapticDevices() {
            mHapticHandler?.pollHapticDevices()
        }

        @JvmStatic
        fun hapticRun(device_id: Int, length: Int) {
            mHapticHandler?.run(device_id, length)
        }

        @JvmStatic
        fun isDeviceSDLJoystick(deviceId: Int): Boolean {
            val device = InputDevice.getDevice(deviceId)
            if (device == null || deviceId < 0) {
                return false
            }
            val sources = device.sources
            return (
                (sources and InputDevice.SOURCE_CLASS_JOYSTICK) == InputDevice.SOURCE_CLASS_JOYSTICK ||
                    (sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD ||
                    (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                )
        }
    }
}

open class SDLJoystickHandler {
    open fun handleMotionEvent(event: MotionEvent): Boolean = false

    open fun pollInputDevices() = Unit
}

open class SDLJoystickHandler_API12 : SDLJoystickHandler() {
    class SDLJoystick {
        var device_id = 0
        var name: String? = null
        var desc: String? = null
        val axes = ArrayList<InputDevice.MotionRange>()
        val hats = ArrayList<InputDevice.MotionRange>()
    }

    private val joysticks = ArrayList<SDLJoystick>()

    override fun pollInputDevices() {
        val deviceIds = InputDevice.getDeviceIds()

        for (i in deviceIds.size - 1 downTo 0) {
            val deviceId = deviceIds[i]
            if (getJoystick(deviceId) != null) {
                continue
            }
            val joystickDevice = InputDevice.getDevice(deviceId) ?: continue
            if (!SDLControllerManager.isDeviceSDLJoystick(deviceId)) {
                continue
            }

            val joystick = SDLJoystick().apply {
                device_id = deviceId
                name = joystickDevice.name
                desc = getJoystickDescriptor(joystickDevice)
            }

            val ranges = joystickDevice.motionRanges.sortedBy { it.axis }
            for (range in ranges) {
                if ((range.source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
                    if (range.axis == MotionEvent.AXIS_HAT_X || range.axis == MotionEvent.AXIS_HAT_Y) {
                        joystick.hats.add(range)
                    } else {
                        joystick.axes.add(range)
                    }
                }
            }

            joysticks.add(joystick)
            SDLControllerManager.nativeAddJoystick(
                joystick.device_id,
                joystick.name,
                joystick.desc,
                0,
                -1,
                joystick.axes.size,
                joystick.hats.size / 2,
                0,
            )
        }

        val removedDevices = ArrayList<Int>()
        for (joystick in joysticks) {
            if (!deviceIds.contains(joystick.device_id)) {
                removedDevices.add(joystick.device_id)
            }
        }

        for (deviceId in removedDevices) {
            SDLControllerManager.nativeRemoveJoystick(deviceId)
            val iterator = joysticks.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().device_id == deviceId) {
                    iterator.remove()
                    break
                }
            }
        }
    }

    protected fun getJoystick(device_id: Int): SDLJoystick? =
        joysticks.firstOrNull { it.device_id == device_id }

    override fun handleMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != 0) {
            val actionPointerIndex = event.actionIndex
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val joystick = getJoystick(event.deviceId)
                    if (joystick != null) {
                        joystick.axes.forEachIndexed { index, range ->
                            val value = (
                                event.getAxisValue(range.axis, actionPointerIndex) -
                                    range.min
                                ) / range.range * 2.0f - 1.0f
                            SDLControllerManager.onNativeJoy(joystick.device_id, index, value)
                        }
                        var index = 0
                        while (index < joystick.hats.size) {
                            val hatX = Math.round(event.getAxisValue(joystick.hats[index].axis, actionPointerIndex))
                            val hatY = Math.round(event.getAxisValue(joystick.hats[index + 1].axis, actionPointerIndex))
                            SDLControllerManager.onNativeHat(joystick.device_id, index / 2, hatX, hatY)
                            index += 2
                        }
                    }
                }
            }
        }
        return true
    }

    open fun getJoystickDescriptor(joystickDevice: InputDevice): String? = joystickDevice.name
}

class SDLJoystickHandler_API16 : SDLJoystickHandler_API12() {
    override fun getJoystickDescriptor(joystickDevice: InputDevice): String? {
        val desc = joystickDevice.descriptor
        return if (!desc.isNullOrEmpty()) {
            desc
        } else {
            super.getJoystickDescriptor(joystickDevice)
        }
    }
}

class SDLHapticHandler {
    class SDLHaptic {
        var device_id = 0
        var name: String? = null
        var vib: Vibrator? = null
    }

    private val haptics = ArrayList<SDLHaptic>()

    fun run(device_id: Int, length: Int) {
        getHaptic(device_id)?.vib?.vibrate(length.toLong())
    }

    fun pollHapticDevices() {
        val deviceIdVibratorService = 999999
        var hasVibratorService = false

        val deviceIds = InputDevice.getDeviceIds()

        if (Build.VERSION.SDK_INT >= 16) {
            for (i in deviceIds.size - 1 downTo 0) {
                val deviceId = deviceIds[i]
                if (getHaptic(deviceId) != null) {
                    continue
                }
                val device = InputDevice.getDevice(deviceId) ?: continue
                val vibrator = device.vibrator
                if (vibrator.hasVibrator()) {
                    val haptic = SDLHaptic().apply {
                        device_id = deviceId
                        name = device.name
                        vib = vibrator
                    }
                    haptics.add(haptic)
                    SDLControllerManager.nativeAddHaptic(haptic.device_id, haptic.name)
                }
            }
        }

        val context = SDL.getContext()
        val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null) {
            hasVibratorService = if (Build.VERSION.SDK_INT >= 11) {
                vibrator.hasVibrator()
            } else {
                true
            }

            if (hasVibratorService && getHaptic(deviceIdVibratorService) == null) {
                val haptic = SDLHaptic().apply {
                    device_id = deviceIdVibratorService
                    name = "VIBRATOR_SERVICE"
                    vib = vibrator
                }
                haptics.add(haptic)
                SDLControllerManager.nativeAddHaptic(haptic.device_id, haptic.name)
            }
        }

        val removedDevices = ArrayList<Int>()
        for (haptic in haptics) {
            val stillAttached = deviceIds.contains(haptic.device_id)
            if (haptic.device_id == deviceIdVibratorService && hasVibratorService) {
                continue
            } else if (!stillAttached) {
                removedDevices.add(haptic.device_id)
            }
        }

        for (deviceId in removedDevices) {
            SDLControllerManager.nativeRemoveHaptic(deviceId)
            val iterator = haptics.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().device_id == deviceId) {
                    iterator.remove()
                    break
                }
            }
        }
    }

    protected fun getHaptic(device_id: Int): SDLHaptic? =
        haptics.firstOrNull { it.device_id == device_id }
}

class SDLGenericMotionListener_API12 : View.OnGenericMotionListener {
    override fun onGenericMotion(view: View, event: MotionEvent): Boolean {
        when (event.source) {
            InputDevice.SOURCE_JOYSTICK,
            InputDevice.SOURCE_GAMEPAD,
            InputDevice.SOURCE_DPAD,
            -> return SDLControllerManager.handleJoystickMotionEvent(event)

            InputDevice.SOURCE_MOUSE -> {
                if (!SDLActivity.mSeparateMouseAndTouch) {
                    return false
                }
                when (val action = event.actionMasked) {
                    MotionEvent.ACTION_SCROLL -> {
                        val x = event.getAxisValue(MotionEvent.AXIS_HSCROLL, 0)
                        val y = event.getAxisValue(MotionEvent.AXIS_VSCROLL, 0)
                        SDLActivity.onNativeMouse(0, action, x, y)
                        return true
                    }
                    MotionEvent.ACTION_HOVER_MOVE -> {
                        val x = event.getX(0)
                        val y = event.getY(0)
                        SDLActivity.onNativeMouse(0, action, x, y)
                        return true
                    }
                }
            }
        }
        return false
    }
}
