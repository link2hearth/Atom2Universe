package com.Atom2Universe.app.reading

import android.content.Intent
import com.Atom2Universe.app.R
import com.Atom2Universe.app.books.BookLibraryActivity
import com.Atom2Universe.app.comics.ComicsLibraryActivity
import com.Atom2Universe.app.hub.BaseHubActivity
import com.Atom2Universe.app.hub.HubTile

class ReadingHubActivity : BaseHubActivity() {

    companion object {
        const val TILE_BOOKS = "books"
        const val TILE_COMICS = "comics"
    }

    override fun getLayoutResId(): Int = R.layout.activity_base_hub

    override fun getPrefsName(): String = "reading_hub_prefs"

    override fun getParentHubPrefsName(): String = "audio_hub_prefs"
    override fun getParentTileId(): String = "reading"

    override fun getHubTitle(): Int = R.string.reading_hub_title

    override fun getHubSubtitle(): Int = R.string.reading_hub_subtitle

    override fun getDefaultTiles(): List<HubTile> = listOf(
        HubTile(
            id = TILE_BOOKS,
            titleRes = R.string.hub_books_title,
            descriptionRes = R.string.hub_books_desc,
            iconRes = R.drawable.ic_hub_books,
            defaultColorRes = R.color.audio_hub_tile_books,
            activityClass = BookLibraryActivity::class.java
        ),
        HubTile(
            id = TILE_COMICS,
            titleRes = R.string.hub_comics_title,
            descriptionRes = R.string.hub_comics_desc,
            iconRes = R.drawable.ic_hub_comics,
            defaultColorRes = R.color.audio_hub_tile_comics,
            activityClass = ComicsLibraryActivity::class.java
        )
    )

    override fun onTileClicked(tile: HubTile) {
        tile.activityClass?.let {
            startActivity(Intent(this, it))
        }
    }
}
