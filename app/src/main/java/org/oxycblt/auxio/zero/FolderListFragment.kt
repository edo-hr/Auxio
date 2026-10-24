/*
 * Copyright (c) 2026 Auxio Project
 * FolderListFragment.kt is part of Auxio.
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
import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentHomeListBinding
import org.oxycblt.auxio.home.HomeViewModel
import org.oxycblt.auxio.list.recycler.FastScrollRecyclerView
import org.oxycblt.auxio.music.IndexingState
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.musikr.Song

/** The Folders tab on the home screen. */
@AndroidEntryPoint
class FolderListFragment :
    ViewBindingFragment<FragmentHomeListBinding>(),
    FolderAdapter.Listener,
    FastScrollRecyclerView.PopupProvider,
    FastScrollRecyclerView.Listener {
    @Inject lateinit var folderRepository: FolderRepository
    private val homeModel: HomeViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val folderAdapter = FolderAdapter(this)

    override fun onCreateBinding(inflater: LayoutInflater) =
        FragmentHomeListBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentHomeListBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        binding.homeRecycler.apply {
            id = R.id.zero_home_folder_recycler
            adapter = folderAdapter
            popupProvider = this@FolderListFragment
            listener = this@FolderListFragment
        }
        binding.homeNoMusicPlaceholder.apply {
            setImageResource(R.drawable.ic_zero_folder_24)
            contentDescription = getString(R.string.zero_folders)
        }
        binding.homeNoMusicMsg.text = getString(R.string.zero_lng_empty_folders)
        binding.homeNoMusicAction.setOnClickListener { homeModel.startChooseMusicLocations() }

        collectImmediately(folderRepository.tree, ::updateTree)
        collectImmediately(homeModel.empty, musicModel.indexingState, ::updateNoMusicIndicator)
        collectImmediately(playbackModel.song, playbackModel.isPlaying, folderAdapter::setPlaying)
    }

    override fun onDestroyBinding(binding: FragmentHomeListBinding) {
        super.onDestroyBinding(binding)
        binding.homeRecycler.apply {
            adapter = null
            popupProvider = null
            listener = null
        }
    }

    private fun updateTree(tree: FolderTree?) {
        val node = tree?.top ?: return
        folderAdapter.submit(
            node,
            node.sortedSongs(requireContext(), FolderSort.load(requireContext())),
        )
    }

    private fun updateNoMusicIndicator(empty: Boolean, indexingState: IndexingState?) {
        val binding = requireBinding()
        binding.homeRecycler.isInvisible = empty
        binding.homeNoMusic.isInvisible = !empty
        binding.homeNoMusicAction.isVisible =
            indexingState == null || (empty && indexingState is IndexingState.Completed)
    }

    fun showSortMenu(anchor: View) {
        FolderSortMenu.show(anchor) { updateTree(folderRepository.tree.value) }
    }

    override fun onOpen(node: FolderNode) = FolderFragment.open(this, node)

    override fun onPlayFolder(node: FolderNode) = folderRepository.play(node, null, false)

    override fun onPlaySong(node: FolderNode, song: Song) = folderRepository.play(node, song, false)

    override fun getPopupData(pos: Int) =
        folderAdapter.popupAt(pos)?.let { FastScrollRecyclerView.PopupProvider.PopupData(it) }

    override fun onFastScrollingChanged(isFastScrolling: Boolean) {
        homeModel.setFastScrolling(isFastScrolling)
    }
}
