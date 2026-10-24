/*
 * Copyright (c) 2026 Auxio Project
 * FolderSort.kt is part of Auxio.
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

import android.content.Context
import androidx.annotation.StringRes
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.musikr.Song

/** Sort order for songs inside a folder. Defaults to file name. */
data class FolderSort(val mode: Mode, val descending: Boolean) {
    enum class Mode(@StringRes val label: Int) {
        FILE_NAME(R.string.zero_file_name),
        TITLE(R.string.lbl_name),
        ARTIST(R.string.lbl_artist),
        ALBUM(R.string.lbl_album),
        TRACK(R.string.lbl_track),
        DURATION(R.string.lbl_duration),
    }

    fun sorted(context: Context, songs: List<Song>): List<Song> {
        val byFile = Comparator<Song> { a, b -> NaturalOrder.compare(fileName(a), fileName(b)) }
        val primary: Comparator<Song> =
            when (mode) {
                Mode.FILE_NAME -> byFile
                Mode.TITLE -> compareBy(NaturalOrder) { s: Song -> s.name.resolve(context) }
                Mode.ARTIST ->
                    compareBy(NaturalOrder) { s: Song -> s.artists.resolveNames(context) }
                Mode.ALBUM -> compareBy(NaturalOrder) { s: Song -> s.album.name.resolve(context) }
                Mode.TRACK ->
                    compareBy<Song> { it.disc?.number ?: 0 }.thenBy { it.track ?: Int.MAX_VALUE }
                Mode.DURATION -> compareBy<Song> { it.durationMs }
            }
        val cmp = primary.then(byFile)
        return songs.sortedWith(if (descending) cmp.reversed() else cmp)
    }

    fun save(context: Context) {
        prefs(context)
            .edit()
            .putString(KEY_MODE, mode.name)
            .putBoolean(KEY_DESC, descending)
            .apply()
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_DESC = "descending"

        private fun prefs(context: Context) =
            context.getSharedPreferences("zero_folders", Context.MODE_PRIVATE)

        private fun fileName(song: Song) = song.path.name ?: ""

        fun load(context: Context): FolderSort {
            val p = prefs(context)
            val mode =
                Mode.entries.find { it.name == p.getString(KEY_MODE, null) } ?: Mode.FILE_NAME
            return FolderSort(mode, p.getBoolean(KEY_DESC, false))
        }
    }
}

/** Case-insensitive comparison where digit runs compare numerically ("2" < "10"). */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val si = i
                val sj = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(si, i).trimStart('0')
                val nb = b.substring(sj, j).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
