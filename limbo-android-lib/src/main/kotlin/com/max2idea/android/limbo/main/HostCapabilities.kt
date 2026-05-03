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
        val snapshot = readSnapshot()

        Log.d(TAG, "Supported ABIs: ${snapshot.supportedAbis}")
        Log.d(TAG, "Supported 64-bit ABIs: ${snapshot.supported64BitAbis}")
        Log.d(TAG, "ARMv8 baseline available: ${snapshot.arm64Baseline}")
        Log.d(TAG, "ARMv9 optimization hints available: ${snapshot.armv9Candidate}")
        if (snapshot.cpuFeatures.isNotEmpty()) {
            Log.d(TAG, "CPU feature hints: ${snapshot.cpuFeatures.joinToString(",")}")
        }
    }

    @JvmStatic
    fun getSummary(): String {
        val snapshot = readSnapshot()
        return buildString {
            appendLine("Host ABIs: ${snapshot.supportedAbis.ifBlank { "unknown" }}")
            appendLine("Host 64-bit ABIs: ${snapshot.supported64BitAbis.ifBlank { "none" }}")
            appendLine("ARMv8 baseline: ${snapshot.arm64Baseline.toYesNo()}")
            append("ARMv9 optimization hints: ${snapshot.armv9Candidate.toYesNo()}")
        }
    }

    private fun readSnapshot(): HostCapabilitySnapshot {
        val cpuFeatures = readCpuFeatures()
        return HostCapabilitySnapshot(
            supportedAbis = Build.SUPPORTED_ABIS?.joinToString(",").orEmpty(),
            supported64BitAbis = Build.SUPPORTED_64_BIT_ABIS?.joinToString(",").orEmpty(),
            cpuFeatures = cpuFeatures,
            arm64Baseline = Build.SUPPORTED_64_BIT_ABIS?.contains("arm64-v8a") == true,
            armv9Candidate = cpuFeatures.any { it in armv9FeatureHints },
        )
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

    private fun Boolean.toYesNo(): String {
        return if (this) "yes" else "no"
    }

    private data class HostCapabilitySnapshot(
        val supportedAbis: String,
        val supported64BitAbis: String,
        val cpuFeatures: Set<String>,
        val arm64Baseline: Boolean,
        val armv9Candidate: Boolean,
    )
}
