package com.Atom2Universe.app.games.cards

import android.graphics.drawable.Drawable
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.cards.crazyeights.CrazyEightsActivity
import com.Atom2Universe.app.games.cards.crazyeights.CrazyEightsArtwork
import com.Atom2Universe.app.games.cards.hearts.HeartsActivity
import com.Atom2Universe.app.games.cards.hearts.HeartsArtwork
import com.Atom2Universe.app.games.cards.spades.SpadesActivity
import com.Atom2Universe.app.games.cards.spades.SpadesArtwork
import com.Atom2Universe.app.games.cards.ohhell.OhHellActivity
import com.Atom2Universe.app.games.cards.ohhell.OhHellArtwork
import com.Atom2Universe.app.games.cards.euchre.EuchreActivity
import com.Atom2Universe.app.games.cards.euchre.EuchreArtwork
import com.Atom2Universe.app.games.cards.gofish.GoFishActivity
import com.Atom2Universe.app.games.cards.gofish.GoFishArtwork
import com.Atom2Universe.app.games.cards.ginrummy.GinRummyActivity
import com.Atom2Universe.app.games.cards.ginrummy.GinRummyArtwork
import com.Atom2Universe.app.games.cards.cribbage.CribbageActivity
import com.Atom2Universe.app.games.cards.cribbage.CribbageArtwork
import com.Atom2Universe.app.hub.HubTile
import kotlin.reflect.KClass

/**
 * Les jeux de cartes portés d'OpenSpiel : un seul endroit les déclare. Le hub des jeux les ajoute
 * à la suite des puzzles du kit, dans cet ordre ; la page des stats, la synchro Drive et le résumé
 * des neutrinos les lisent ici aussi.
 */
object CardGames {

    private class Entry(
        val id: String,
        val titleRes: Int,
        val descriptionRes: Int,
        val activity: Class<*>,
        val artwork: KClass<out Drawable>,
    )

    private val entries = listOf(
        Entry("crazyeights", R.string.crazy8_title, R.string.crazy8_description,
            CrazyEightsActivity::class.java, CrazyEightsArtwork::class),
        Entry("hearts", R.string.hearts_title, R.string.hearts_description,
            HeartsActivity::class.java, HeartsArtwork::class),
        Entry("spades", R.string.spades_title, R.string.spades_description,
            SpadesActivity::class.java, SpadesArtwork::class),
        Entry("ohhell", R.string.ohhell_title, R.string.ohhell_description,
            OhHellActivity::class.java, OhHellArtwork::class),
        Entry("euchre", R.string.euchre_title, R.string.euchre_description,
            EuchreActivity::class.java, EuchreArtwork::class),
        Entry("gofish", R.string.gofish_title, R.string.gofish_description,
            GoFishActivity::class.java, GoFishArtwork::class),
        Entry("ginrummy", R.string.gin_title, R.string.gin_description,
            GinRummyActivity::class.java, GinRummyArtwork::class),
        Entry("cribbage", R.string.cribbage_title, R.string.cribbage_description,
            CribbageActivity::class.java, CribbageArtwork::class),
    )

    /** Clé et nom de chaque jeu, dans l'ordre du hub. */
    fun titles(): List<Pair<String, Int>> = entries.map { it.id to it.titleRes }

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
