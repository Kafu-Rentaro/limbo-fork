@file:Suppress("DEPRECATION")

package org.libsdl.app

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.max

class SDLAudioManager private constructor() {
    companion object {
        private const val TAG = "SDLAudio"

        @JvmField
        var mAudioTrack: AudioTrack? = null

        @JvmField
        var mAudioRecord: AudioRecord? = null

        @JvmStatic
        fun initialize() {
            mAudioTrack = null
            mAudioRecord = null
        }

        @JvmStatic
        fun audioOpen(sampleRate: Int, is16Bit: Boolean, isStereo: Boolean, desiredFrames: Int): Int {
            try {
                Thread.sleep(1000)
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
            }

            val channelConfig = if (isStereo) {
                AudioFormat.CHANNEL_CONFIGURATION_STEREO
            } else {
                AudioFormat.CHANNEL_CONFIGURATION_MONO
            }
            val audioFormat = if (is16Bit) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT
            val frameSize = (if (isStereo) 2 else 1) * (if (is16Bit) 2 else 1)
            val frameCount = max(
                desiredFrames,
                (AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) + frameSize - 1) / frameSize,
            )

            Log.v(
                TAG,
                "SDL audio: wanted ${if (isStereo) "stereo" else "mono"} " +
                    "${if (is16Bit) "16-bit" else "8-bit"} ${sampleRate / 1000f}kHz, " +
                    "$desiredFrames frames buffer",
            )

            if (mAudioTrack == null) {
                mAudioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    frameCount * frameSize,
                    AudioTrack.MODE_STREAM,
                )

                if (mAudioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "Failed during initialization of Audio Track")
                    mAudioTrack = null
                    return -1
                }

                mAudioTrack?.play()
            }

            val track = mAudioTrack ?: return -1
            Log.v(
                TAG,
                "SDL audio: got ${if (track.channelCount >= 2) "stereo" else "mono"} " +
                    "${if (track.audioFormat == AudioFormat.ENCODING_PCM_16BIT) "16-bit" else "8-bit"} " +
                    "${track.sampleRate / 1000f}kHz, $frameCount frames buffer",
            )

            return 0
        }

        @JvmStatic
        fun audioWriteShortBuffer(buffer: ShortArray) {
            val track = mAudioTrack
            if (track == null) {
                Log.e(TAG, "Attempted to make audio call with uninitialized audio!")
                return
            }

            var index = 0
            while (index < buffer.size) {
                when (val result = track.write(buffer, index, buffer.size - index)) {
                    in 1..Int.MAX_VALUE -> index += result
                    0 -> sleepBriefly()
                    else -> {
                        Log.w(TAG, "SDL audio: error return from write(short)")
                        return
                    }
                }
            }
        }

        @JvmStatic
        fun audioWriteByteBuffer(buffer: ByteArray) {
            val track = mAudioTrack
            if (track == null) {
                Log.e(TAG, "Attempted to make audio call with uninitialized audio!")
                return
            }

            var index = 0
            while (index < buffer.size) {
                when (val result = track.write(buffer, index, buffer.size - index)) {
                    in 1..Int.MAX_VALUE -> index += result
                    0 -> sleepBriefly()
                    else -> {
                        Log.w(TAG, "SDL audio: error return from write(byte)")
                        return
                    }
                }
            }
        }

        @JvmStatic
        fun captureOpen(sampleRate: Int, is16Bit: Boolean, isStereo: Boolean, desiredFrames: Int): Int {
            val channelConfig = if (isStereo) {
                AudioFormat.CHANNEL_CONFIGURATION_STEREO
            } else {
                AudioFormat.CHANNEL_CONFIGURATION_MONO
            }
            val audioFormat = if (is16Bit) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT
            val frameSize = (if (isStereo) 2 else 1) * (if (is16Bit) 2 else 1)
            val frameCount = max(
                desiredFrames,
                (AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) + frameSize - 1) / frameSize,
            )

            Log.v(
                TAG,
                "SDL capture: wanted ${if (isStereo) "stereo" else "mono"} " +
                    "${if (is16Bit) "16-bit" else "8-bit"} ${sampleRate / 1000f}kHz, " +
                    "$desiredFrames frames buffer",
            )

            if (mAudioRecord == null) {
                mAudioRecord = AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    frameCount * frameSize,
                )

                if (mAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "Failed during initialization of AudioRecord")
                    mAudioRecord?.release()
                    mAudioRecord = null
                    return -1
                }

                mAudioRecord?.startRecording()
            }

            val record = mAudioRecord ?: return -1
            Log.v(
                TAG,
                "SDL capture: got ${if (record.channelCount >= 2) "stereo" else "mono"} " +
                    "${if (record.audioFormat == AudioFormat.ENCODING_PCM_16BIT) "16-bit" else "8-bit"} " +
                    "${record.sampleRate / 1000f}kHz, $frameCount frames buffer",
            )

            return 0
        }

        @JvmStatic
        fun captureReadShortBuffer(buffer: ShortArray, blocking: Boolean): Int =
            mAudioRecord?.read(buffer, 0, buffer.size) ?: -1

        @JvmStatic
        fun captureReadByteBuffer(buffer: ByteArray, blocking: Boolean): Int =
            mAudioRecord?.read(buffer, 0, buffer.size) ?: -1

        @JvmStatic
        fun audioClose() {
            mAudioTrack?.stop()
            mAudioTrack?.release()
            mAudioTrack = null
        }

        @JvmStatic
        fun captureClose() {
            mAudioRecord?.stop()
            mAudioRecord?.release()
            mAudioRecord = null
        }

        @JvmStatic
        external fun nativeSetupJNI(): Int

        private fun sleepBriefly() {
            try {
                Thread.sleep(1)
            } catch (_: InterruptedException) {
            }
        }
    }
}
