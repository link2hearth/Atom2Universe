package com.Atom2Universe.app.music.equalizer

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.equalizer.data.EqAlbumOverride
import com.Atom2Universe.app.music.equalizer.data.EqArtistOverride
import com.Atom2Universe.app.music.equalizer.data.EqPreset
import com.Atom2Universe.app.music.equalizer.data.OverrideSource
import com.Atom2Universe.app.music.equalizer.data.ResolvedPreset
import com.Atom2Universe.app.music.model.MusicTrack

/**
 * Resolves which EQ preset should be applied for a given track.
 * Uses cascade priority: Track > Album > Artist > Global
 */
class EqPresetResolver(private val context: Context) {

    private val database by lazy { MusicDatabase.getInstance(context) }
    private val presetDao by lazy { database.eqPresetDao() }
    private val overrideDao by lazy { database.eqOverrideDao() }

    companion object {
        private const val TAG = "EqPresetResolver"
    }

    /**
     * Resolve the effective preset for a track using cascade priority.
     * @param track The track to resolve preset for
     * @param globalPresetId The ID of the global/default preset (fallback)
     * @return ResolvedPreset containing the preset and its source
     */
    suspend fun resolvePresetForTrack(track: MusicTrack, globalPresetId: Long): ResolvedPreset {
        val albumKey = EqAlbumOverride.createKey(track.album, track.albumArtist)
        val artistKey = EqArtistOverride.createKey(track.artist, track.albumArtist)

        Log.d(TAG, "resolvePresetForTrack: trackId=${track.id}, albumKey='$albumKey', artistKey='$artistKey'")

        // Try cascade resolution (Track > Album > Artist)
        val cascadeResult = overrideDao.resolvePresetForTrack(
            trackId = track.id,
            albumKey = albumKey,
            artistKey = artistKey
        )

        if (cascadeResult != null) {
            Log.d(TAG, "resolvePresetForTrack: Found override! preset='${cascadeResult.preset.name}', source=${cascadeResult.source}")
            return cascadeResult
        }

        Log.d(TAG, "resolvePresetForTrack: No override found, using global preset id=$globalPresetId")

        // Fall back to global preset
        val globalPreset = presetDao.getPresetById(globalPresetId)
            ?: presetDao.getPresetByName("Flat")
            ?: EqPreset.flat("Flat", true)

        Log.d(TAG, "resolvePresetForTrack: Using global preset '${globalPreset.name}'")
        return ResolvedPreset(globalPreset, OverrideSource.GLOBAL)
    }

    /**
     * Check if a specific track has an override.
     */
    suspend fun hasTrackOverride(trackId: Long): Boolean {
        return overrideDao.getTrackOverride(trackId) != null
    }

    /**
     * Check if a specific album has an override.
     */
    suspend fun hasAlbumOverride(album: String, albumArtist: String?): Boolean {
        val albumKey = EqAlbumOverride.createKey(album, albumArtist)
        return overrideDao.getAlbumOverride(albumKey) != null
    }

    /**
     * Check if a specific artist has an override.
     */
    suspend fun hasArtistOverride(artist: String, albumArtist: String?): Boolean {
        val artistKey = EqArtistOverride.createKey(artist, albumArtist)
        return overrideDao.getArtistOverride(artistKey) != null
    }

    /**
     * Get detailed override info for a track (for UI display).
     */
    suspend fun getOverrideInfo(track: MusicTrack): OverrideInfo {
        val albumKey = EqAlbumOverride.createKey(track.album, track.albumArtist)
        val artistKey = EqArtistOverride.createKey(track.artist, track.albumArtist)

        return OverrideInfo(
            trackOverride = overrideDao.getPresetForTrack(track.id),
            albumOverride = overrideDao.getPresetForAlbum(albumKey),
            artistOverride = overrideDao.getPresetForArtist(artistKey)
        )
    }

    /**
     * Get the first system preset (Flat) as default.
     */
    suspend fun getDefaultPreset(): EqPreset {
        return presetDao.getPresetByName("Flat")
            ?: presetDao.getSystemPresets().firstOrNull()
            ?: EqPreset.flat("Flat", true)
    }
}

/**
 * Contains all override information for a track.
 */
data class OverrideInfo(
    val trackOverride: EqPreset?,
    val albumOverride: EqPreset?,
    val artistOverride: EqPreset?
) {
    /** The effective override (first non-null in cascade order) */
    val effectiveOverride: EqPreset?
        get() = trackOverride ?: albumOverride ?: artistOverride

    /** The source of the effective override */
    val effectiveSource: OverrideSource?
        get() = when {
            trackOverride != null -> OverrideSource.TRACK
            albumOverride != null -> OverrideSource.ALBUM
            artistOverride != null -> OverrideSource.ARTIST
            else -> null
        }

    /** True if any override exists */
    val hasOverride: Boolean
        get() = effectiveOverride != null
}
