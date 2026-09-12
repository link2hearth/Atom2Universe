package com.Atom2Universe.app.hub

import android.graphics.drawable.Drawable
import com.Atom2Universe.app.crypto.ClickerHubTileDrawable
import com.Atom2Universe.app.crypto.MainClickerActivity
import com.Atom2Universe.app.games.farm.FarmActivity
import com.Atom2Universe.app.games.farm.FarmHubTileDrawable
import com.Atom2Universe.app.games.survivor.SurvivorActivity
import com.Atom2Universe.app.games.survivor.SurvivorHubTileDrawable
import kotlin.reflect.KClass

/**
 * Les illustrations de tuile, rangees par activite.
 *
 * Un raccourci ajoute sur une tuile du hub principal ne garde que le nom de classe de son activite
 * et une couleur figee au moment de l'ajout - pour une tuile illustree, cette couleur est celle
 * qu'on ne voit jamais sous le dessin, souvent presque noire. Retrouver l'illustration a partir du
 * nom de classe marche aussi pour les raccourcis deja enregistres, sans changer leur format.
 */
object HubTileArtworks {
    private val byActivity: Map<String, KClass<out Drawable>> = mapOf(
        MainClickerActivity::class.java.name to ClickerHubTileDrawable::class,
        FarmActivity::class.java.name to FarmHubTileDrawable::class,
        SurvivorActivity::class.java.name to SurvivorHubTileDrawable::class
    )

    fun forActivity(activityClassName: String): KClass<out Drawable>? = byActivity[activityClassName]

    /**
     * Le libelle pose sur un dessin clair : sa couleur, et de combien l'agrandir. Sans style, le
     * libelle reste blanc et a sa taille normale. Lu pour la tuile du hub ET pour son raccourci.
     */
    class TextStyle(val color: Int, val scale: Float)

    private val textStyles: Map<String, TextStyle> = mapOf(
        // L'herbe de la ferme est claire : le titre y est ecrit en noir, en grand.
        FarmActivity::class.java.name to TextStyle(android.graphics.Color.BLACK, 2f)
    )

    fun textStyleFor(activityClassName: String): TextStyle? = textStyles[activityClassName]
}
