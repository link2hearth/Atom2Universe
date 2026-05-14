package com.Atom2Universe.app.creative

import android.content.Intent
import com.Atom2Universe.app.R
import com.Atom2Universe.app.hub.BaseHubActivity
import com.Atom2Universe.app.hub.HubTile
import com.Atom2Universe.app.notes.ui.NotesActivity
import com.Atom2Universe.app.pixelart.CanvasEditorActivity
import com.Atom2Universe.app.pixelart.PixelArtEditorActivity

class CreativeHubActivity : BaseHubActivity() {

    companion object {
        const val TILE_PIXEL_ART = "pixel_art"
        const val TILE_CANVAS = "canvas"
        const val TILE_NOTES = "notes"
    }

    override fun getLayoutResId(): Int = R.layout.activity_base_hub

    override fun getPrefsName(): String = "creative_hub_prefs"

    override fun getParentHubPrefsName(): String = "audio_hub_prefs"
    override fun getParentTileId(): String = "creative"

    override fun getHubTitle(): Int = R.string.creative_hub_title

    override fun getHubSubtitle(): Int = R.string.creative_hub_subtitle

    override fun getDefaultTiles(): List<HubTile> = listOf(
        HubTile(
            id = TILE_NOTES,
            titleRes = R.string.hub_notes_title,
            descriptionRes = R.string.hub_notes_desc,
            iconRes = R.drawable.ic_notes,
            defaultColorRes = R.color.creative_hub_tile_notes,
            activityClass = NotesActivity::class.java
        ),
        HubTile(
            id = TILE_PIXEL_ART,
            titleRes = R.string.creative_hub_pixel_art_title,
            descriptionRes = R.string.creative_hub_pixel_art_desc,
            iconRes = android.R.drawable.ic_menu_edit,
            defaultColorRes = R.color.creative_hub_tile_pixel_art,
            activityClass = PixelArtEditorActivity::class.java
        ),
        HubTile(
            id = TILE_CANVAS,
            titleRes = R.string.creative_hub_canvas_title,
            descriptionRes = R.string.creative_hub_canvas_desc,
            iconRes = android.R.drawable.ic_menu_gallery,
            defaultColorRes = R.color.creative_hub_tile_canvas,
            activityClass = CanvasEditorActivity::class.java
        )
    )

    override fun onTileClicked(tile: HubTile) {
        tile.activityClass?.let {
            startActivity(Intent(this, it))
        }
    }
}
