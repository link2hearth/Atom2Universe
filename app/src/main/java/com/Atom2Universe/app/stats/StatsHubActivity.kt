package com.Atom2Universe.app.stats

import android.content.Intent
import com.Atom2Universe.app.R
import com.Atom2Universe.app.clickerstats.ClickerStatsActivity
import com.Atom2Universe.app.hub.BaseHubActivity
import com.Atom2Universe.app.hub.HubTile
import com.Atom2Universe.app.stats.ui.ReadingStatsActivity
import com.Atom2Universe.app.stats.ui.StatsActivity

class StatsHubActivity : BaseHubActivity() {

    companion object {
        const val TILE_AUDIO = "audio_music"
        const val TILE_GAMES = "games"
        const val TILE_READING = "reading"
    }

    override fun getLayoutResId(): Int = R.layout.activity_base_hub

    override fun getPrefsName(): String = "stats_hub_prefs"

    override fun getParentHubPrefsName(): String = "audio_hub_prefs"
    override fun getParentTileId(): String = "stats_hub"

    override fun getHubTitle(): Int = R.string.hub_stats_title

    override fun getHubSubtitle(): Int? = null

    override fun getDefaultTiles(): List<HubTile> = listOf(
        HubTile(
            id = TILE_AUDIO,
            titleRes = R.string.audio_hub_stats_title,
            descriptionRes = R.string.audio_hub_stats_desc,
            iconRes = R.drawable.ic_stats,
            defaultColorRes = R.color.audio_hub_tile_stats,
            activityClass = StatsActivity::class.java
        ),
        HubTile(
            id = TILE_GAMES,
            titleRes = R.string.clicker_stats_title,
            descriptionRes = R.string.main_hub_clicker_stats_desc,
            iconRes = android.R.drawable.ic_menu_info_details,
            defaultColorRes = R.color.main_hub_tile_clicker_stats,
            activityClass = ClickerStatsActivity::class.java
        ),
        HubTile(
            id = TILE_READING,
            titleRes = R.string.reading_stats_title,
            descriptionRes = R.string.reading_stats_desc,
            iconRes = R.drawable.ic_hub_books,
            defaultColorRes = R.color.audio_hub_tile_books,
            activityClass = ReadingStatsActivity::class.java
        )
    )

    override fun onTileClicked(tile: HubTile) {
        tile.activityClass?.let {
            startActivity(Intent(this, it))
        }
    }
}
