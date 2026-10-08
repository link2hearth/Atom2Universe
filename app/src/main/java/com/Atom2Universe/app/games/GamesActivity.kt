package com.Atom2Universe.app.games

import android.content.Intent
import android.widget.Toast
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.farm.FarmState
import com.Atom2Universe.app.hub.BaseHubActivity
import com.Atom2Universe.app.hub.HubTile

class GamesActivity : BaseHubActivity() {

    private companion object {
        const val KEY_CATALOG_ORDER = "catalog_order_families"
    }

    override fun getLayoutResId(): Int = R.layout.activity_base_hub

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

    /**
     * Le catalogue est rangé par familles de jeux. L'ordre enregistré l'emporterait sur lui ;
     * on l'efface donc **une seule fois** : ensuite, chaque tuile reste là où le joueur la range.
     */
    override fun normalizeTileOrder(tiles: List<HubTile>): List<HubTile> {
        if (hubPrefs.getBoolean(KEY_CATALOG_ORDER, false)) return tiles
        hubPrefs.edit().putBoolean(KEY_CATALOG_ORDER, true).remove("tile_order").apply()
        val rank = GamesCatalog.tiles.withIndex().associate { it.value.id to it.index }
        return tiles.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
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
