package com.Atom2Universe.app.science

import android.content.Intent
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.gameoflife.GameOfLifeActivity
import com.Atom2Universe.app.hub.BaseHubActivity
import com.Atom2Universe.app.hub.HubTile
import com.Atom2Universe.app.periodic.PeriodicTableActivity
import com.Atom2Universe.app.science.nuclide.NuclideTableActivity
import com.Atom2Universe.app.science.pendulum.DoublePendulumActivity
import com.Atom2Universe.app.science.solarsystem.SolarSystemActivity

class ScienceHubActivity : BaseHubActivity() {

    override fun getLayoutResId(): Int = R.layout.activity_base_hub

    override fun getPrefsName(): String = "science_hub_prefs"

    override fun getParentHubPrefsName(): String = "audio_hub_prefs"
    override fun getParentTileId(): String = "science"

    override fun getHubTitle(): Int = R.string.hub_science_title

    override fun getHubSubtitle(): Int = R.string.science_hub_subtitle

    override fun getDefaultTiles(): List<HubTile> = listOf(
        HubTile(
            id = "periodic_table",
            titleRes = R.string.hub_periodic_title,
            descriptionRes = R.string.hub_periodic_desc,
            iconRes = R.drawable.ic_science,
            defaultColorRes = R.color.science_tile_periodic,
            activityClass = PeriodicTableActivity::class.java
        ),
        HubTile(
            id = "nuclide_table",
            titleRes = R.string.hub_nuclide_title,
            descriptionRes = R.string.hub_nuclide_desc,
            iconRes = R.drawable.ic_science,
            defaultColorRes = R.color.science_tile_nuclide,
            activityClass = NuclideTableActivity::class.java
        ),
        HubTile(
            id = "game_of_life",
            titleRes = R.string.hub_game_of_life_title,
            descriptionRes = R.string.hub_game_of_life_desc,
            iconRes = R.drawable.ic_games,
            defaultColorRes = R.color.science_tile_game_of_life,
            activityClass = GameOfLifeActivity::class.java
        ),
        HubTile(
            id = "double_pendulum",
            titleRes = R.string.hub_pendulum_title,
            descriptionRes = R.string.hub_pendulum_desc,
            iconRes = R.drawable.ic_science,
            defaultColorRes = R.color.science_tile_pendulum,
            activityClass = DoublePendulumActivity::class.java
        ),
        HubTile(
            id = "solar_system",
            titleRes = R.string.hub_solar_title,
            descriptionRes = R.string.hub_solar_desc,
            iconRes = R.drawable.ic_science,
            defaultColorRes = R.color.science_tile_solar,
            activityClass = SolarSystemActivity::class.java
        )
    )

    override fun onTileClicked(tile: HubTile) {
        when (tile.id) {
            "periodic_table" -> startActivity(
                Intent(this, PeriodicTableActivity::class.java)
                    .putExtra(PeriodicTableActivity.EXTRA_SOURCE, PeriodicTableActivity.SOURCE_SCIENCE)
            )
            else -> tile.activityClass?.let { startActivity(Intent(this, it)) }
        }
    }
}
