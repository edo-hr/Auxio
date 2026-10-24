/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimer.kt is part of Auxio.
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

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.oxycblt.auxio.playback.state.PlaybackStateManager

/** App-wide sleep timer. Pauses playback when the chosen time elapses. */
@Singleton
class SleepTimer @Inject constructor(private val playbackManager: PlaybackStateManager) {
    private val handler = Handler(Looper.getMainLooper())
    private val _endsAt = MutableStateFlow<Long?>(null)

    /** [SystemClock.elapsedRealtime] at which playback will pause, or null if inactive. */
    val endsAt: StateFlow<Long?> = _endsAt

    /** Last chosen duration, reused as the dial's initial value. */
    var lastMinutes = 30
        private set

    // Polls elapsedRealtime so that time spent in deep sleep is still counted.
    private val fire: Runnable =
        object : Runnable {
            override fun run() {
                val left = remainingMs() ?: return
                if (left > 0) {
                    handler.postDelayed(this, left.coerceAtMost(10_000L))
                    return
                }
                _endsAt.value = null
                playbackManager.playing(false)
            }
        }

    fun start(minutes: Int) {
        handler.removeCallbacks(fire)
        lastMinutes = minutes
        val ms = minutes * 60_000L
        _endsAt.value = SystemClock.elapsedRealtime() + ms
        handler.postDelayed(fire, ms.coerceAtMost(10_000L))
    }

    fun cancel() {
        handler.removeCallbacks(fire)
        _endsAt.value = null
    }

    fun remainingMs(): Long? = _endsAt.value?.let { (it - SystemClock.elapsedRealtime()).coerceAtLeast(0) }
}
