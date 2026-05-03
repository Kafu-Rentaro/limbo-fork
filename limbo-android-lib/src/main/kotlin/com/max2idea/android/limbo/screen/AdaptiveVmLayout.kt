package com.max2idea.android.limbo.screen

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * Keeps the VM display and touch-control area usable as the window changes size.
 *
 * This stays independent from Jetpack WindowManager so the legacy Activity can feed posture data
 * into one narrow boundary without coupling the SDL integration to foldable APIs.
 */
object AdaptiveVmLayout {
    const val POSTURE_FLAT = 0
    const val POSTURE_TABLETOP = 1
    const val POSTURE_BOOK = 2

    private const val PORTRAIT_DISPLAY_WEIGHT = 60f
    private const val PORTRAIT_CONTROL_WEIGHT = 40f
    private const val WIDE_DISPLAY_WEIGHT = 75f
    private const val WIDE_CONTROL_WEIGHT = 25f
    private const val FOLDED_CONTROL_WEIGHT = 50f
    private const val COMPACT_DISPLAY_WEIGHT = 100f

    @JvmStatic
    fun apply(
        displayContainer: View?,
        controlGap: View?,
        orientation: Int,
        widthPx: Int,
        heightPx: Int
    ): Float {
        return apply(displayContainer, controlGap, orientation, widthPx, heightPx, POSTURE_FLAT)
    }

    @JvmStatic
    fun apply(
        displayContainer: View?,
        controlGap: View?,
        orientation: Int,
        widthPx: Int,
        heightPx: Int,
        posture: Int
    ): Float {
        return apply(displayContainer, controlGap, orientation, widthPx, heightPx, posture, -1f)
    }

    @JvmStatic
    fun apply(
        displayContainer: View?,
        controlGap: View?,
        orientation: Int,
        widthPx: Int,
        heightPx: Int,
        posture: Int,
        hingeAngleDegrees: Float
    ): Float {
        if (displayContainer == null || controlGap == null) {
            return 0f
        }

        val shortestSide = minOf(widthPx, heightPx)
        val longestSide = maxOf(widthPx, heightPx)
        val isLargeOrFoldableLike = shortestSide >= 900 || longestSide >= shortestSide * 2

        val flatControlWeight = when {
            orientation == Configuration.ORIENTATION_PORTRAIT -> PORTRAIT_CONTROL_WEIGHT
            isLargeOrFoldableLike -> WIDE_CONTROL_WEIGHT
            else -> 0f
        }
        val foldedTargetControlWeight = when (orientation) {
            Configuration.ORIENTATION_PORTRAIT -> PORTRAIT_CONTROL_WEIGHT
            else -> WIDE_CONTROL_WEIGHT
        }
        val isFoldedPosture = posture == POSTURE_TABLETOP || posture == POSTURE_BOOK
        val controlWeight = when {
            isFoldedPosture && hingeAngleDegrees in 30f..165f -> {
                val openFraction = ((hingeAngleDegrees - 30f) / 135f).coerceIn(0f, 1f)
                FOLDED_CONTROL_WEIGHT + (foldedTargetControlWeight - FOLDED_CONTROL_WEIGHT) * openFraction
            }
            isFoldedPosture -> FOLDED_CONTROL_WEIGHT
            else -> flatControlWeight
        }
        val displayWeight = when {
            isFoldedPosture && controlWeight > 0f -> 100f - controlWeight
            controlWeight == 0f -> COMPACT_DISPLAY_WEIGHT
            orientation == Configuration.ORIENTATION_PORTRAIT -> PORTRAIT_DISPLAY_WEIGHT
            else -> WIDE_DISPLAY_WEIGHT
        }

        val useHorizontalSplit = posture == POSTURE_BOOK && controlWeight > 0f
        configureParentOrientation(displayContainer, useHorizontalSplit)
        controlGap.visibility = if (controlWeight == 0f) View.GONE else View.VISIBLE
        setWeightedSize(displayContainer, displayWeight, useHorizontalSplit)
        setWeightedSize(controlGap, controlWeight, useHorizontalSplit)
        return if (controlWeight == 0f) 0f else controlWeight / (displayWeight + controlWeight)
    }

    private fun configureParentOrientation(view: View, horizontal: Boolean) {
        val parent = view.parent
        if (parent is LinearLayout) {
            parent.orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
    }

    private fun setWeightedSize(view: View, weight: Float, horizontal: Boolean) {
        val params = view.layoutParams
        if (params is LinearLayout.LayoutParams) {
            if (horizontal) {
                params.width = 0
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = 0
            }
            params.weight = weight
            view.layoutParams = params
        }
    }
}
