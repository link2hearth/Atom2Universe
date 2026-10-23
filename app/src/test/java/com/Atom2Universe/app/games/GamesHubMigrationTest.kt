package com.Atom2Universe.app.games

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class GamesHubMigrationTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("games_hub_prefs", Context.MODE_PRIVATE)
    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun followsAccretionAcrossPanesWithoutReorderingOtherGames() {
        prefs.edit().putString("tile_order", "chess,balance")
            .putString("tile_order_1", "golf,bigger,clicker")
            .putString("tile_panes", "bigger:1;chess:0;golf:1").commit()
        GamesHubMigration.separateRicochet(prefs)
        assertEquals("chess,balance", prefs.getString("tile_order", null))
        assertEquals("golf,bigger,ricochet,clicker", prefs.getString("tile_order_1", null))
        assertTrue(prefs.getString("tile_panes", "")!!.contains("ricochet:1"))
        // Un déplacement ultérieur du joueur ne doit jamais être annulé.
        prefs.edit().putString("tile_order_1", "ricochet,golf,bigger,clicker").commit()
        GamesHubMigration.separateRicochet(prefs)
        assertEquals("ricochet,golf,bigger,clicker", prefs.getString("tile_order_1", null))
    }

    @Test fun freshHubKeepsDefaultOrderAndSeparateDestinations() {
        GamesHubMigration.separateRicochet(prefs)
        assertFalse(prefs.contains("tile_order"))
        val index = GamesCatalog.tiles.indexOfFirst { it.id == "bigger" }
        assertEquals("ricochet", GamesCatalog.tiles[index + 1].id)
        assertEquals("BiggerActivity", GamesCatalog.tiles[index].activityClass?.simpleName)
        assertEquals("RicochetMenuActivity", GamesCatalog.tiles[index + 1].activityClass?.simpleName)
    }

    @Test fun oldOrderWithoutAccretionStillPlacesNewTileBesideIt() {
        prefs.edit().putString("tile_order", "chess,balance").commit()
        GamesHubMigration.separateRicochet(prefs)
        assertEquals("chess,balance,bigger,ricochet", prefs.getString("tile_order", null))
    }
}
