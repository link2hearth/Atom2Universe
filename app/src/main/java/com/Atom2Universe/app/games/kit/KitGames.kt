package com.Atom2Universe.app.games.kit

import android.graphics.drawable.Drawable
import com.Atom2Universe.app.R
import com.Atom2Universe.app.hub.HubTile
import kotlin.reflect.KClass

/**
 * Les jeux portés depuis OpenSpiel et la collection de Simon Tatham : un seul endroit les
 * déclare. Le hub des jeux les ajoute **à la suite** de ses tuiles, dans cet ordre, et les
 * illustrations du hub y trouvent la leur.
 */
object KitGames {

    private class Entry(
        val id: String,
        val titleRes: Int,
        val descriptionRes: Int,
        val activity: Class<*>,
        val artwork: KClass<out Drawable>,
    )

    /** Clé et nom de chaque jeu, dans l'ordre du hub (pour le résumé des neutrinos). */
    fun titles(): List<Pair<String, Int>> = entries.map { it.id to it.titleRes }

    private val entries = listOf(
        // Glissement et rotation
        Entry("fifteen", R.string.fifteen_title, R.string.fifteen_description,
            com.Atom2Universe.app.games.puzzles.fifteen.FifteenActivity::class.java,
            com.Atom2Universe.app.games.puzzles.fifteen.FifteenArtwork::class),
        Entry("sixteen", R.string.sixteen_title, R.string.sixteen_description,
            com.Atom2Universe.app.games.puzzles.sixteen.SixteenActivity::class.java,
            com.Atom2Universe.app.games.puzzles.sixteen.SixteenArtwork::class),
        Entry("twiddle", R.string.twiddle_title, R.string.twiddle_description,
            com.Atom2Universe.app.games.puzzles.twiddle.TwiddleActivity::class.java,
            com.Atom2Universe.app.games.puzzles.twiddle.TwiddleArtwork::class),
        Entry("netslide", R.string.netslide_title, R.string.netslide_description,
            com.Atom2Universe.app.games.puzzles.netslide.NetslideActivity::class.java,
            com.Atom2Universe.app.games.puzzles.netslide.NetslideArtwork::class),
        Entry("flip", R.string.flip_title, R.string.flip_description,
            com.Atom2Universe.app.games.puzzles.flip.FlipActivity::class.java,
            com.Atom2Universe.app.games.puzzles.flip.FlipArtwork::class),
        Entry("inertia", R.string.inertia_title, R.string.inertia_description,
            com.Atom2Universe.app.games.puzzles.inertia.InertiaActivity::class.java,
            com.Atom2Universe.app.games.puzzles.inertia.InertiaArtwork::class),
        Entry("cube", R.string.cube_title, R.string.cube_description,
            com.Atom2Universe.app.games.puzzles.cube.CubeActivity::class.java,
            com.Atom2Universe.app.games.puzzles.cube.CubeArtwork::class),
        // Chiffres et grilles
        Entry("towers", R.string.towers_title, R.string.towers_description,
            com.Atom2Universe.app.games.puzzles.towers.TowersActivity::class.java,
            com.Atom2Universe.app.games.puzzles.towers.TowersArtwork::class),
        Entry("unequal", R.string.unequal_title, R.string.unequal_description,
            com.Atom2Universe.app.games.puzzles.unequal.UnequalActivity::class.java,
            com.Atom2Universe.app.games.puzzles.unequal.UnequalArtwork::class),
        Entry("keen", R.string.keen_title, R.string.keen_description,
            com.Atom2Universe.app.games.puzzles.keen.KeenActivity::class.java,
            com.Atom2Universe.app.games.puzzles.keen.KeenArtwork::class),
        Entry("unruly", R.string.unruly_title, R.string.unruly_description,
            com.Atom2Universe.app.games.puzzles.unruly.UnrulyActivity::class.java,
            com.Atom2Universe.app.games.puzzles.unruly.UnrulyArtwork::class),
        Entry("range", R.string.range_title, R.string.range_description,
            com.Atom2Universe.app.games.puzzles.range.RangeActivity::class.java,
            com.Atom2Universe.app.games.puzzles.range.RangeArtwork::class),
        Entry("singles", R.string.singles_title, R.string.singles_description,
            com.Atom2Universe.app.games.puzzles.singles.SinglesActivity::class.java,
            com.Atom2Universe.app.games.puzzles.singles.SinglesArtwork::class),
        Entry("filling", R.string.filling_title, R.string.filling_description,
            com.Atom2Universe.app.games.puzzles.filling.FillingActivity::class.java,
            com.Atom2Universe.app.games.puzzles.filling.FillingArtwork::class),
        // Boucles et chemins
        Entry("loopy", R.string.loopy_title, R.string.loopy_description,
            com.Atom2Universe.app.games.puzzles.loopy.LoopyActivity::class.java,
            com.Atom2Universe.app.games.puzzles.loopy.LoopyArtwork::class),
        Entry("pearl", R.string.pearl_title, R.string.pearl_description,
            com.Atom2Universe.app.games.puzzles.pearl.PearlActivity::class.java,
            com.Atom2Universe.app.games.puzzles.pearl.PearlArtwork::class),
        Entry("tracks", R.string.tracks_title, R.string.tracks_description,
            com.Atom2Universe.app.games.puzzles.tracks.TracksActivity::class.java,
            com.Atom2Universe.app.games.puzzles.tracks.TracksArtwork::class),
        Entry("slant", R.string.slant_title, R.string.slant_description,
            com.Atom2Universe.app.games.puzzles.slant.SlantActivity::class.java,
            com.Atom2Universe.app.games.puzzles.slant.SlantArtwork::class),
        Entry("signpost", R.string.signpost_title, R.string.signpost_description,
            com.Atom2Universe.app.games.puzzles.signpost.SignpostActivity::class.java,
            com.Atom2Universe.app.games.puzzles.signpost.SignpostArtwork::class),
        Entry("palisade", R.string.palisade_title, R.string.palisade_description,
            com.Atom2Universe.app.games.puzzles.palisade.PalisadeActivity::class.java,
            com.Atom2Universe.app.games.puzzles.palisade.PalisadeArtwork::class),
        // Régions et dessin
        Entry("map", R.string.map_title, R.string.map_description,
            com.Atom2Universe.app.games.puzzles.map.MapActivity::class.java,
            com.Atom2Universe.app.games.puzzles.map.MapArtwork::class),
        Entry("galaxies", R.string.galaxies_title, R.string.galaxies_description,
            com.Atom2Universe.app.games.puzzles.galaxies.GalaxiesActivity::class.java,
            com.Atom2Universe.app.games.puzzles.galaxies.GalaxiesArtwork::class),
        Entry("rect", R.string.rect_title, R.string.rect_description,
            com.Atom2Universe.app.games.puzzles.rect.RectActivity::class.java,
            com.Atom2Universe.app.games.puzzles.rect.RectArtwork::class),
        Entry("pattern", R.string.pattern_title, R.string.pattern_description,
            com.Atom2Universe.app.games.puzzles.pattern.PatternActivity::class.java,
            com.Atom2Universe.app.games.puzzles.pattern.PatternArtwork::class),
        Entry("mosaic", R.string.mosaic_title, R.string.mosaic_description,
            com.Atom2Universe.app.games.puzzles.mosaic.MosaicActivity::class.java,
            com.Atom2Universe.app.games.puzzles.mosaic.MosaicArtwork::class),
        Entry("dominosa", R.string.dominosa_title, R.string.dominosa_description,
            com.Atom2Universe.app.games.puzzles.dominosa.DominosaActivity::class.java,
            com.Atom2Universe.app.games.puzzles.dominosa.DominosaArtwork::class),
        // Placement et déduction
        Entry("tents", R.string.tents_title, R.string.tents_description,
            com.Atom2Universe.app.games.puzzles.tents.TentsActivity::class.java,
            com.Atom2Universe.app.games.puzzles.tents.TentsArtwork::class),
        Entry("magnets", R.string.magnets_title, R.string.magnets_description,
            com.Atom2Universe.app.games.puzzles.magnets.MagnetsActivity::class.java,
            com.Atom2Universe.app.games.puzzles.magnets.MagnetsArtwork::class),
        Entry("undead", R.string.undead_title, R.string.undead_description,
            com.Atom2Universe.app.games.puzzles.undead.UndeadActivity::class.java,
            com.Atom2Universe.app.games.puzzles.undead.UndeadArtwork::class),
        Entry("lightup", R.string.lightup_title, R.string.lightup_description,
            com.Atom2Universe.app.games.puzzles.lightup.LightUpActivity::class.java,
            com.Atom2Universe.app.games.puzzles.lightup.LightUpArtwork::class),
        Entry("blackbox", R.string.blackbox_title, R.string.blackbox_description,
            com.Atom2Universe.app.games.puzzles.blackbox.BlackBoxActivity::class.java,
            com.Atom2Universe.app.games.puzzles.blackbox.BlackBoxArtwork::class),
        Entry("guess", R.string.guess_title, R.string.guess_description,
            com.Atom2Universe.app.games.puzzles.guess.GuessActivity::class.java,
            com.Atom2Universe.app.games.puzzles.guess.GuessArtwork::class),
        // Réflexion et arcade calme
        Entry("flood", R.string.flood_title, R.string.flood_description,
            com.Atom2Universe.app.games.puzzles.flood.FloodActivity::class.java,
            com.Atom2Universe.app.games.puzzles.flood.FloodArtwork::class),
        Entry("samegame", R.string.samegame_title, R.string.samegame_description,
            com.Atom2Universe.app.games.puzzles.samegame.SameGameActivity::class.java,
            com.Atom2Universe.app.games.puzzles.samegame.SameGameArtwork::class),
        Entry("pegs", R.string.pegs_title, R.string.pegs_description,
            com.Atom2Universe.app.games.puzzles.pegs.PegsActivity::class.java,
            com.Atom2Universe.app.games.puzzles.pegs.PegsArtwork::class),
        Entry("untangle", R.string.untangle_title, R.string.untangle_description,
            com.Atom2Universe.app.games.puzzles.untangle.UntangleActivity::class.java,
            com.Atom2Universe.app.games.puzzles.untangle.UntangleArtwork::class),
    )

    val tiles: List<HubTile> = entries.map {
        HubTile(
            id = it.id,
            titleRes = it.titleRes,
            descriptionRes = it.descriptionRes,
            iconRes = android.R.drawable.ic_menu_view,
            defaultColorRes = R.color.game_tile_kit,
            activityClass = it.activity,
        )
    }

    val artworks: Map<String, KClass<out Drawable>> = entries.associate { it.activity.name to it.artwork }
}
