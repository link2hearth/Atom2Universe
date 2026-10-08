package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * **Un triangle est un solide comme un autre.**
 *
 * Le moteur traite les boîtes contre les boîtes avec un code spécialisé, et tout le
 * reste — c'est-à-dire tout ce qui contient un triangle — avec un solveur générique. Ce
 * solveur ne rendait qu'**un seul** point de contact, au milieu de la face d'appui. Ça
 * semblait raisonnable : « une base triangulaire repose sur toute sa longueur ». Mais un
 * point unique ne transmet **aucun couple**. Un triangle posé à plat pivotait donc sur
 * place comme sur une pointe.
 *
 * Le jeu d'équilibre l'a payé deux fois. On a d'abord bloqué la rotation des triangles
 * pour les empêcher de tourner ; et un solide à rotation bloquée posé sur une planche qui
 * s'incline **reste horizontal**, ne la touche plus que par un coin, et lui transmet son
 * poids à ce coin au lieu du dessous de son centre de gravité. Mesuré le 04/09/2026 sur
 * douze niveaux MEDIUM disposés au couple exactement nul : la planche partait à **6 à 9
 * degrés**, les rectangles suivant son angle au dixième près pendant que les triangles
 * restaient à 0,00°. Rien ne pouvait le signaler — les briques ne bougeaient pas d'un
 * millimètre, et le couple relevé, qui se lit sur leur abscisse, restait nul.
 *
 * Deux corrections en une : le solveur générique découpe maintenant les faces comme le
 * fait celui des boîtes et rend jusqu'à **deux** contacts, et le blocage de rotation a
 * disparu. Ce fichier garde les deux propriétés qui le prouvent, et elles échouent toutes
 * les deux en silence : un triangle qui tourne sur place se voit à l'œil, un triangle qui
 * ne suit pas son support ne se voit pas du tout.
 */
class PhysicsTriangleTest {

    /** Un triangle isocèle, base en bas, d'aire [area] — comme les briques du jeu. */
    private fun triangle(area: Float, x: Float, y: Float, mass: Float = 10f): PhysBody {
        val half = sqrt(area / 2f)
        return PhysBody.compound(mass) { triangle(half, half) }.apply {
            this.x = x
            this.y = y
            friction = 0.62f
        }
    }

    /**
     * **Un triangle lâché sur le sol se pose à plat et ne tourne pas.**
     *
     * C'est le symptôme qu'on voyait : avec un seul point de contact, rien ne s'oppose au
     * couple, et le triangle part en rotation dès qu'il touche.
     */
    @Test
    fun `un triangle lache se pose a plat et ne tourne pas`() {
        val world = worldWithGround()
        val t = triangle(0.09f, 0f, 0.8f)
        world.add(t)

        world.simulate(4f)

        println(
            "TRIANGLE posé : y=${"%.3f".format(t.y)} angle=" +
                "${"%.2f".format(Math.toDegrees(t.angle.toDouble()))}°"
        )
        assertTrue("le triangle traverse le sol : y=${t.y}", t.y > 0.02f)
        assertTrue(
            "le triangle pivote sur place : ${Math.toDegrees(t.angle.toDouble())}°",
            abs(t.angle) < 0.08f
        )
        assertTrue("le triangle ne s'immobilise pas : v=${t.vy}", abs(t.vy) < 0.05f)
    }

    /**
     * **Un triangle posé sur un plan incliné épouse la pente, comme une boîte.**
     *
     * C'est le test qui compte, et c'est celui qu'aucun compteur du jeu ne pouvait
     * remplacer. On pose côte à côte une boîte et un triangle de même masse sur un sol
     * penché de dix degrés, et on demande la seule chose qui les distinguait : que les
     * deux aient **le même angle** que leur support.
     *
     * Avec un contact unique, ou avec une rotation bloquée, le triangle reste à zéro : il
     * ne touche alors la pente que par un coin, et lui transmet tout son poids en ce
     * point — ce qui fausse silencieusement tout ce qui pèse.
     */
    @Test
    fun `un triangle sur une pente epouse la pente comme une boite`() {
        val pente = Math.toRadians(10.0).toFloat()
        val world = PhysWorld()
        world.add(
            PhysBody(5f, 0.5f, 0f).apply {
                x = 0f
                y = -0.5f
                angle = pente
                lockPosition = true
                lockRotation = true
                friction = 0.9f
                refreshMass()
            }
        )
        // Posés au ras de la pente, chacun de son côté, assez loin pour ne pas se toucher.
        val boite = box(0.15f, 0.15f, 10f, -0.8f, -0.8f * kotlin.math.tan(pente) + 0.30f)
        val tri = triangle(0.09f, 0.8f, 0.8f * kotlin.math.tan(pente) + 0.30f)
        world.add(boite)
        world.add(tri)

        world.simulate(5f)

        val aBoite = Math.toDegrees(boite.angle.toDouble())
        val aTri = Math.toDegrees(tri.angle.toDouble())
        println(
            "PENTE 10° : la boîte est à ${"%.2f".format(aBoite)}°, " +
                "le triangle à ${"%.2f".format(aTri)}°"
        )
        assertTrue(
            "la boîte n'épouse pas la pente : $aBoite° pour 10°",
            abs(aBoite - 10.0) < 2.0
        )
        assertTrue(
            "le triangle n'épouse pas la pente : $aTri° pour 10° (la boîte, elle, " +
                "est à $aBoite°) — il ne la touche donc que par un coin",
            abs(aTri - 10.0) < 2.0
        )
    }
}
