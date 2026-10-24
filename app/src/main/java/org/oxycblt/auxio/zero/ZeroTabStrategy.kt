/*
 * Copyright (c) 2026 Auxio Project
 * ZeroTabStrategy.kt is part of Auxio.
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

import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator.TabConfigurationStrategy
import org.oxycblt.auxio.R

/** Names the extra Folders tab appended after the regular home tabs. */
class ZeroTabStrategy(private val inner: TabConfigurationStrategy, private val musicTabs: Int) :
    TabConfigurationStrategy {
    override fun onConfigureTab(tab: TabLayout.Tab, position: Int) {
        if (position >= musicTabs) tab.setText(R.string.zero_folders)
        else inner.onConfigureTab(tab, position)
    }
}
