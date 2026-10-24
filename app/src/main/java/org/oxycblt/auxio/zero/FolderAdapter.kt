/*
 * Copyright (c) 2026 Auxio Project
 * FolderAdapter.kt is part of Auxio.
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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.oxycblt.auxio.databinding.ItemSongBinding
import org.oxycblt.auxio.databinding.ZeroItemFolderBinding
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.musikr.Song

/** Lists a folder's subfolders followed by its own songs. */
class FolderAdapter(private val listener: Listener) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    interface Listener {
        fun onOpen(node: FolderNode)

        fun onPlayFolder(node: FolderNode)

        fun onPlaySong(node: FolderNode, song: Song)
    }

    private var node: FolderNode? = null
    private var folders: List<FolderNode> = emptyList()
    private var songs: List<Song> = emptyList()
    private var playing: Song? = null
    private var isPlaying = false

    fun submit(node: FolderNode, songs: List<Song>) {
        if (this.node === node && this.songs == songs) return
        this.node = node
        this.folders = node.sortedChildren
        this.songs = songs
        @Suppress("NotifyDataSetChanged") notifyDataSetChanged()
    }

    fun setPlaying(song: Song?, isPlaying: Boolean) {
        if (song == playing && isPlaying == this.isPlaying) return
        val old = positionOf(playing)
        playing = song
        this.isPlaying = isPlaying
        val new = positionOf(song)
        if (old >= 0) notifyItemChanged(old, PAYLOAD_PLAYING)
        if (new >= 0 && new != old) notifyItemChanged(new, PAYLOAD_PLAYING)
    }

    /** Short label for the fast-scroll popup. */
    fun popupAt(pos: Int): String? {
        val name =
            if (pos < folders.size) folders[pos].name
            else songs.getOrNull(pos - folders.size)?.path?.name ?: return null
        return name.firstOrNull()?.uppercaseChar()?.toString()
    }

    private fun positionOf(song: Song?): Int {
        if (song == null) return -1
        val i = songs.indexOf(song)
        return if (i < 0) -1 else folders.size + i
    }

    override fun getItemCount() = folders.size + songs.size

    override fun getItemViewType(position: Int) =
        if (position < folders.size) TYPE_FOLDER else TYPE_SONG

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOLDER) {
            FolderHolder(ZeroItemFolderBinding.inflate(inflater, parent, false))
        } else {
            SongHolder(ItemSongBinding.inflate(inflater, parent, false)).also {
                it.binding.songMenu.visibility = View.GONE
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isNotEmpty() && holder is SongHolder) {
            bindPlaying(holder, songs[position - folders.size])
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val node = node ?: return
        when (holder) {
            is FolderHolder -> {
                val folder = folders[position]
                val b = holder.binding
                b.zeroFolderName.text = folder.name
                b.root.setOnClickListener { listener.onOpen(folder) }
                val playable = folder.songs.isNotEmpty()
                b.zeroFolderPlay.isEnabled = playable
                b.zeroFolderPlay.visibility = if (playable) View.VISIBLE else View.INVISIBLE
                b.zeroFolderPlay.setOnClickListener { listener.onPlayFolder(folder) }
            }
            is SongHolder -> {
                val song = songs[position - folders.size]
                val b = holder.binding
                val context = b.root.context
                b.songAlbumCover.bind(song)
                b.songName.text = song.name.resolve(context)
                b.songInfo.text = song.artists.resolveNames(context)
                b.root.setOnClickListener { listener.onPlaySong(node, song) }
                bindPlaying(holder, song)
            }
        }
    }

    private fun bindPlaying(holder: SongHolder, song: Song) {
        val active = song == playing
        holder.binding.root.isSelected = active
        holder.binding.songAlbumCover.setPlaying(active && isPlaying)
    }

    private class FolderHolder(val binding: ZeroItemFolderBinding) :
        RecyclerView.ViewHolder(binding.root)

    private class SongHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val TYPE_FOLDER = 0
        const val TYPE_SONG = 1
        const val PAYLOAD_PLAYING = 1
    }
}
