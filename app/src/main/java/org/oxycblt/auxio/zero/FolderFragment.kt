/*
 * Copyright (c) 2026 Auxio Project
 * FolderFragment.kt is part of Auxio.
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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.ItemSongBinding
import org.oxycblt.auxio.databinding.ZeroFragmentFoldersBinding
import org.oxycblt.auxio.databinding.ZeroItemFolderBinding
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.auxio.playback.state.PlaybackCommand
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song

/** Browses the library by folder. Playing a folder only queues its own files, not subfolders. */
@AndroidEntryPoint
class FolderFragment :
    ViewBindingFragment<ZeroFragmentFoldersBinding>(),
    Toolbar.OnMenuItemClickListener,
    MusicRepository.UpdateListener,
    PlaybackStateManager.Listener {
    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var playbackManager: PlaybackStateManager

    private var top: FolderNode? = null
    private var current: FolderNode? = null
    private val adapter = FolderAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateBinding(inflater: LayoutInflater) =
        ZeroFragmentFoldersBinding.inflate(inflater)

    override fun onBindingCreated(binding: ZeroFragmentFoldersBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        binding.zeroFoldersToolbar.apply {
            inflateMenu(R.menu.zero_folders)
            setOnMenuItemClickListener(this@FolderFragment)
            setNavigationOnClickListener { findNavController().navigateUp() }
        }
        binding.zeroFoldersRecycler.adapter = adapter
        musicRepository.addUpdateListener(this)
        playbackManager.addListener(this)
        rebuild()
    }

    override fun onDestroyBinding(binding: ZeroFragmentFoldersBinding) {
        super.onDestroyBinding(binding)
        musicRepository.removeUpdateListener(this)
        playbackManager.removeListener(this)
        binding.zeroFoldersToolbar.setOnMenuItemClickListener(null)
        binding.zeroFoldersRecycler.adapter = null
    }

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        if (!changes.deviceLibrary) return
        view?.post {
            if (view != null) rebuild()
        }
    }

    override fun onIndexMoved(index: Int) = refreshPlaying()

    override fun onNewPlayback(
        parent: MusicParent?,
        queue: List<Song>,
        index: Int,
        isShuffled: Boolean,
    ) = refreshPlaying()

    private fun refreshPlaying() {
        view?.post { adapter.notifyItemRangeChanged(0, adapter.itemCount) }
    }

    private fun rebuild() {
        val context = requireContext()
        val songs = musicRepository.library?.songs ?: emptyList()
        val newRoot = FolderNode.build(context, songs)
        var newTop = newRoot
        while (newTop.songs.isEmpty() && newTop.children.size == 1) {
            newTop = newTop.children.values.first()
        }
        top = newTop
        current = arguments?.getStringArrayList(KEY_PATH)?.let(newRoot::find) ?: newTop
        show()
    }

    private fun show() {
        val node = current ?: return
        val context = requireContext()
        requireBinding().zeroFoldersToolbar.title =
            if (node === top) getString(R.string.zero_folders) else node.name
        adapter.submit(node, FolderSort.load(context).sorted(context, node.songs))
    }

    private fun open(node: FolderNode) {
        findNavController()
            .navigate(R.id.zero_folder_fragment, bundleOf(KEY_PATH to ArrayList(node.path())))
    }

    private fun play(node: FolderNode, start: Song?, shuffled: Boolean) {
        val context = requireContext()
        val queue = FolderSort.load(context).sorted(context, node.songs)
        if (queue.isEmpty()) return
        playbackManager.play(FolderCommand(start, queue, shuffled))
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val node = current ?: return false
        when (item.itemId) {
            R.id.zero_action_play -> play(node, null, false)
            R.id.zero_action_shuffle -> play(node, null, true)
            R.id.zero_action_sort -> showSortMenu()
            else -> return false
        }
        return true
    }

    private fun showSortMenu() {
        val context = requireContext()
        val toolbar = requireBinding().zeroFoldersToolbar
        val anchor = toolbar.getMenuButton(R.id.zero_action_sort) ?: toolbar
        val sort = FolderSort.load(context)
        val popup = PopupMenu(context, anchor)
        FolderSort.Mode.entries.forEachIndexed { i, mode ->
            popup.menu.add(GROUP_MODES, i, i, mode.label)
        }
        popup.menu.setGroupCheckable(GROUP_MODES, true, true)
        popup.menu.findItem(sort.mode.ordinal)?.isChecked = true
        popup.menu
            .add(Menu.NONE, ID_DESCENDING, FolderSort.Mode.entries.size, R.string.lbl_sort_dsc)
            .apply {
                isCheckable = true
                isChecked = sort.descending
            }
        popup.setOnMenuItemClickListener { item ->
            val newSort =
                if (item.itemId == ID_DESCENDING) sort.copy(descending = !sort.descending)
                else sort.copy(mode = FolderSort.Mode.entries[item.itemId])
            newSort.save(context)
            show()
            true
        }
        popup.show()
    }

    private data class FolderCommand(
        override val song: Song?,
        override val queue: List<Song>,
        override val shuffled: Boolean,
    ) : PlaybackCommand {
        override val parent: MusicParent? = null
    }

    private sealed interface Row {
        data class Folder(val node: FolderNode) : Row

        data class File(val song: Song) : Row
    }

    private inner class FolderAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var node: FolderNode? = null
        private var rows: List<Row> = emptyList()

        fun submit(node: FolderNode, songs: List<Song>) {
            this.node = node
            val folders =
                node.children.values.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
            rows = folders.map { Row.Folder(it) } + songs.map { Row.File(it) }
            @Suppress("NotifyDataSetChanged") notifyDataSetChanged()
        }

        override fun getItemCount() = rows.size

        override fun getItemViewType(position: Int) =
            if (rows[position] is Row.Folder) TYPE_FOLDER else TYPE_SONG

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_FOLDER) {
                FolderHolder(ZeroItemFolderBinding.inflate(inflater, parent, false))
            } else {
                SongHolder(ItemSongBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Folder -> {
                    val b = (holder as FolderHolder).binding
                    b.zeroFolderName.text = row.node.name
                    b.root.setOnClickListener { open(row.node) }
                    b.zeroFolderPlay.setOnClickListener { play(row.node, null, false) }
                    b.zeroFolderPlay.isEnabled = row.node.songs.isNotEmpty()
                    b.zeroFolderPlay.alpha = if (row.node.songs.isNotEmpty()) 1f else 0f
                }
                is Row.File -> {
                    val b = (holder as SongHolder).binding
                    val context = b.root.context
                    val song = row.song
                    b.songAlbumCover.bind(song)
                    b.songName.text = song.name.resolve(context)
                    b.songInfo.text = song.artists.resolveNames(context)
                    b.songMenu.visibility = android.view.View.GONE
                    val active = playbackManager.currentSong == song
                    b.root.isSelected = active
                    b.songAlbumCover.setPlaying(active && playbackManager.progression.isPlaying)
                    b.root.setOnClickListener { node?.let { play(it, song, false) } }
                }
            }
        }
    }

    private class FolderHolder(val binding: ZeroItemFolderBinding) :
        RecyclerView.ViewHolder(binding.root)

    private class SongHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val KEY_PATH = "path"
        const val TYPE_FOLDER = 0
        const val TYPE_SONG = 1
        const val GROUP_MODES = 1
        const val ID_DESCENDING = 1000
    }
}

/** A directory in the folder tree, holding only the songs directly inside it. */
class FolderNode(val name: String, val parent: FolderNode?) {
    val children = HashMap<String, FolderNode>()
    val songs = ArrayList<Song>()

    fun path(): List<String> {
        val out = ArrayList<String>()
        var n: FolderNode? = this
        while (n?.parent != null) {
            out.add(n.name)
            n = n.parent
        }
        return out.asReversed()
    }

    fun find(path: List<String>): FolderNode? {
        var n = this
        for (p in path) n = n.children[p] ?: return null
        return n
    }

    companion object {
        fun build(context: Context, songs: Collection<Song>): FolderNode {
            val root = FolderNode("", null)
            val volumeNames = HashMap<Any, String>()
            for (song in songs) {
                val dir = song.path.directory
                val volume =
                    volumeNames.getOrPut(dir.volume) { dir.volume.resolveName(context) }
                var node = root.children.getOrPut(volume) { FolderNode(volume, root) }
                for (c in dir.components.components) {
                    val parent = node
                    node = parent.children.getOrPut(c) { FolderNode(c, parent) }
                }
                node.songs.add(song)
            }
            return root
        }
    }
}
