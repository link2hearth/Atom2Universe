package com.Atom2Universe.app.music.equalizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Combined DAO for all EQ override operations (track, album, artist).
 * Handles the cascade resolution logic for determining which preset applies.
 */
@Dao
interface EqOverrideDao {

    // ========== Track Overrides ==========

    @Query("SELECT * FROM eq_track_overrides WHERE trackId = :trackId")
    suspend fun getTrackOverride(trackId: Long): EqTrackOverride?

    @Query("SELECT * FROM eq_track_overrides WHERE trackId = :trackId")
    fun getTrackOverrideFlow(trackId: Long): Flow<EqTrackOverride?>

    @Query("SELECT p.* FROM eq_presets p INNER JOIN eq_track_overrides o ON p.id = o.presetId WHERE o.trackId = :trackId")
    suspend fun getPresetForTrack(trackId: Long): EqPreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setTrackOverride(override: EqTrackOverride)

    @Query("DELETE FROM eq_track_overrides WHERE trackId = :trackId")
    suspend fun clearTrackOverride(trackId: Long)

    @Query("SELECT COUNT(*) FROM eq_track_overrides")
    suspend fun getTrackOverrideCount(): Int

    // ========== Album Overrides ==========

    @Query("SELECT * FROM eq_album_overrides WHERE albumKey = :albumKey")
    suspend fun getAlbumOverride(albumKey: String): EqAlbumOverride?

    @Query("SELECT * FROM eq_album_overrides WHERE albumKey = :albumKey")
    fun getAlbumOverrideFlow(albumKey: String): Flow<EqAlbumOverride?>

    @Query("SELECT p.* FROM eq_presets p INNER JOIN eq_album_overrides o ON p.id = o.presetId WHERE o.albumKey = :albumKey")
    suspend fun getPresetForAlbum(albumKey: String): EqPreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setAlbumOverride(override: EqAlbumOverride)

    @Query("DELETE FROM eq_album_overrides WHERE albumKey = :albumKey")
    suspend fun clearAlbumOverride(albumKey: String)

    @Query("SELECT COUNT(*) FROM eq_album_overrides")
    suspend fun getAlbumOverrideCount(): Int

    // ========== Artist Overrides ==========

    @Query("SELECT * FROM eq_artist_overrides WHERE artistKey = :artistKey")
    suspend fun getArtistOverride(artistKey: String): EqArtistOverride?

    @Query("SELECT * FROM eq_artist_overrides WHERE artistKey = :artistKey")
    fun getArtistOverrideFlow(artistKey: String): Flow<EqArtistOverride?>

    @Query("SELECT p.* FROM eq_presets p INNER JOIN eq_artist_overrides o ON p.id = o.presetId WHERE o.artistKey = :artistKey")
    suspend fun getPresetForArtist(artistKey: String): EqPreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setArtistOverride(override: EqArtistOverride)

    @Query("DELETE FROM eq_artist_overrides WHERE artistKey = :artistKey")
    suspend fun clearArtistOverride(artistKey: String)

    @Query("SELECT COUNT(*) FROM eq_artist_overrides")
    suspend fun getArtistOverrideCount(): Int

    // ========== Cascade Resolution ==========

    /**
     * Resolve the effective preset for a track using cascade priority:
     * Track > Album > Artist > null (caller should use global)
     */
    @Transaction
    suspend fun resolvePresetForTrack(
        trackId: Long,
        albumKey: String,
        artistKey: String
    ): ResolvedPreset? {
        // 1. Check track override first (highest priority)
        getPresetForTrack(trackId)?.let {
            return ResolvedPreset(it, OverrideSource.TRACK)
        }

        // 2. Check album override
        getPresetForAlbum(albumKey)?.let {
            return ResolvedPreset(it, OverrideSource.ALBUM)
        }

        // 3. Check artist override
        getPresetForArtist(artistKey)?.let {
            return ResolvedPreset(it, OverrideSource.ARTIST)
        }

        // 4. No override found, caller should use global preset
        return null
    }

    // ========== Bulk Operations ==========

    @Query("DELETE FROM eq_track_overrides")
    suspend fun clearAllTrackOverrides()

    @Query("DELETE FROM eq_album_overrides")
    suspend fun clearAllAlbumOverrides()

    @Query("DELETE FROM eq_artist_overrides")
    suspend fun clearAllArtistOverrides()

    @Transaction
    suspend fun clearAllOverrides() {
        clearAllTrackOverrides()
        clearAllAlbumOverrides()
        clearAllArtistOverrides()
    }

    // ========== Statistics ==========

    @Query("""
        SELECT
            (SELECT COUNT(*) FROM eq_track_overrides) as trackCount,
            (SELECT COUNT(*) FROM eq_album_overrides) as albumCount,
            (SELECT COUNT(*) FROM eq_artist_overrides) as artistCount
    """)
    suspend fun getOverrideStats(): OverrideStats
}

/**
 * Result of cascade preset resolution, includes the preset and its source.
 */
data class ResolvedPreset(
    val preset: EqPreset,
    val source: OverrideSource
)

/**
 * Indicates where the resolved preset came from in the cascade.
 */
enum class OverrideSource {
    TRACK,
    ALBUM,
    ARTIST,
    GLOBAL
}

/**
 * Statistics about override counts.
 */
data class OverrideStats(
    val trackCount: Int,
    val albumCount: Int,
    val artistCount: Int
) {
    val total: Int get() = trackCount + albumCount + artistCount
}
