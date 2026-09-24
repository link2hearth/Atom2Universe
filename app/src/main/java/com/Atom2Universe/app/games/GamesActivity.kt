package com.Atom2Universe.app.games

import android.content.Intent
import android.widget.Toast
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.farm.FarmState
import com.Atom2Universe.app.hub.BaseHubActivity
import com.Atom2Universe.app.hub.HubTile

class GamesActivity : BaseHubActivity() {

    private companion object {
        const val KEY_ACCRETION_MOVED = "accretion_before_chess"
    }

    override fun getLayoutResId(): Int = R.layout.activity_base_hub

    override fun getPrefsName(): String = "games_hub_prefs"

    override fun getParentHubPrefsName(): String = "audio_hub_prefs"
    override fun getParentTileId(): String = "games"

    override fun getHubTitle(): Int = R.string.games_title

    override fun getHubSubtitle(): Int? = null

    // Chaque jeu a son illustration, déclarée une seule fois dans HubTileArtworks : pas de
    // couleur de tuile à choisir, et une grille de carrés seulement.
    override fun supportsTileColors(): Boolean = false
    override fun supportsListMode(): Boolean = false

    override fun getDefaultTiles(): List<HubTile> = GamesCatalog.tiles

    /**
     * Accrétion est montée devant les échecs. L'ordre par défaut suffit pour qui n'a jamais
     * déplacé de tuile ; pour les autres, l'ordre enregistré l'emporterait. On le corrige
     * donc **une seule fois** : ensuite, la tuile reste là où le joueur la range.
     */
    override fun normalizeTileOrder(tiles: List<HubTile>): List<HubTile> {
        if (hubPrefs.getBoolean(KEY_ACCRETION_MOVED, false)) return tiles
        hubPrefs.edit().putBoolean(KEY_ACCRETION_MOVED, true).apply()
        val accretion = tiles.firstOrNull { it.id == "bigger" } ?: return tiles
        val others = tiles.filter { it !== accretion }
        val chess = others.indexOfFirst { it.id == "chess" }
        if (chess < 0) return tiles
        val moved = others.toMutableList().apply { add(chess, accretion) }
        if (loadTileOrder().isNotEmpty()) saveTileOrder(moved.map { it.id })
        return moved
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
     */
    override fun onResume() {
        super.onResume()
        tilesAdapter.setNotificationCount("farm", FarmState.readyToHarvest(this))
    }
}
