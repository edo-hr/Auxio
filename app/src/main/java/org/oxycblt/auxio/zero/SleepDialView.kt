/*
 * Copyright (c) 2026 Auxio Project
 * SleepDialView.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.zero

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Circular dial selecting [MIN_MINUTES]..[MAX_MINUTES] in [STEP_MINUTES] steps. */
class SleepDialView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val stroke = 10 * density
    private val thumbRadius = 14 * density

    private val trackPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color =
                MaterialColors.getColor(
                    this@SleepDialView,
                    com.google.android.material.R.attr.colorSurfaceContainerHighest,
                )
        }
    private val arcPaint =
        Paint(trackPaint).apply {
            color =
                MaterialColors.getColor(this@SleepDialView, androidx.appcompat.R.attr.colorPrimary)
        }
    private val thumbPaint = Paint(arcPaint).apply { style = Paint.Style.FILL }
    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 44 * density
            isFakeBoldText = true
            color =
                MaterialColors.getColor(
                    this@SleepDialView,
                    com.google.android.material.R.attr.colorOnSurface,
                )
        }
    private val oval = RectF()

    /** Position along the dial, 0..1. */
    private var fraction = 0f
    private var lastAngle = 0f

    var onChange: ((Int) -> Unit)? = null

    var minutes: Int
        get() = MIN_MINUTES + (fraction * STEPS).roundToInt() * STEP_MINUTES
        set(value) {
            val clamped = value.coerceIn(MIN_MINUTES, MAX_MINUTES)
            fraction = ((clamped - MIN_MINUTES) / STEP_MINUTES).toFloat() / STEPS
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val size = min(w, (260 * density).toInt())
        setMeasuredDimension(w, size)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - thumbRadius - 4 * density
        oval.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawOval(oval, trackPaint)
        val snapped = (fraction * STEPS).roundToInt().toFloat() / STEPS
        val sweep = snapped * 360f
        canvas.drawArc(oval, -90f, sweep, false, arcPaint)
        val rad = Math.toRadians((sweep - 90f).toDouble())
        canvas.drawCircle(
            cx + r * cos(rad).toFloat(),
            cy + r * sin(rad).toFloat(),
            thumbRadius,
            thumbPaint,
        )
        val m = minutes
        val y = cy - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText("%d:%02d".format(m / 60, m % 60), cx, y, textPaint)
    }

    private fun angleOf(e: MotionEvent): Float {
        // 0 at the top, increasing clockwise, in 0..1.
        val a = Math.toDegrees(atan2((e.x - width / 2f).toDouble(), (height / 2f - e.y).toDouble()))
        return (((a + 360) % 360) / 360).toFloat()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastAngle = angleOf(event)
                // Jump to the touched point unless that would wrap across the top.
                if (kotlin.math.abs(lastAngle - fraction) < 0.5f) {
                    update(lastAngle)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val a = angleOf(event)
                var d = a - lastAngle
                if (d > 0.5f) d -= 1f
                if (d < -0.5f) d += 1f
                lastAngle = a
                update((fraction + d).coerceIn(0f, 1f))
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun update(newFraction: Float) {
        val before = minutes
        fraction = newFraction
        val after = minutes
        if (after != before) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onChange?.invoke(after)
        }
        invalidate()
    }

    companion object {
        const val MIN_MINUTES = 10
        const val MAX_MINUTES = 240
        const val STEP_MINUTES = 5
        private const val STEPS = (MAX_MINUTES - MIN_MINUTES) / STEP_MINUTES
    }
}
