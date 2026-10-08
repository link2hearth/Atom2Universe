package com.Atom2Universe.app.music

import android.content.Context
import android.widget.ImageView
import com.Atom2Universe.app.music.model.Album
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.CoroutineScope

/** Album tiles and players share the same source selection and thumbnail cache. */
object AlbumImageCache {
    fun init(context: Context) = MusicArtworkLoader.init(context)

    fun loadAlbumArt(imageView: ImageView, album: Album, defaultIconResId: Int, scope: CoroutineScope) =
        MusicArtworkLoader.loadAlbum(imageView, album, defaultIconResId, scope)

    fun loadTrackArt(
        imageView: ImageView,
        track: MusicTrack,
        defaultIconResId: Int,
        scope: CoroutineScope,
        size: Int = 256
    ) = MusicArtworkLoader.loadTrack(imageView, track, defaultIconResId, scope, size)

    fun cancel(imageView: ImageView) = MusicArtworkLoader.cancel(imageView)
    fun clearAll() = MusicArtworkLoader.clearAll()
    fun invalidateAlbum(album: Album) = MusicArtworkLoader.invalidateAlbum(album)
    fun invalidateAlbums(albums: List<Album>) = albums.forEach(::invalidateAlbum)
}
