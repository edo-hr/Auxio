/*
 * Copyright (c) 2026 Auxio Project
 * SwipeSkip.kt is part of Auxio.
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import kotlin.math.abs

/**
 * Lets the user swipe horizontally on this view to skip tracks, Spotify-style. Swiping left goes
 * to the next track, right to the previous one. Taps and long presses keep working.
 *
 * @param moving Views that follow the finger during the swipe.
 */
@SuppressLint("ClickableViewAccessibility")
fun View.installSwipeSkip(moving: List<View>, onNext: () -> Unit, onPrev: () -> Unit) {
    val slop = ViewConfiguration.get(context).scaledTouchSlop
    val threshold = 64 * resources.displayMetrics.density
    var downX = 0f
    var downY = 0f
    var swiping = false

    fun settle(dx: Float) {
        for (v in moving) {
            v.animate().translationX(0f).alpha(1f).setDuration(180).start()
        }
        if (abs(dx) >= threshold) {
            if (dx < 0) onNext() else onPrev()
        }
    }

    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                swiping = false
                false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (!swiping && abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) {
                    swiping = true
                    (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                    // Cancel the pending click/long-press on the view itself.
                    val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
                    v.onTouchEvent(cancel)
                    cancel.recycle()
                }
                if (swiping) {
                    val w = v.width.coerceAtLeast(1)
                    for (m in moving) {
                        m.translationX = dx
                        m.alpha = 1f - (abs(dx) / w).coerceAtMost(0.7f)
                    }
                }
                swiping
            }
            MotionEvent.ACTION_UP -> {
                if (swiping) {
                    swiping = false
                    (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(false)
                    settle(e.rawX - downX)
                    true
                } else {
                    false
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (swiping) {
                    swiping = false
                    settle(0f)
                }
                false
            }
            else -> swiping
        }
    }
}
