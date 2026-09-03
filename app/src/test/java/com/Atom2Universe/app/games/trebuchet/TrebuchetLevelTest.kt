package com.Atom2Universe.app.games.trebuchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le niveau bout en bout : on charge une graine, la construction doit tenir debout
 * dans le monde de la machine, et un tir doit se terminer.
 *
 * C'est le test qui remplace « lancer l'appli pour voir » : tout ce qui casse ici
 * casserait sur le téléphone, en moins lisible.
 */
class TrebuchetLevelTest {

    private fun simulate(g: TrebuchetGame, seconds: Float, dt: Float = 1f / 60f) {
        repeat((seconds / dt).toInt()) { g.step(dt) }
    }

    @Test
    fun `les niveaux generes tiennent dans leurs bornes`() {
        for (seed in 1L..12L) {
            val lvl = TargetGenerator.generate(seed)
            val s = lvl.structure
            println(
                "NIVEAU $seed ${TargetGenerator.label(lvl)} : ${s.blocks.size} corps, " +
                    "pile ${s.deepestStack()}, ${"%.1f".format(s.baseHeight)} m de haut, " +
                    "${"%.1f".format(s.width)} m de front, " +
                    "${(s.totalMass / 1000f).toInt()} t"
            )
            assertTrue(
                "graine $seed : distance ${lvl.distance} hors bornes",
                lvl.distance in TargetGenerator.MIN_DISTANCE..TargetGenerator.MAX_DISTANCE
            )
            assertEquals(
                "graine $seed : la construction n'est pas posée à sa distance",
                lvl.distance, s.left, 0.01f
            )
            assertTrue("graine $seed : ${s.blocks.size} corps", s.blocks.size <= TargetRules.BODY_BUDGET)
            assertTrue(
                "graine $seed : pile de ${s.deepestStack()} corps",
                s.deepestStack() <= TargetRules.MAX_STACKED_BODIES
            )
            assertTrue("graine $seed : construction plate", s.baseHeight > 2f)
        }
    }

    @Test
    fun `une meme graine redonne le meme niveau`() {
        val a = TargetGenerator.generate(77L)
        val b = TargetGenerator.generate(77L)
        assertEquals(a.kind, b.kind)
        assertEquals(a.distance, b.distance, 1e-4f)
        assertEquals(a.structure.blocks.size, b.structure.blocks.size)
        for (i in a.structure.blocks.indices) {
            assertEquals(a.structure.blocks[i].x, b.structure.blocks[i].x, 1e-4f)
            assertEquals(a.structure.blocks[i].y, b.structure.blocks[i].y, 1e-4f)
        }
    }

    @Test
    fun `le chateau de cartes est reserve au mode arcade`() {
        val arcade = (1L..40L).map { TargetGenerator.kindFor(it, TargetStyle.ARCADE) }.toSet()
        val realiste = (1L..40L).map { TargetGenerator.kindFor(it, TargetStyle.REALISTE) }.toSet()
        assertTrue("le château de cartes a disparu du mode arcade", SiteKind.CHATEAU_CARTES in arcade)
        assertTrue("le château de cartes apparaît encore en réaliste", SiteKind.CHATEAU_CARTES !in realiste)
        assertTrue("la seigneurie n'apparaît jamais en arcade", SiteKind.SEIGNEURIE in arcade)
        assertTrue("la seigneurie n'apparaît jamais en réaliste", SiteKind.SEIGNEURIE in realiste)
    }

    @Test
    fun `une seigneurie raconte tour village puis petit chateau`() {
        val precedent = TargetRules.style
        try {
            TargetRules.style = TargetStyle.ARCADE
            // La nouvelle famille est ajoutée après les seize graines historiques :
            // la graine 17 la désigne sans changer les niveaux 1 à 16.
            val lvl = TargetGenerator.generate(17L)
            assertEquals(SiteKind.SEIGNEURIE, lvl.kind)
            val s = lvl.structure
            assertTrue("la seigneurie est trop courte : ${s.width} m", s.width > 100f)
            assertTrue("la tour de garde n'est pas assez haute : ${s.baseHeight} m", s.baseHeight > 60f)

            val firstLimit = s.left + s.width * 0.2f
            val villageLeft = s.left + s.width * 0.2f
            val villageRight = s.left + s.width * 0.72f
            val front = s.blocks.filter { it.x < firstLimit }
            val village = s.blocks.filter { it.x in villageLeft..villageRight }
            val castle = s.blocks.filter { it.x > villageRight }
            assertTrue("la grande tour n'est pas en première ligne", front.maxOf { it.top() } > 60f)
            assertTrue("le village central manque de maisons", village.count { it.material == Material.WOOD } >= 8)
            assertTrue("le petit château final manque de pierre", castle.count { it.material.masonry } >= 8)
        } finally {
            TargetRules.style = precedent
        }
    }

    @Test
    fun `un niveau charge tient debout a cote de la machine`() {
        val g = TrebuchetGame()
        g.loadLevel(3L)
        val h0 = g.targets.ruinHeight()
        assertTrue("aucune cible chargée", g.targets.pieces.isNotEmpty())

        // La machine est bandée, personne ne tire : rien ne doit bouger.
        g.release()
        simulate(g, 0.5f)
        g.rebuild()
        simulate(g, 0.2f)

        println(
            "CHARGE ${g.targets.pieces.size} corps, hauteur $h0 -> ${g.targets.ruinHeight()}, " +
                "détruit=${"%.1f".format(g.targets.brokenRatio * 100)}%"
        )
        assertEquals("la cible s'est abîmée toute seule", 0f, g.targets.brokenRatio, 1e-4f)
    }

    @Test
    fun `regler la machine ne reconstruit pas la cible`() {
        // Le joueur allonge sa poutre entre deux tirs : le château ne doit pas se
        // relever derrière lui.
        val g = TrebuchetGame()
        g.loadLevel(5L)
        val avant = g.targets.pieces.size
        g.targets.blast(g.targets.left + 3f, 2f, 3_000_000f, 25f)
        simulate(g, 0.1f)
        val apres = g.targets.pieces.size
        assertTrue("le souffle n'a rien fait : $avant -> $apres", apres != avant)

        g.setBeamLength(g.config.beamLength + 2f)
        assertEquals(
            "la cible s'est reconstruite quand on a réglé la machine",
            apres, g.targets.pieces.size
        )
        // Et les corps sont bien retournés dans le monde, sinon ils tomberaient hors
        // de la simulation sans que rien ne le dise.
        for (p in g.targets.pieces) {
            assertTrue("une pierre a été oubliée hors du monde", g.world.bodies.contains(p.body))
        }
    }

    @Test
    fun `un tir se termine et laisse la main au joueur`() {
        val g = TrebuchetGame()
        g.loadLevel(2L)
        g.release()
        var t = 0f
        while (g.phase == TrebuchetGame.Phase.FLIGHT && t < 40f) {
            g.step(1f / 60f)
            t += 1f / 60f
        }
        println(
            "TIR fin en ${"%.1f".format(t)} s, portée ${"%.0f".format(g.shotDistance)} m, " +
                "cible à ${"%.0f".format(g.level!!.distance)} m"
        )
        assertEquals("le tir ne s'est jamais terminé", TrebuchetGame.Phase.RESULT, g.phase)
        assertTrue("le tir n'a pas été mesuré", g.shotDistance > 0f)
    }

    @Test
    fun `un boulet qui arrive sur la cible l abime`() {
        // On ne cherche pas ici à régler une machine : on pose le boulet en vol au bon
        // endroit, ce qui teste tout le reste de la chaîne.
        val g = TrebuchetGame()
        g.loadLevel(4L)
        simulate(g, 0f)
        g.release()
        // On laisse la cible s'armer, puis on téléporte le boulet devant elle.
        repeat(90) { g.step(1f / 60f) }
        val cible = g.targets
        g.ball.x = cible.left - 8f
        g.ball.y = 1.6f
        g.ball.vx = 120f
        g.ball.vy = 0f
        g.world.forgetContacts(g.ball)
        repeat(240) { g.step(1f / 60f) }

        println(
            "IMPACT détruit=${"%.1f".format(cible.brokenRatio * 100)}% " +
                "progression=${"%.0f".format(cible.progress * 100)}% " +
                "fêlées=${cible.pieces.count { it.crackLevel > 0 }}"
        )
        assertTrue(
            "le boulet n'a rien fait à la cible",
            cible.brokenRatio > 0f || cible.pieces.any { it.crackLevel > 0 }
        )
    }
}
