package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Le relief : sa géométrie, ses corps, et ce qu'il promet aux constructions.
 *
 * Trois choses peuvent mal tourner avec un sol qui n'est plus une droite, et ce sont
 * les trois qu'on mesure ici. **Un trou** : deux dalles qui ne se rejoignent pas
 * exactement, et le boulet passe au travers du monde. **Une lamelle** : une dalle plus
 * mince que le boulet, et le moteur découpe chaque image en trente sous-pas pour rien.
 * **Un bâtiment en l'air** : un module posé à une altitude qui n'est pas celle de son
 * plateau, et le site s'effondre au chargement sans que personne l'ait touché.
 */
class TrebuchetTerrainTest {

    @Before
    fun modeArcade() {
        TargetRules.style = TargetStyle.ARCADE
    }

    @After
    fun rendLeMode() {
        TargetRules.style = TargetStyle.ARCADE
    }

    // ── La géométrie ──────────────────────────────────────────────────────────

    @Test
    fun `le profil interpole entre ses noeuds`() {
        val t = Terrain(
            listOf(
                TerrainNode(0f, 0f),
                TerrainNode(10f, 0f),
                TerrainNode(20f, 10f),
                TerrainNode(30f, 10f)
            )
        )
        assertEquals("avant le début", 0f, t.heightAt(-50f), 1e-4f)
        assertEquals("sur le plat", 0f, t.heightAt(5f), 1e-4f)
        assertEquals("au milieu de la pente", 5f, t.heightAt(15f), 1e-4f)
        assertEquals("sur le plateau", 10f, t.heightAt(25f), 1e-4f)
        assertEquals("après la fin", 10f, t.heightAt(500f), 1e-4f)
    }

    @Test
    fun `une falaise rend l altitude haute`() {
        // Une marche verticale : deux nœuds à la même abscisse. « À quelle hauteur est
        // le sol ici » veut dire « où se poserait quelque chose lâché ici », et au bord
        // d'une falaise c'est le haut.
        val t = Terrain(
            listOf(TerrainNode(0f, 0f), TerrainNode(10f, 0f), TerrainNode(10f, 8f), TerrainNode(20f, 8f))
        )
        assertEquals(0f, t.heightAt(9.9f), 1e-3f)
        assertEquals(8f, t.heightAt(10f), 1e-3f)
        assertEquals(8f, t.heightAt(10.1f), 1e-3f)
    }

    @Test
    fun `les noeuds alignes sont jetes`() {
        // Quatre nœuds pour une seule droite : le relief n'en garde que deux, donc un
        // seul corps immobile. C'est ce qui fait qu'une plaine coûte exactement ce
        // qu'elle coûtait avant que le relief n'existe.
        val t = Terrain(
            listOf(
                TerrainNode(0f, 0f), TerrainNode(10f, 0f),
                TerrainNode(30f, 0f), TerrainNode(60f, 0f)
            )
        )
        assertEquals(2, t.nodes.size)
        assertEquals(1, t.bodies().size)
        assertTrue("une plaine devrait se déclarer plate", t.flat)
    }

    // ── Les corps ─────────────────────────────────────────────────────────────

    @Test
    fun `aucune dalle n est plus mince que le boulet`() {
        // Le moteur règle ses sous-pas sur la pièce la plus mince du monde. Une dalle
        // de terrain fine ferait ramer le jeu sans qu'aucun test ne le dise — ce
        // test-ci le dit.
        for (seed in 1L..24L) {
            val lvl = TargetGenerator.generate(seed)
            for (b in lvl.terrain.bodies()) {
                assertTrue(
                    "graine $seed : dalle de ${b.smallestHalfExtent} m de demi-épaisseur",
                    b.smallestHalfExtent > TrebuchetRules.BALL_RADIUS * 4f
                )
            }
        }
    }

    @Test
    fun `le sol est etanche sous une bille qu on laisse tomber`() {
        // Le test qui compte vraiment : on lâche une bille tous les deux mètres au-dessus
        // de tout le relief, on la laisse tomber, et elle doit s'arrêter à l'altitude
        // que le profil annonce. Un trou entre deux dalles se voit ici et nulle part
        // ailleurs.
        for (seed in 1L..12L) {
            val lvl = TargetGenerator.generate(seed)
            val relief = lvl.terrain
            val w = PhysWorld().apply { iterations = 12 }
            for (b in relief.bodies()) w.add(b)

            var x = lvl.distance - 120f
            var pire = 0f
            var pireX = 0f
            while (x < lvl.distance + 90f) {
                val bille = PhysBody.circle(0.4f, 30f).apply {
                    this.x = x
                    y = relief.highest + 12f
                    friction = 0.6f
                    category = TrebuchetCategory.DEBRIS
                    collidesWith = TrebuchetCategory.GROUND
                }
                w.add(bille)
                repeat(240) { w.stepFrame(1f / 120f) }
                // Deux précautions, et chacune vaut pour la même raison : ce qu'on
                // cherche est un **trou**, pas une pente. La bille roule, donc on juge
                // sur le sol de là où elle est arrivée et pas de là où elle est partie ;
                // et on ne retient que l'enfoncement, parce qu'une bille arrêtée
                // au-dessus du profil est simplement posée en équilibre quelque part,
                // ce qui ne dit rien du sol. Une bille arrêtée en dessous, elle, a
                // traversé quelque chose.
                val enfoncement = bille.y - 0.4f - relief.heightAt(bille.x)
                if (enfoncement < pire) {
                    pire = enfoncement
                    pireX = bille.x
                }
                w.remove(bille)
                x += 2f
            }
            println(
                "ÉTANCHE graine $seed ${lvl.shape} : pire enfoncement ${"%.3f".format(pire)} m " +
                    "à ${"%.0f".format(pireX)} m, ${relief.bodies().size} dalles"
            )
            assertTrue(
                "graine $seed : une bille a traversé le sol de ${"%.2f".format(pire)} m " +
                    "en x=${"%.0f".format(pireX)}",
                pire > -0.25f
            )
        }
    }

    // ── Ce que le relief promet aux constructions ─────────────────────────────

    @Test
    fun `chaque pierre est posee sur le sol de son abscisse`() {
        // La promesse du système : une construction est bâtie à plat puis soulevée sur
        // son plateau. Aucune pierre ne doit donc être enterrée, et aucune ne doit
        // flotter très haut au-dessus du sol de son abscisse — sauf celles qui sont
        // posées sur d'autres, évidemment, d'où la seule borne basse.
        for (seed in 1L..30L) {
            val lvl = TargetGenerator.generate(seed)
            var pire = 0f
            for (b in lvl.structure.blocks) {
                val sous = b.bottom() - lvl.terrain.heightAt(b.x)
                if (sous < pire) pire = sous
            }
            assertTrue(
                "graine $seed (${lvl.shape}) : une pierre est enterrée de " +
                    "${"%.2f".format(-pire)} m",
                pire > -0.35f
            )
        }
    }

    @Test
    fun `le tablier de la machine reste plat`() {
        // Le relief a le droit de tout faire devant la machine, sauf de lui passer sous
        // les pieds. Le bâti, le contrepoids et la fronde traînée au sol supposent tous
        // un sol à zéro sous le pivot, et ils ont raison de le supposer.
        for (seed in 1L..40L) {
            val lvl = TargetGenerator.generate(seed)
            var x = TrebuchetRules.GROUND_LEFT
            while (x < TrebuchetRules.FIRING_LINE + 30f) {
                assertEquals(
                    "graine $seed : le sol n'est pas à zéro en x=$x",
                    0f, lvl.terrain.heightAt(x), 1e-4f
                )
                x += 5f
            }
        }
    }

    @Test
    fun `une meme graine redonne le meme relief`() {
        val a = TargetGenerator.generate(91L)
        val b = TargetGenerator.generate(91L)
        assertEquals(a.shape, b.shape)
        assertEquals(a.terrain.nodes.size, b.terrain.nodes.size)
        for (i in a.terrain.nodes.indices) {
            assertEquals(a.terrain.nodes[i].x, b.terrain.nodes[i].x, 1e-4f)
            assertEquals(a.terrain.nodes[i].y, b.terrain.nodes[i].y, 1e-4f)
        }
    }

    @Test
    fun `les reliefs restent lisibles et abordables`() {
        // Un relevé, plus qu'un test : il imprime ce que chaque graine donne, et il
        // n'échoue que sur les deux bornes qui rendraient un niveau injouable — un site
        // plus haut que ce que la machine sait cadrer, ou un relief découpé en tant de
        // dalles qu'il coûterait plus cher que la construction qu'il porte.
        val vus = HashSet<TerrainShape>()
        for (seed in 1L..30L) {
            val lvl = TargetGenerator.generate(seed)
            vus += lvl.shape
            val dalles = lvl.terrain.bodies().size
            println(
                "RELIEF $seed ${TargetGenerator.label(lvl)} : " +
                    "${"%.1f".format(lvl.terrain.lowest)}..${"%.1f".format(lvl.terrain.highest)} m, " +
                    "$dalles dalles, ${lvl.structure.blocks.size} corps, " +
                    "site ${"%.0f".format(lvl.structure.width)} m de front"
            )
            assertTrue(
                "graine $seed : relief de ${lvl.terrain.highest} m de haut",
                lvl.terrain.highest <= 40f
            )
            assertTrue("graine $seed : relief de ${lvl.terrain.lowest} m", lvl.terrain.lowest >= -20f)
            assertTrue("graine $seed : $dalles dalles de terrain", dalles <= 24)
        }
        println("RELIEFS rencontrés en 30 graines : ${vus.sortedBy { it.name }}")
        assertTrue("un seul relief sur 30 graines : $vus", vus.size >= 4)
    }

    @Test
    fun `un plateau porte ce qu on lui demande de porter`() {
        // Le contrat entre le plan et le relief : on demande des emprises, on reçoit des
        // plateaux au moins aussi larges, tous plats, et rangés de gauche à droite.
        val rng = Random(7)
        for (shape in TerrainShape.entries) {
            val emprises = listOf(12f, 8f, 15f)
            val plan = Terrain.plan(rng, shape, emprises, 90f)
            assertEquals("$shape : mauvais nombre de plateaux", emprises.size, plan.pads.size)
            var precedent = -Float.MAX_VALUE
            for ((i, pad) in plan.pads.withIndex()) {
                assertTrue("$shape : plateau $i trop étroit", pad.width >= emprises[i] - 1e-3f)
                assertTrue("$shape : plateaux dans le désordre", pad.left > precedent)
                precedent = pad.right
                // Plat d'un bout à l'autre : c'est toute la raison d'être d'un plateau.
                var x = pad.left
                while (x <= pad.right) {
                    assertEquals(
                        "$shape : le plateau $i n'est pas plat en x=$x",
                        pad.y, plan.terrain.heightAt(x), 1e-3f
                    )
                    x += 0.5f
                }
            }
        }
    }

    @Test
    fun `un niveau en relief tient debout dans le monde de la machine`() {
        // Bout en bout : on charge, on ne tire pas, et rien ne doit bouger. C'est le
        // test qui remplace « lancer l'appli pour voir » — sur un terrain plat il
        // passait déjà, et c'est le relief qu'on ajoute qui pourrait le casser.
        for (seed in longArrayOf(4L, 8L, 11L, 13L, 15L)) {
            val g = TrebuchetGame()
            g.loadLevel(seed)
            val hauteur = g.targets.ruinHeight()
            repeat(180) { g.step(1f / 60f) }
            println(
                "TENUE $seed ${TargetGenerator.label(g.level!!)} : " +
                    "crête ${"%.2f".format(hauteur)} -> ${"%.2f".format(g.targets.ruinHeight())}, " +
                    "abîmé ${"%.1f".format(g.targets.brokenRatio * 100)}%, " +
                    "renversées ${g.targets.pieceToppled}/${g.targets.pieces.size}"
            )
            assertEquals(
                "graine $seed : le site s'est abîmé tout seul",
                0f, g.targets.brokenRatio, 1e-4f
            )
            assertTrue(
                "graine $seed : le site s'est affaissé, ${g.targets.ruinHeight()} pour $hauteur",
                g.targets.ruinHeight() > hauteur - 1f
            )
        }
    }
}
