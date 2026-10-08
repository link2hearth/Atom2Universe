package com.Atom2Universe.app.midi.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.Atom2Universe.app.midi.repository.MidiRepository
import com.Atom2Universe.app.midi.repository.PlaylistRepository
import com.Atom2Universe.app.midi.repository.SettingsRepository

/**
 * Factory pour créer MidiPlayerViewModel avec ses dépendances
 */
class MidiPlayerViewModelFactory(
    private val midiRepository: MidiRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MidiPlayerViewModel::class.java)) {
            return MidiPlayerViewModel(
                midiRepository,
                playlistRepository,
                settingsRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
