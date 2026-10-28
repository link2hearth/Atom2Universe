package com.Atom2Universe.app.games

import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.farm.FarmState
import com.Atom2Universe.app.hub.BaseHubActivity
import com.Atom2Universe.app.hub.HubTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Le hub des jeux, en deux files côte à côte : les casse-têtes (puzzles, plateau, cartes) et les
 * grands jeux (action, simulation). On passe de l'une à l'autre en balayant, et une tuile tenue
 * jusqu'au bord de l'écran se dépose dans l'autre file.
 */
class GamesActivity : BaseHubActivity() {

    private companion object {
        /** Les files de tuiles ont remplacé la liste unique : l'ancien ordre et l'ancienne migration sont à jeter une fois. */
        const val KEY_PANES_V1 = "panes_v2"
        const val PANE_PUZZLES = 0
        const val PANE_GAMES = 1
    }

    override fun getLayoutResId(): Int = R.layout.activity_games_hub

    override fun getPrefsName(): String = "games_hub_prefs"

    override fun getParentHubPrefsName(): String = "audio_hub_prefs"
    override fun getParentTileId(): String = "games"

    override fun getHubTitle(): Int = R.string.games_title

    override fun getHubSubtitle(): Int? = null

    // La grille garde ses carrés ; la liste place l'illustration à côté du titre et du descriptif.
    override fun usesSquareTiles(): Boolean = true

    // Chaque jeu a son illustration, déclarée une seule fois dans HubTileArtworks.
    override fun supportsTileColors(): Boolean = false
    override fun supportsListMode(): Boolean = true

    override fun getDefaultTiles(): List<HubTile> = GamesCatalog.tiles

    override fun getPaneCount(): Int = 2

    override fun defaultPaneOf(tile: HubTile): Int =
        if (tile.id in GamesCatalog.bigGameIds) PANE_GAMES else PANE_PUZZLES

    override fun getPaneTitle(pane: Int): Int =
        if (pane == PANE_GAMES) R.string.games_pane_games else R.string.games_pane_puzzles

    override fun onBeforeLoadTiles() {
        if (!hubPrefs.getBoolean(KEY_PANES_V1, false)) hubPrefs.edit()
            .putBoolean(KEY_PANES_V1, true)
            .remove("tile_order")
            .remove("tile_order_1")
            .remove("tile_panes")
            .apply()
        GamesHubMigration.separateRicochet(hubPrefs)
    }

    override fun onTileClicked(tile: HubTile) {
        if (tile.activityClass != null) {
            startActivity(Intent(this, tile.activityClass))
        } else {
            Toast.makeText(this, R.string.games_coming_soon, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * La tuile de la ferme porte une pastille avec le nombre de cultures pretes. Elle est recalculee
     * a chaque retour sur le hub et nulle part ailleurs : les plantes murissent en heures, une
     * minuterie qui tourne pendant qu'on regarde la grille ne changerait jamais le chiffre.
     * La lecture se fait hors du fil de l'ecran : au retour de la ferme, la sauvegarde vient de
     * changer, et la relire en entier sur ce fil retardait l'affichage du hub.
     */
    override fun onResume() {
        super.onResume()
        val app = applicationContext
        lifecycleScope.launch {
            val ready = withContext(Dispatchers.Default) { FarmState.readyToHarvest(app) }
            paneAdapters.forEach { it.setNotificationCount("farm", ready) }
        }
    }
}
