/*
 * Copyright (c) 2026 Auxio Project
 * FolderTree.kt is part of Auxio.
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.playback.state.PlaybackCommand
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song

/** A directory in the folder tree, holding only the songs directly inside it. */
class FolderNode(val name: String, val parent: FolderNode?) {
    val children = HashMap<String, FolderNode>()
    val songs = ArrayList<Song>()

    val sortedChildren: List<FolderNode> by lazy {
        children.values.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
    }

    @Volatile private var sortCache: Pair<FolderSort, List<Song>>? = null

    fun sortedSongs(context: Context, sort: FolderSort): List<Song> {
        sortCache?.let { if (it.first == sort) return it.second }
        return sort.sorted(context, songs).also { sortCache = sort to it }
    }

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
}

/** The folder hierarchy of the library. [top] skips leading single-child folders. */
class FolderTree(val root: FolderNode, val top: FolderNode) {
    val isEmpty: Boolean
        get() = root.children.isEmpty()

    companion object {
        fun build(context: Context, songs: Collection<Song>): FolderTree {
            val root = FolderNode("", null)
            val volumeNames = HashMap<Any, String>()
            for (song in songs) {
                val dir = song.path.directory
                val volume = volumeNames.getOrPut(dir.volume) { dir.volume.resolveName(context) }
                var node = root.children.getOrPut(volume) { FolderNode(volume, root) }
                for (c in dir.components.components) {
                    val parent = node
                    node = parent.children.getOrPut(c) { FolderNode(c, parent) }
                }
                node.songs.add(song)
            }
            var top = root
            while (top.songs.isEmpty() && top.children.size == 1) {
                top = top.children.values.first()
            }
            // Warm up the child ordering off the main thread.
            fun warm(n: FolderNode) {
                n.sortedChildren.forEach(::warm)
            }
            warm(root)
            return FolderTree(root, top)
        }
    }
}

/** Builds the folder tree in the background whenever the library changes. */
@Singleton
class FolderRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackStateManager,
) : MusicRepository.UpdateListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val _tree = MutableStateFlow<FolderTree?>(null)
    val tree: StateFlow<FolderTree?> = _tree

    init {
        musicRepository.addUpdateListener(this)
        rebuild()
    }

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        if (changes.deviceLibrary) rebuild()
    }

    private fun rebuild() {
        val songs = musicRepository.library?.songs ?: return
        job?.cancel()
        job = scope.launch { _tree.value = FolderTree.build(context, songs) }
    }

    /** Plays [node]'s own songs (never subfolders), optionally starting from [start]. */
    fun play(node: FolderNode, start: Song?, shuffled: Boolean) {
        val queue = node.sortedSongs(context, FolderSort.load(context))
        if (queue.isEmpty()) return
        playbackManager.play(FolderCommand(start, queue, shuffled))
    }

    private data class FolderCommand(
        override val song: Song?,
        override val queue: List<Song>,
        override val shuffled: Boolean,
    ) : PlaybackCommand {
        override val parent: MusicParent? = null
    }
}
