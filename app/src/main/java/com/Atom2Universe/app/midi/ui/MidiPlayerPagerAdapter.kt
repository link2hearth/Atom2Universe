package com.Atom2Universe.app.midi.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Adapter pour le ViewPager2 de MidiPlayerActivity
 * Gère les 3 fragments: Library, Playlists, Now Playing
 */
class MidiPlayerPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> MidiLibraryFragment.newInstance()
            1 -> PlaylistsFragment.newInstance()
            2 -> NowPlayingFragment.newInstance()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
