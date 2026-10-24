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
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.abs
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentPlaybackBarBinding
import org.oxycblt.auxio.databinding.ZeroBarGhostBinding
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.musikr.Song

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ZeroEntryPoint {
    fun playbackManager(): PlaybackStateManager
}

/**
 * Spotify-style swiping on the playback bar: the current track slides away while the next (swipe
 * left) or previous (swipe right) track slides in. Taps and long presses keep working.
 */
@SuppressLint("ClickableViewAccessibility")
fun FragmentPlaybackBarBinding.installZeroBarSwipe() {
    val bar = root
    val context = bar.context
    val playbackManager =
        EntryPointAccessors.fromApplication(context, ZeroEntryPoint::class.java).playbackManager()
    val density = context.resources.displayMetrics.density
    val slop = ViewConfiguration.get(context).scaledTouchSlop
    val minFling = 800 * density

    val ghost = ZeroBarGhostBinding.inflate(LayoutInflater.from(context), bar, false)
    ghost.root.layoutParams =
        ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToStart = R.id.playback_controls_wrapper
            topToTop = R.id.playback_cover
            bottomToBottom = R.id.playback_cover
        }
    // Index 0 so it draws beneath the controls.
    bar.addView(ghost.root, 0)

    val moving = listOf<View>(playbackCover, playbackSong, playbackInfo)
    var downX = 0f
    var downY = 0f
    var swiping = false
    var target: Song? = null
    var targetIndex = -1
    var direction = 0 // -1 = next (finger moving left), 1 = previous
    var velocity: VelocityTracker? = null
    var animating = false

    fun peek(dir: Int): Pair<Int, Song>? {
        val queue = playbackManager.queue
        if (queue.isEmpty()) return null
        var i = playbackManager.index + if (dir < 0) 1 else -1
        if (i !in queue.indices) {
            if (playbackManager.repeatMode != RepeatMode.ALL) return null
            i = if (dir < 0) 0 else queue.lastIndex
        }
        return i to queue[i]
    }

    fun showGhost(song: Song) {
        ghost.zeroGhostCover.bind(song)
        ghost.zeroGhostSong.text = song.name.resolve(context)
        ghost.zeroGhostInfo.text = song.artists.resolveNames(context)
        ghost.root.visibility = View.VISIBLE
    }

    fun layout(dx: Float) {
        val w = bar.width.toFloat()
        val dir = if (dx < 0) -1 else 1
        if (dir != direction) {
            direction = dir
            val p = peek(dir)
            targetIndex = p?.first ?: -1
            target = p?.second
            target?.let(::showGhost) ?: run { ghost.root.visibility = View.INVISIBLE }
        }
        val t = if (target == null) dx * 0.25f else dx
        moving.forEach { it.translationX = t }
        ghost.root.translationX = t - dir * w
    }

    fun reset() {
        moving.forEach { it.translationX = 0f }
        ghost.root.translationX = 0f
        ghost.root.visibility = View.INVISIBLE
        direction = 0
        target = null
    }

    fun settle(complete: Boolean) {
        animating = true
        val w = bar.width.toFloat()
        val song = target
        val index = targetIndex
        val dir = direction
        val end = if (complete && song != null) dir * w else 0f
        moving.forEach { it.animate().translationX(end).setDuration(180).start() }
        ghost.root
            .animate()
            .translationX(end - dir * w)
            .setDuration(180)
            .withEndAction {
                if (complete && song != null) {
                    // Show the new track immediately so nothing flickers back.
                    playbackCover.bind(song)
                    playbackSong.text = song.name.resolve(context)
                    playbackInfo.text = song.artists.resolveNames(context)
                    playbackManager.goto(index)
                }
                reset()
                animating = false
            }
            .start()
    }

    bar.setOnTouchListener { v, e ->
        if (animating) return@setOnTouchListener true
        velocity?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                swiping = false
                direction = 0
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(e) }
                false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (!swiping && abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) {
                    swiping = true
                    (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                    // Cancel the pending click/long-press on the bar itself.
                    val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
                    v.onTouchEvent(cancel)
                    cancel.recycle()
                }
                if (swiping) layout(dx)
                swiping
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val wasSwiping = swiping
                swiping = false
                if (wasSwiping) {
                    (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(false)
                    val dx = e.rawX - downX
                    val vt = velocity?.apply { computeCurrentVelocity(1000) }?.xVelocity ?: 0f
                    val far = abs(dx) > bar.width * 0.35f
                    val flung = abs(vt) > minFling && (vt < 0) == (dx < 0)
                    settle(
                        e.actionMasked == MotionEvent.ACTION_UP && target != null && (far || flung)
                    )
                }
                velocity?.recycle()
                velocity = null
                wasSwiping
            }
            else -> swiping
        }
    }
}
