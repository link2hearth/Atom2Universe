package com.Atom2Universe.app.music

import android.content.Context
import android.widget.ImageView
import com.Atom2Universe.app.music.model.Album
import kotlinx.coroutines.CoroutineScope

/** Custom artist icons take priority; album fallbacks use exactly the player's artwork resolver. */
object ArtistImageCache {
    fun init(context: Context) = MusicArtworkLoader.init(context)

    fun loadArtistImage(
        imageView: ImageView,
        customIconPath: String?,
        albums: List<Album>,
        defaultIconResId: Int,
        scope: CoroutineScope
    ) = MusicArtworkLoader.loadArtist(imageView, customIconPath, albums, defaultIconResId, scope)

    // Source keys include the icon path and its file timestamp, so changes survive process restarts.
    @Suppress("UNUSED_PARAMETER")
    fun invalidateArtist(artistName: String) = MusicArtworkLoader.clearMemory()
    fun clearAll() = MusicArtworkLoader.clearAll()
}
