package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.trebuchet.gears.GearMachineGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * **Les deux jeux cadrent leur site de la même façon.**
 *
 * Troisième volet du même sujet, après `GearMachineSiteTest` (le sol) et `TirPartageTest`
 * (le tir) : le trébuchet et l'atelier d'engrenages avaient chacun leur caméra, et elles
 * ne disaient pas la même chose du sol.
 *
 * [ShotCamera] est une classe **ordinaire**, sans rien d'Android : c'est ce qui permet de
 * la mesurer ici au lieu de la regarder tourner sur la tablette. C'était la condition
 * pour que ce fichier existe.
 */
class CadragePartageTest {

    // Une tablette 1920 x 1200, densité 2.
    private val w = 1920
    private val h = 1200
    private val dp = 2f

    private fun camera(inset: Float = 34f) = ShotCamera(inset).apply { resize(w, h, dp) }

    /**
     * **Le sol se pose toujours au même endroit de l'écran, quel que soit le relief.**
     *
     * C'est le défaut que l'atelier avait et que le trébuchet n'avait plus. Sans plancher,
     * la ligne de sol est clouée à `h - inset·dp` : un village bâti au fond d'un vallon a
     * ses pieds `profondeur × échelle` pixels plus bas, donc hors de l'écran dès que ça
     * dépasse la marge du bas. Mesuré sur la graine 2 (sol à -8,3 m) :
     *
     * ```
     *   tout le site  (8 px/m) : pied du village à 1204 px chez l'atelier, 1132 au trébuchet
     *   un bâtiment  (64 px/m) : pied du village à 1670 px chez l'atelier, 1132 au trébuchet
     * ```
     *
     * 470 px sous le bord bas d'un écran qui en fait 1200. On vérifie donc la seule chose
     * qui compte : **à toutes les échelles**, le sol tombe à la même hauteur d'écran.
     */
    @Test
    fun `le sol se pose au meme endroit a toutes les echelles`() {
        val precedent = TargetRules.style
        try {
            TargetRules.style = TargetStyle.JEU
            var vus = 0
            for (seed in longArrayOf(2L, 4L, 28L)) {
                val game = GearMachineGame()
                game.loadSite(seed)
                if (game.terrain.lowest > -0.5f) continue
                vus++
                val site = game.targets
                val milieu = (site.left + site.right) / 2f
                val terrain: (Float, Float) -> Float =
                    { a, b -> game.terrain.lowestBetween(a, b) }

                var pire = 0f
                var refUn = 0f
                for ((i, span) in listOf(400f, 200f, 60f, 30f).withIndex()) {
                    val cam = camera()
                    // On pose le cadrage d'un coup : c'est le joueur qui vient d'arriver
                    // là au doigt, pas une caméra qui glisse.
                    cam.snap(1f, milieu, w / span, terrain)
                    // Deux relectures : le plancher se lisse, on le laisse arriver.
                    repeat(200) { cam.updateFloor(1f / 60f, terrain) }
                    cam.y = cam.groundY()

                    val ecran = cam.sy(cam.floor)
                    if (i == 0) refUn = ecran else pire = maxOf(pire, abs(ecran - refUn))
                    assertTrue(
                        "graine $seed a ${"%.0f".format(w / span)} px/m : le sol tombe à " +
                            "${"%.0f".format(ecran)} px sur un écran de $h",
                        ecran <= h
                    )
                }
                println(
                    "CADRAGE graine $seed (sol ${"%.1f".format(game.terrain.lowest)} m) : " +
                        "le sol reste à ${"%.0f".format(refUn)} px, écart max entre les " +
                        "échelles ${"%.2f".format(pire)} px"
                )
                assertTrue(
                    "le sol bouge de ${"%.1f".format(pire)} px quand on zoome",
                    pire < 1f
                )
            }
            assertTrue("aucun site en contrebas dans l'échantillon", vus > 0)
        } finally {
            TargetRules.style = precedent
        }
    }

    /**
     * **Le plancher ne remonte jamais au-dessus de zéro.**
     *
     * Une butte n'abaisse pas le cadrage : elle monte dans l'image comme il se doit, et
     * un terrain plat se cadre exactement comme avant que le relief n'existe. On ne paie
     * le décalage que lorsqu'un creux est réellement à l'écran.
     */
    @Test
    fun `une butte ne fait pas descendre le cadrage`() {
        val cam = camera()
        val butte: (Float, Float) -> Float = { _, _ -> 12f }
        cam.snap(1f, 0f, 30f, butte)
        repeat(200) { cam.updateFloor(1f / 60f, butte) }
        assertEquals("une butte a fait descendre le cadrage", 0f, cam.floor, 1e-4f)

        val plat: (Float, Float) -> Float = { _, _ -> 0f }
        val camPlat = camera()
        camPlat.snap(1f, 0f, 30f, plat)
        camPlat.y = camPlat.groundY()
        assertEquals(
            "un terrain plat ne se cadre plus comme avant",
            h - 34f * dp, camPlat.sy(0f), 0.01f
        )
    }

    /**
     * **Le plancher descend en glissant, il ne saute pas.**
     *
     * Il change quand un creux entre dans la vue, et un plancher qui sauterait ferait
     * sauter l'horizon avec lui. On descend dans le vallon comme on y marcherait.
     */
    @Test
    fun `le plancher se lisse au lieu de sauter`() {
        val cam = camera()
        val plat: (Float, Float) -> Float = { _, _ -> 0f }
        cam.snap(1f, 0f, 30f, plat)
        val vallon: (Float, Float) -> Float = { _, _ -> -8f }
        cam.updateFloor(1f / 60f, vallon)
        val premierPas = abs(cam.floor)
        println("PLANCHER première image dans le vallon : ${"%.2f".format(cam.floor)} m sur -8")
        assertTrue("le plancher a sauté de $premierPas m d'un coup", premierPas < 2f)
        repeat(300) { cam.updateFloor(1f / 60f, vallon) }
        assertEquals("le plancher n'arrive jamais au fond", -8f, cam.floor, 0.05f)
    }

    /**
     * **Une caméra qui a fini de rattraper sa cible est déclarée immobile.**
     *
     * Le calque des fantômes se garde d'une image à l'autre tant que le cadrage ne bouge
     * pas. Le rattrapage est asymptotique : à l'égalité stricte des flottants — la règle
     * qu'avait l'atelier — il faut **186 images** pour que la caméra se déclare immobile,
     * contre **80** au demi-pixel, la règle du trébuchet. Cent six images de calque
     * repeint pour rien, presque deux secondes, après chaque changement de cadrage.
     */
    @Test
    fun `une camera posee est declaree immobile`() {
        val cam = camera()
        val plat: (Float, Float) -> Float = { _, _ -> 0f }
        cam.snap(1f, 0f, 30f, plat)
        cam.beginFrame()

        var images = 0
        while (images < 1_000) {
            cam.follow(1f / 60f, 100f, 30f, 4.5f, plat)
            cam.beginFrame()
            images++
            if (!cam.moving) break
        }
        println("CAMÉRA déclarée immobile après $images images (60 img/s)")
        assertTrue(
            "la caméra n'est jamais immobile : le calque se repeint sans fin",
            !cam.moving
        )
        assertTrue("il a fallu $images images pour se poser", images < 120)
    }

    /**
     * **La projection et son inverse se répondent.**
     *
     * Les quatre fonctions étaient écrites deux fois, avec les termes dans un ordre
     * différent. Elles n'avaient pas encore divergé.
     */
    @Test
    fun `la projection est reversible`() {
        val cam = camera()
        cam.x = 137f
        cam.y = 22f
        cam.scale = 17f
        for (x in listOf(-500f, 0f, 137f, 980f)) {
            assertEquals(x, cam.worldX(cam.sx(x)), 0.01f)
        }
        for (y in listOf(-8f, 0f, 22f, 140f)) {
            assertEquals(y, cam.worldY(cam.sy(y)), 0.01f)
        }
    }
}
