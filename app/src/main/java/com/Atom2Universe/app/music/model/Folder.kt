package com.Atom2Universe.app.music.model

/**
 * Represents a folder in the file system hierarchy containing audio files.
 * Used for folder-based navigation in the music player.
 */
data class Folder(
    val path: String,           // Absolute path of the folder
    val name: String,           // Folder name (last segment of path)
    val subfolders: MutableList<Folder> = mutableListOf(),
    val tracks: MutableList<MusicTrack> = mutableListOf()
) {
    /** Number of tracks directly in this folder */
    val trackCount: Int get() = tracks.size

    /** Number of immediate subfolders */
    val subfolderCount: Int get() = subfolders.size

    /** Total number of tracks including all subfolders recursively */
    val totalTrackCount: Int get() = tracks.size + subfolders.sumOf { it.totalTrackCount }

    /** Total number of folders (including this one and all subfolders recursively) */
    val totalFolderCount: Int get() = 1 + subfolders.sumOf { it.totalFolderCount }

    /**
     * Collects all tracks from this folder and all subfolders recursively.
     */
    fun getAllTracksRecursive(): List<MusicTrack> {
        val result = mutableListOf<MusicTrack>()
        result.addAll(tracks)
        for (subfolder in subfolders) {
            result.addAll(subfolder.getAllTracksRecursive())
        }
        return result
    }
}
