package com.max2idea.android.limbo.main

import android.os.Build
import android.util.Log
import java.io.File

object HostCapabilities {
    private const val TAG = "HostCapabilities"

    private val armv9FeatureHints = setOf(
        "sve",
        "sve2",
        "sme",
        "sme2",
        "i8mm",
        "bf16",
        "flagm2",
    )

    @JvmStatic
    fun logHostCapabilities() {
        val supportedAbis = Build.SUPPORTED_ABIS?.joinToString(",").orEmpty()
        val supported64BitAbis = Build.SUPPORTED_64_BIT_ABIS?.joinToString(",").orEmpty()
        val cpuFeatures = readCpuFeatures()
        val arm64Baseline = Build.SUPPORTED_64_BIT_ABIS?.contains("arm64-v8a") == true
        val armv9Candidate = cpuFeatures.any { it in armv9FeatureHints }

        Log.d(TAG, "Supported ABIs: $supportedAbis")
        Log.d(TAG, "Supported 64-bit ABIs: $supported64BitAbis")
        Log.d(TAG, "ARMv8 baseline available: $arm64Baseline")
        Log.d(TAG, "ARMv9 optimization hints available: $armv9Candidate")
        if (cpuFeatures.isNotEmpty()) {
            Log.d(TAG, "CPU feature hints: ${cpuFeatures.joinToString(",")}")
        }
    }

    private fun readCpuFeatures(): Set<String> {
        return runCatching {
            File("/proc/cpuinfo")
                .takeIf { it.canRead() }
                ?.readLines()
                .orEmpty()
                .asSequence()
                .mapNotNull { line ->
                    val index = line.indexOf(':')
                    if (index <= 0) {
                        null
                    } else {
                        val key = line.substring(0, index).trim().lowercase()
                        if (key == "features" || key == "flags") {
                            line.substring(index + 1)
                        } else {
                            null
                        }
                    }
                }
                .flatMap { it.trim().splitToSequence(Regex("\\s+")) }
                .filter { it.isNotBlank() }
                .map { it.lowercase() }
                .toSet()
        }.getOrDefault(emptySet())
    }
}
