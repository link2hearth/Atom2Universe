package com.Atom2Universe.app.games.roguelike

import android.graphics.Canvas
import com.Atom2Universe.app.games.roguelike.demo.*

enum class DungeonBackdrop(val outdoor: Boolean = false, val jungle: Boolean = false, val night: Boolean = false) {
    DUNGEON, INN, MEADOW_DAY(true), MEADOW_NIGHT(true, night = true),
    JUNGLE_DAY(true, jungle = true), JUNGLE_NIGHT(true, jungle = true, night = true),
    WHEAT_DAY(true), WHEAT_NIGHT(true, night = true),
    BATTLEFIELD_DAY(true), BATTLEFIELD_NIGHT(true, night = true),
    CEMETERY_DAY(true), CEMETERY_NIGHT(true, night = true), CRYPT, LIBRARY, MINE,
    MONASTERY_DAY(true), MONASTERY_NIGHT(true, night = true), SPACESHIP,
    PIRATE_DECK_DAY(true), PIRATE_DECK_NIGHT(true, night = true), CAPTAIN_CABIN
}

/** Catalogue et rendu uniques pour la démo et les combats réels. */
internal class DungeonBackdropArt {
    private val sceneArt = DungeonSceneArt()
    fun drawBackground(c: Canvas, backdrop: DungeonBackdrop, floorY: Float, worldHeight: Float) {
        if (backdrop == DungeonBackdrop.PIRATE_DECK_DAY || backdrop == DungeonBackdrop.PIRATE_DECK_NIGHT || backdrop == DungeonBackdrop.CAPTAIN_CABIN) {
            DungeonPirateBackdrop.draw(c, floorY, worldHeight, backdrop == DungeonBackdrop.CAPTAIN_CABIN, backdrop.night)
            return
        }
        if (backdrop == DungeonBackdrop.MONASTERY_DAY || backdrop == DungeonBackdrop.MONASTERY_NIGHT) {
            DungeonMonasteryBackdrop.draw(c, floorY, worldHeight, backdrop.night)
            return
        }
        if (backdrop == DungeonBackdrop.SPACESHIP) {
            DungeonSpaceshipBackdrop.draw(c, floorY, worldHeight)
            return
        }
        if (backdrop == DungeonBackdrop.LIBRARY) {
            DungeonLibraryBackdrop.draw(c, floorY, worldHeight)
            return
        }
        if (backdrop == DungeonBackdrop.MINE) {
            DungeonMineBackdrop.draw(c, floorY, worldHeight)
            return
        }
        if (backdrop == DungeonBackdrop.CRYPT) {
            DungeonCryptBackdrop.draw(c, floorY, worldHeight)
            return
        }
        if (backdrop == DungeonBackdrop.CEMETERY_DAY || backdrop == DungeonBackdrop.CEMETERY_NIGHT) {
            DungeonCemeteryBackdrop.draw(c, floorY, worldHeight, backdrop.night)
            return
        }
        if (backdrop == DungeonBackdrop.BATTLEFIELD_DAY || backdrop == DungeonBackdrop.BATTLEFIELD_NIGHT) {
            DungeonBattlefieldBackdrop.draw(c, floorY, worldHeight, backdrop.night)
            return
        }
        if (backdrop == DungeonBackdrop.WHEAT_DAY || backdrop == DungeonBackdrop.WHEAT_NIGHT) {
            DungeonWheatBackdrop.draw(c, floorY, worldHeight, backdrop.night)
            return
        }
        if (backdrop.outdoor) {
            DungeonOutdoorBackdrop.draw(c, floorY, worldHeight, backdrop.jungle, backdrop.night)
            return
        }
        if (backdrop == DungeonBackdrop.INN) {
            DungeonInnBackdrop.draw(c, floorY, worldHeight)
            return
        }
        sceneArt.drawBackground(c, floorY, worldHeight)
    }

    fun drawAtmosphere(c: Canvas, backdrop: DungeonBackdrop, floorY: Float, clock: Float) {
        if (backdrop == DungeonBackdrop.PIRATE_DECK_DAY || backdrop == DungeonBackdrop.PIRATE_DECK_NIGHT || backdrop == DungeonBackdrop.CAPTAIN_CABIN) {
            DungeonPirateBackdrop.atmosphere(c, floorY, clock, backdrop == DungeonBackdrop.CAPTAIN_CABIN, backdrop.night)
            return
        }
        if (backdrop == DungeonBackdrop.MONASTERY_DAY || backdrop == DungeonBackdrop.MONASTERY_NIGHT) {
            DungeonMonasteryBackdrop.atmosphere(c, floorY, clock, backdrop.night)
            return
        }
        if (backdrop == DungeonBackdrop.SPACESHIP) {
            DungeonSpaceshipBackdrop.atmosphere(c, floorY, clock)
            return
        }
        if (backdrop == DungeonBackdrop.LIBRARY) {
            DungeonLibraryBackdrop.atmosphere(c, floorY, clock)
            return
        }
        if (backdrop == DungeonBackdrop.MINE) {
            DungeonMineBackdrop.atmosphere(c, floorY, clock)
            return
        }
        if (backdrop == DungeonBackdrop.CRYPT) {
            DungeonCryptBackdrop.atmosphere(c, floorY, clock)
            return
        }
        if (backdrop == DungeonBackdrop.CEMETERY_DAY || backdrop == DungeonBackdrop.CEMETERY_NIGHT) {
            DungeonCemeteryBackdrop.atmosphere(c, floorY, clock, backdrop.night)
            return
        }
        if (backdrop == DungeonBackdrop.BATTLEFIELD_DAY || backdrop == DungeonBackdrop.BATTLEFIELD_NIGHT) {
            DungeonBattlefieldBackdrop.atmosphere(c, floorY, clock, backdrop.night)
            return
        }
        if (backdrop == DungeonBackdrop.WHEAT_DAY || backdrop == DungeonBackdrop.WHEAT_NIGHT) {
            DungeonWheatBackdrop.atmosphere(c, floorY, clock, backdrop.night)
            return
        }
        if (backdrop.outdoor) {
            DungeonOutdoorBackdrop.atmosphere(c, floorY, clock, backdrop.jungle, backdrop.night)
            return
        }
        if (backdrop == DungeonBackdrop.INN) {
            DungeonInnBackdrop.atmosphere(c, floorY, clock)
            return
        }
        sceneArt.drawAtmosphere(c, floorY, clock)
    }
}
