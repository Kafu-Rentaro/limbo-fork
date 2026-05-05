package org.libsdl.app

import android.content.Context

/**
 * SDL library initialization.
 */
class SDL private constructor() {
    companion object {
        @JvmField
        var mContext: Context? = null

        @JvmStatic
        fun setupJNI() {
            SDLActivity.nativeSetupJNI()
            SDLAudioManager.nativeSetupJNI()
            SDLControllerManager.nativeSetupJNI()
        }

        @JvmStatic
        fun initialize() {
            setContext(null)

            SDLActivity.initialize()
            SDLAudioManager.initialize()
            SDLControllerManager.initialize()
        }

        @JvmStatic
        fun setContext(context: Context?) {
            mContext = context
        }

        @JvmStatic
        fun getContext(): Context? = mContext
    }
}
