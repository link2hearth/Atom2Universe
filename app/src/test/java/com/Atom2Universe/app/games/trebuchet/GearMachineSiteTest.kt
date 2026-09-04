package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * **L'atelier d'engrenages tire sur le même terrain que le trébuchet.**
 *
 * Il tirait sur une dalle plate, et le décor s'arrêtait au ciel. Il a maintenant des
 * collines et des bâtiments — le même relief, les mêmes pierres destructibles, sortis de
 * la même graine. Ce fichier garde les quatre choses qui peuvent casser en chemin, et
 * elles cassent toutes en silence : rien de tout ça ne lève d'exception, ça se voit à
 * l'œil ou ça ne se voit pas du tout.
 */
class GearMachineSiteTest {

    /**
     * **Un atelier neuf a des collines et un village.**
     *
     * C'est le test le plus bête et le plus utile : le site se charge dans le `init` du
     * modèle, et une erreur d'ordre d'initialisation en Kotlin donnerait un terrain plat
     * et zéro pierre sans jamais rien signaler.
     */
    @Test
    fun `un atelier neuf a du relief et des batiments`() {
        val game = GearMachineGame()
        val site = game.targets
        println(
            "ATELIER relief de ${"%.1f".format(game.terrain.highest - game.terrain.lowest)} m, " +
                "${site.pieces.size} pierres entre ${"%.0f".format(site.left)} et " +
                "${"%.0f".format(site.right)} m"
        )
        assertTrue("l'atelier est resté une dalle plate", !game.terrain.flat)
        assertTrue("l'atelier n'a aucun bâtiment", site.pieces.size >= 8)
        assertTrue("le site est collé à la machine", site.left > 20f)
    }

    /**
     * **Le relief est plat sous la machine.**
     *
     * Une colline sous l'atelier enterrerait les roues, et le joueur verrait sa machine
     * à moitié dans la terre sans comprendre pourquoi. C'est [TargetGenerator] qui le
     * garantit — il bâtit le terrain autour des bâtiments — mais rien ne le disait du
     * côté de l'atelier, dont la machine ne se tient pas au même endroit que le
     * trébuchet.
     */
    @Test
    fun `le sol est plat sous la machine`() {
        val game = GearMachineGame()
        val b = game.bounds()
        var x = b[0] - 5f
        while (x <= b[1] + 5f) {
            assertEquals(
                "le sol monte à ${"%.2f".format(game.terrain.heightAt(x))} m sous la machine (x=$x)",
                0f, game.terrain.heightAt(x), 0.05f
            )
            x += 1f
        }
    }

    /**
     * **Les collines arrêtent vraiment un boulet.**
     *
     * Le relief n'est du décor que si ses corps ne sont pas dans le monde physique. On
     * lâche donc une bille au-dessus du point le plus haut du terrain et on regarde où
     * elle s'arrête : au sommet, et pas à l'altitude zéro trois mètres plus bas.
     */
    @Test
    fun `une bille lachee sur une colline s arrete dessus`() {
        val game = GearMachineGame()
        // Le point le plus haut du site, là où un sol resté plat se trahirait le mieux.
        var sommetX = game.targets.left
        var sommetY = game.terrain.heightAt(sommetX)
        var x = game.targets.left
        while (x <= game.targets.right) {
            val y = game.terrain.heightAt(x)
            if (y > sommetY) { sommetY = y; sommetX = x }
            x += 0.5f
        }
        // Un site parfaitement plat ne prouverait rien : on ne teste que s'il y a du relief.
        if (sommetY < 0.5f) {
            println("ATELIER site plat (${"%.2f".format(sommetY)} m) : rien à vérifier")
            return
        }

        val bille = PhysBody.circle(0.25f, 20f).apply {
            this.x = sommetX
            y = sommetY + 4f
            category = TrebuchetCategory.BALL
            collidesWith = TrebuchetCategory.BALL_FREE_MASK
            collisionLayer = -32
            collisionLayerDepth = 65
        }
        game.world.add(bille)
        repeat(600) { game.step(1f / 120f) }

        println(
            "BILLE lâchée sur une colline de ${"%.2f".format(sommetY)} m, " +
                "posée à ${"%.2f".format(bille.y)} m"
        )
        assertTrue(
            "la bille a traversé la colline : ${"%.2f".format(bille.y)} m pour un sommet " +
                "à ${"%.2f".format(sommetY)} m",
            bille.y > sommetY - 0.5f
        )
    }

    /**
     * **Le mécanisme, lui, ne touche jamais le paysage.**
     *
     * Les catégories de collision de l'atelier ont été renumérotées pour reprendre
     * celles du trébuchet — sans quoi `TargetField`, qui ré-estampille ses pierres à
     * chaque cassure, aurait remis les siennes. Le seul risque de cette manœuvre est
     * qu'une pierre ou un gravat vienne cogner un engrenage : on vérifie donc que les
     * roues ne rencontrent rien, ce qui est vrai par construction et doit le rester.
     */
    @Test
    fun `les engrenages ne collisionnent avec rien`() {
        val game = GearMachineGame()
        for (gear in game.gears) {
            assertEquals(
                "une roue est entrée dans le monde des collisions",
                0, gear.body.collidesWith
            )
        }
        // Et le sol accepte bien le boulet, les pierres et les gravats.
        val masque = game.ground.collidesWith
        assertTrue("le sol ignore le boulet", masque and TrebuchetCategory.BALL != 0)
        assertTrue("le sol ignore les pierres", masque and TrebuchetCategory.TARGET != 0)
        assertTrue("le sol ignore les gravats", masque and TrebuchetCategory.DEBRIS != 0)
        assertTrue(
            "le sol arrête les engrenages",
            masque and com.Atom2Universe.app.games.trebuchet.gears.GearMachineRules.CATEGORY_WHEEL == 0
        )
    }

    /**
     * **Le site se rendort, sinon il coûte deux cents fois le prix d'une image.**
     *
     * C'est la mesure de [TargetField] : une image de vol coûte trente microsecondes
     * sans cible et sept millisecondes avec un château, parce que ses quatre-vingts
     * pierres sont résolues à chacun des vingt sous-pas d'un boulet rapide. La mise en
     * veille est ce qui rend le site gratuit tant qu'on ne le touche pas — et elle
     * n'arrive que si quelqu'un appelle `update`, ce que l'atelier a failli oublier.
     */
    @Test
    fun `le site s endort quand personne ne le touche`() {
        val game = GearMachineGame()
        repeat(1200) { game.step(1f / 120f) }
        println("ATELIER site en veille après dix secondes : ${game.targets.dormant}")
        assertTrue("le site ne s'endort jamais : il coûtera plein pot", game.targets.dormant)
    }

    /** Sans graine, l'atelier retrouve sa dalle nue — c'est la sortie de secours. */
    @Test
    fun `on peut retirer le site`() {
        val game = GearMachineGame()
        game.loadSite(null)
        assertTrue("le relief est resté", game.terrain.flat)
        assertEquals("des pierres ont survécu", 0, game.targets.pieces.size)
        assertEquals("le sol a bougé", 0f, game.terrain.heightAt(50f), 1e-4f)
        assertTrue("la machine a disparu avec le site", game.gears.isNotEmpty())
        assertEquals(0f, abs(game.terrain.heightAt(0f)), 1e-4f)
    }

    /**
     * **Un site posé au fond d'un vallon reste au fond du vallon.**
     *
     * L'atelier pose une dalle de quarante kilomètres sous tout le monde : elle porte la
     * machine et ferme le monde de part et d'autre du relief, lequel ne couvre que la
     * longueur du site. Cette dalle a longtemps eu sa face supérieure calée **à
     * l'altitude zéro** — et un site de vallon descend jusqu'à huit mètres sous le
     * niveau de la machine. C'était donc la dalle qui portait le village, pas le relief,
     * pendant que le décor se dessinait sur le vrai profil : bâtiments et gravats
     * flottaient huit mètres au-dessus du sol qu'on leur voyait.
     *
     * Le pire était que ça ne se voyait qu'au **premier impact** : la cible se charge
     * endormie, donc rien ne bougeait tant que rien ne la touchait. Le boulet arrivait,
     * tout se réveillait d'un coup, et le site remontait en bloc.
     *
     * On mesure donc la seule chose qui compte : après avoir fait tourner le monde, une
     * pierre est-elle toujours là où le générateur l'avait posée.
     */
    @Test
    fun `un site en contrebas ne remonte pas a l altitude zero`() {
        val precedent = TargetRules.style
        try {
            TargetRules.style = TargetStyle.JEU
            var vallonsVus = 0
            for (seed in 1L..40L) {
                val game = GearMachineGame()
                game.loadSite(seed)
                val site = game.targets
                if (site.pieces.isEmpty()) continue
                // Seuls les reliefs qui descendent sous la machine sont concernés.
                if (game.terrain.lowest > -0.5f) continue
                vallonsVus++

                val avant = site.pieces.map { it.body to it.body.y }
                site.wake()
                repeat(600) {
                    game.world.stepFrame(1f / 60f)
                    site.update(1f / 60f)
                }
                var pire = 0f
                for ((corps, y0) in avant) pire = maxOf(pire, corps.y - y0)
                println(
                    "VALLON graine $seed : sol à ${"%.1f".format(game.terrain.lowest)} m, " +
                        "la pierre la plus soulevée a monté de ${"%.2f".format(pire)} m"
                )
                assertTrue(
                    "graine $seed : le site a remonté de ${"%.2f".format(pire)} m — " +
                        "la dalle passe au-dessus du relief",
                    pire < 0.5f
                )
            }
            assertTrue("aucun site en contrebas dans les quarante premières graines", vallonsVus > 0)
        } finally {
            TargetRules.style = precedent
        }
    }
}
