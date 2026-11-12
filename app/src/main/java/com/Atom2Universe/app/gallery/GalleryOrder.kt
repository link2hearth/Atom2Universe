package com.Atom2Universe.app.gallery

import android.content.SharedPreferences
import com.Atom2Universe.app.R
import org.json.JSONArray
import java.text.Collator
import java.util.Locale
import kotlin.random.Random

internal enum class GalleryOrder(val label: Int) {
    NEWEST(R.string.gallery_newest), OLDEST(R.string.gallery_oldest),
    AZ(R.string.gallery_az), ZA(R.string.gallery_za),
    SHUFFLE(R.string.gallery_shuffle), MANUAL(R.string.gallery_manual)
}

internal class GalleryOrdering(private val prefs: SharedPreferences, val key: String, private val shelf: Shelf) {
    val mode: GalleryOrder get() = prefs.getString("sort/$key", null)?.let(GalleryOrder::valueOf)
        ?: if (shelf == Shelf.FAVORITES) GalleryOrder.AZ else GalleryOrder.NEWEST

    fun choose(order: GalleryOrder) {
        prefs.edit().apply {
            putString("sort/$key", order.name)
            if (order == GalleryOrder.SHUFFLE) putLong("seed/$key", Random.nextLong())
        }.apply()
    }

    fun saveManual(tiles: List<GalleryTile>) {
        val ids = JSONArray()
        tiles.forEach { if (it is Photo) ids.put(it.id) }
        prefs.edit().putString("sort/$key", GalleryOrder.MANUAL.name).putString("manual/$key", ids.toString()).apply()
    }

    fun arrange(items: List<GalleryTile>): List<GalleryTile> {
        val photos = items.filterIsInstance<Photo>()
        val sorted = when (mode) {
            GalleryOrder.AZ, GalleryOrder.ZA -> {
                val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
                val comparator = compareBy<Photo, String>(collator) { it.name }.thenBy { it.id }
                photos.sortedWith(if (mode == GalleryOrder.AZ) comparator else comparator.reversed())
            }
            GalleryOrder.NEWEST, GalleryOrder.OLDEST -> {
                // The repository already orders recent opens and trash entries by their event date.
                if (shelf == Shelf.RECENT || shelf == Shelf.TRASH) {
                    if (mode == GalleryOrder.NEWEST) photos else photos.asReversed()
                } else {
                    val comparator = compareBy<Photo> { it.modified }.thenBy { it.id }
                    photos.sortedWith(if (mode == GalleryOrder.NEWEST) comparator.reversed() else comparator)
                }
            }
            GalleryOrder.SHUFFLE -> photos.sortedBy { it.id }.shuffled(Random(prefs.getLong("seed/$key", 0)))
            GalleryOrder.MANUAL -> {
                val ids = JSONArray(prefs.getString("manual/$key", "[]"))
                val positions = (0 until ids.length()).associateBy { ids.getString(it) }
                photos.sortedBy { positions[it.id] ?: Int.MAX_VALUE }
            }
        }
        return items.filterNot { it is Photo } + sorted
    }
}
