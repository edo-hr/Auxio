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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.ZeroFragmentFoldersBinding
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.musikr.Song

/** A single folder opened from the Folders tab. Playing it never includes subfolders. */
@AndroidEntryPoint
class FolderFragment :
    ViewBindingFragment<ZeroFragmentFoldersBinding>(),
    Toolbar.OnMenuItemClickListener,
    FolderAdapter.Listener {
    @Inject lateinit var folderRepository: FolderRepository
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val folderAdapter = FolderAdapter(this)
    private var current: FolderNode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateBinding(inflater: LayoutInflater) =
        ZeroFragmentFoldersBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: ZeroFragmentFoldersBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onBindingCreated(binding, savedInstanceState)
        binding.zeroFoldersToolbar.apply {
            inflateMenu(R.menu.zero_folders)
            setOnMenuItemClickListener(this@FolderFragment)
            setNavigationOnClickListener { findNavController().navigateUp() }
        }
        binding.zeroFoldersRecycler.adapter = folderAdapter
        collectImmediately(folderRepository.tree, ::updateTree)
        collectImmediately(playbackModel.song, playbackModel.isPlaying, folderAdapter::setPlaying)
    }

    override fun onDestroyBinding(binding: ZeroFragmentFoldersBinding) {
        super.onDestroyBinding(binding)
        binding.zeroFoldersToolbar.setOnMenuItemClickListener(null)
        binding.zeroFoldersRecycler.adapter = null
    }

    private fun updateTree(tree: FolderTree?) {
        tree ?: return
        val path = arguments?.getStringArrayList(KEY_PATH) ?: return
        val node = tree.root.find(path)
        if (node == null) {
            findNavController().navigateUp()
            return
        }
        current = node
        requireBinding().zeroFoldersToolbar.title = node.name
        val context = requireContext()
        folderAdapter.submit(node, node.sortedSongs(context, FolderSort.load(context)))
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val node = current ?: return false
        when (item.itemId) {
            R.id.zero_action_play -> folderRepository.play(node, null, false)
            R.id.zero_action_shuffle -> folderRepository.play(node, null, true)
            R.id.zero_action_sort -> {
                val toolbar = requireBinding().zeroFoldersToolbar
                FolderSortMenu.show(toolbar.getMenuButton(item.itemId) ?: toolbar) {
                    updateTree(folderRepository.tree.value)
                }
            }
            else -> return false
        }
        return true
    }

    override fun onOpen(node: FolderNode) = open(this, node)

    override fun onPlayFolder(node: FolderNode) = folderRepository.play(node, null, false)

    override fun onPlaySong(node: FolderNode, song: Song) = folderRepository.play(node, song, false)

    companion object {
        private const val KEY_PATH = "path"

        fun open(from: Fragment, node: FolderNode) {
            from
                .findNavController()
                .navigate(R.id.zero_folder_fragment, bundleOf(KEY_PATH to ArrayList(node.path())))
        }
    }
}

/** Popup for choosing how songs inside folders are sorted. */
object FolderSortMenu {
    private const val GROUP_MODES = 1
    private const val ID_DESCENDING = 1000

    fun show(anchor: View, onChanged: () -> Unit) {
        val context = anchor.context
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
            onChanged()
            true
        }
        popup.show()
    }
}
