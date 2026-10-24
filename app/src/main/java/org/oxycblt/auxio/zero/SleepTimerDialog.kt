/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimerDialog.kt is part of Auxio.
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

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.R

@AndroidEntryPoint
class SleepTimerDialog : DialogFragment() {
    @Inject lateinit var sleepTimer: SleepTimer

    private val handler = Handler(Looper.getMainLooper())
    private val ticker =
        object : Runnable {
            override fun run() {
                updateTitle()
                handler.postDelayed(this, 1000)
            }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val dial =
            SleepDialView(context).apply {
                minutes = savedInstanceState?.getInt(KEY_MINUTES) ?: sleepTimer.lastMinutes
            }
        val pad = (24 * resources.displayMetrics.density).toInt()
        val container =
            FrameLayout(context).apply {
                setPadding(pad, pad, pad, 0)
                addView(dial)
            }
        val builder =
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.zero_sleep_timer)
                .setView(container)
                .setPositiveButton(R.string.zero_start) { _, _ -> sleepTimer.start(dial.minutes) }
                .setNegativeButton(R.string.lbl_cancel, null)
        if (sleepTimer.endsAt.value != null) {
            builder.setNeutralButton(R.string.zero_stop) { _, _ -> sleepTimer.cancel() }
        }
        this.dial = dial
        return builder.create()
    }

    private var dial: SleepDialView? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        dial?.let { outState.putInt(KEY_MINUTES, it.minutes) }
    }

    override fun onStart() {
        super.onStart()
        handler.post(ticker)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ticker)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dial = null
    }

    private fun updateTitle() {
        val dialog = dialog as? AlertDialog ?: return
        val base = getString(R.string.zero_sleep_timer)
        val left = sleepTimer.remainingMs()
        if (left == null) {
            dialog.setTitle(base)
            return
        }
        val s = (left + 999) / 1000
        dialog.setTitle("$base · %d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60))
    }

    companion object {
        private const val TAG = "zero_sleep_timer"
        private const val KEY_MINUTES = "minutes"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                SleepTimerDialog().show(fragmentManager, TAG)
            }
        }
    }
}
