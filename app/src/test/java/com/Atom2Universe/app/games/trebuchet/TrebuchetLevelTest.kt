package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
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
        val arcade = (1L..40L).map { TargetGenerator.kindFor(it, TargetStyle.JEU) }.toSet()
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
            TargetRules.style = TargetStyle.JEU
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

    /**
     * Les villes : ce qu'on leur demande est une **densité**, et une densité se mesure.
     *
     * Un village pose un bâtiment par plateau, et le relief réserve à chaque plateau une
     * marge plate de chaque côté plus un entre-deux : deux bâtiments voisins sont donc
     * séparés d'une vingtaine de mètres de terrain nu. Une ville tient tout entière dans
     * **un seul groupe**, donc sur un seul plateau, et ses immeubles ne sont plus
     * séparés que par le `gap` du générateur. Ce test mesure exactement ça : le plus
     * grand vide entre deux bâtiments voisins.
     */
    @Test
    fun `une ville est dense, haute et dans le budget`() {
        val precedent = TargetRules.style
        try {
            TargetRules.style = TargetStyle.JEU
            var vues = 0
            for (seed in 1L..60L) {
                val kind = TargetGenerator.kindFor(seed, TargetStyle.JEU)
                if (kind != SiteKind.VILLE && kind != SiteKind.METROPOLE) continue
                vues++
                val lvl = TargetGenerator.generate(seed)
                val s = lvl.structure

                // Le plus grand vide de la façade : on balaie l'emprise et on mesure la
                // plus longue tranche que ne traverse aucun bloc.
                val pas = 2f
                var vide = 0f
                var pire = 0f
                var x = s.left
                while (x < s.right) {
                    val occupe = s.blocks.any { kotlin.math.abs(it.x - x) <= it.halfSpan() }
                    vide = if (occupe) 0f else vide + pas
                    if (vide > pire) pire = vide
                    x += pas
                }
                println(
                    "VILLE graine $seed ${TargetGenerator.label(lvl)} : ${s.blocks.size} corps, " +
                        "${"%.0f".format(s.baseHeight)} m de haut sur ${"%.0f".format(s.width)} m, " +
                        "plus grand vide ${"%.0f".format(pire)} m, pile ${s.deepestStack()}"
                )
                assertTrue("graine $seed : ${s.blocks.size} corps", s.blocks.size <= TargetRules.BODY_BUDGET)
                assertTrue(
                    "graine $seed : pile de ${s.deepestStack()}",
                    s.deepestStack() <= TargetRules.MAX_STACKED_BODIES
                )
                assertTrue("graine $seed : ville trop basse (${s.baseHeight} m)", s.baseHeight > 40f)
                assertTrue("graine $seed : ville trop courte (${s.width} m)", s.width > 120f)
                assertTrue(
                    "graine $seed : la ville a un trou de ${"%.0f".format(pire)} m — " +
                        "ce n'est plus une rue, c'est un terrain vague",
                    pire < 12f
                )
            }
            assertTrue("aucune ville dans les soixante premières graines", vues >= 4)
        } finally {
            TargetRules.style = precedent
        }
    }

    /**
     * L'objectif de tirs et la récompense disent la même chose du même site.
     *
     * Les deux sortent de [TargetGenerator.difficulty], et c'est tout le sujet : un site
     * qui demande plus de coups doit payer davantage. S'ils calculaient chacun leur
     * difficulté, ils finiraient par se contredire, et le joueur trouverait un site qui
     * paie comme un facile en se jouant comme un difficile.
     */
    @Test
    fun `un site plus dur demande plus de tirs et paie davantage`() {
        val precedent = TargetRules.style
        try {
            TargetRules.style = TargetStyle.JEU
            val hameau = TargetGenerator.generate(1L).structure
            val chateau = TargetGenerator.generate(15L).structure
            val metropole = TargetGenerator.generate(19L).structure
            for ((nom, s) in listOf("hameau" to hameau, "château" to chateau, "métropole" to metropole)) {
                println(
                    "OBJECTIF $nom : difficulté ${"%.2f".format(TargetGenerator.difficulty(s))}, " +
                        "${TargetGenerator.par(s)} tirs touchés, " +
                        "${NeutrinoRewards.trebuchet(TargetGenerator.difficulty(s))} neutrinos"
                )
            }
            assertTrue(
                "le hameau n'est pas le plus facile",
                TargetGenerator.difficulty(hameau) < TargetGenerator.difficulty(chateau)
            )
            assertTrue(
                "la métropole n'est pas la plus dure",
                TargetGenerator.difficulty(chateau) < TargetGenerator.difficulty(metropole)
            )
            // L'objectif et la récompense montent ensemble, jamais l'un sans l'autre.
            assertTrue(
                "l'objectif du hameau n'est pas plus court que celui de la métropole",
                TargetGenerator.par(hameau) < TargetGenerator.par(metropole)
            )
            assertTrue(
                "le hameau ne paie pas moins que la métropole",
                NeutrinoRewards.trebuchet(TargetGenerator.difficulty(hameau)) <
                    NeutrinoRewards.trebuchet(TargetGenerator.difficulty(metropole))
            )
            // Et l'objectif reste atteignable : le banc rase un hameau en quatre tirs
            // touchés et une métropole en dix.
            assertTrue("objectif de hameau hors barème", TargetGenerator.par(hameau) in 3..7)
            assertTrue("objectif de métropole hors barème", TargetGenerator.par(metropole) in 9..15)
        } finally {
            TargetRules.style = precedent
        }
    }

    @Test
    fun `les villes existent dans les deux temperaments`() {
        val arcade = (1L..40L).map { TargetGenerator.kindFor(it, TargetStyle.JEU) }.toSet()
        val realiste = (1L..40L).map { TargetGenerator.kindFor(it, TargetStyle.REALISTE) }.toSet()
        for (kind in listOf(SiteKind.VILLE, SiteKind.METROPOLE)) {
            assertTrue("$kind manque en arcade", kind in arcade)
            assertTrue("$kind manque en réaliste", kind in realiste)
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
    /**
     * **Le barème du jeu : combien de boulets bien placés pour raser un site.**
     *
     * C'est le seul test qui mesure l'équilibre plutôt qu'une propriété, et il vaut
     * tous les autres réunis pour ça : rien d'autre ne dirait qu'un réglage a rendu le
     * jeu trivial ou impossible. On modélise un joueur qui vise correctement — à chaque
     * tir, le boulet part sur la pièce debout la plus à gauche, au tiers de sa hauteur —
     * et on compte les coups.
     *
     * Le barème mesuré à l'écriture de ce test : hameau 4 coups, moulin 6, village 10,
     * château 10, centre-ville 15, métropole 10.
     *
     * Et surtout, **aucun site ne tombe au premier boulet**. C'était le cas quand la
     * traversée était gratuite : le boulet ne payait que les points de vie de ce qu'il
     * cassait, le bois n'en a presque pas, et un seul tir enfilait les vingt-six corps
     * d'un hameau comme une boule de bowling.
     *
     * Deux fausses pistes valent d'être écrites, parce qu'elles reviendraient sinon.
     * **Le curseur de solidité n'était pas la parade** : en quadruplant les points de
     * vie, le hameau tombait toujours en un coup pendant que la métropole devenait
     * inrasable. Et **une traversée tout ou rien** — plein si elle est méritée, zéro
     * sinon — remettait bien le jeu à niveau mais aplatissait le barème à 7-14 coups
     * partout : le nombre de tirs ne dépendait plus de ce que le site oppose, mais du
     * nombre de couches à fêler, lequel est le même partout. D'où
     * [TargetRules.PIERCE_UNEARNED], qui rend la pente.
     */
    @Test
    fun `un site ne tombe jamais au premier boulet, et tombe en une douzaine`() {
        // Deux classes, et des bornes larges : ce test garde une **forme de courbe**,
        // pas un chiffre. Un site qui tombe en deux coups ou qui résiste à vingt est un
        // défaut ; qu'un château demande neuf tirs plutôt que onze ne regarde personne.
        val petits = 3..9
        val gros = 6..18
        val bornes = mapOf(
            1L to petits,      // hameau
            6L to petits,      // moulin
            4L to gros,        // village
            15L to gros,       // château
            18L to gros,       // centre-ville
            19L to gros        // métropole
        )
        // On mesure **tout** avant de juger : un barème dont la première ligne échoue
        // n'apprendrait rien sur les cinq autres, et c'est justement la forme de la
        // courbe entière qu'on veut voir dans le rapport d'échec.
        val fautes = ArrayList<String>()
        for ((seed, attendu) in bornes) {
            val g = TrebuchetGame()
            g.loadLevel(seed)
            val f = g.targets
            val nom = TargetGenerator.label(g.level!!)
            var coups = 0
            val courbe = StringBuilder()
            while (coups < 20 && f.pieces.isNotEmpty() && !f.cleared) {
                if (!tirVise(g)) break
                coups++
                courbe.append(" ${(f.progress * 100).toInt()}%")
            }
            println("BARÈME graine $seed $nom : ${if (f.cleared) "$coups coups" else "> 20 coups"} —$courbe")
            if (!f.cleared) fautes += "graine $seed ($nom) n'a pas été rasé en vingt coups"
            else if (coups !in attendu) fautes += "graine $seed ($nom) : $coups coups, attendu $attendu"
        }
        assertTrue(fautes.joinToString(" ; "), fautes.isEmpty())
    }

    /**
     * Un tir de joueur qui vise : le boulet part sur la pièce debout la plus à gauche,
     * au tiers de sa hauteur, à cent cinquante mètres par seconde.
     *
     * On le pose en vol plutôt que de régler une machine : ce banc mesure la solidité
     * des constructions, pas la balistique, et une machine mal réglée mesurerait la
     * machine.
     */
    private fun tirVise(g: TrebuchetGame): Boolean {
        val f = g.targets
        val cible = f.pieces.filter { !it.debris }.minByOrNull { it.body.x } ?: return false
        g.release()
        var garde = 0
        while (!g.ballFree && garde < 600) {
            g.step(1f / 60f)
            garde++
        }
        if (!g.ballFree) return false
        val sol = f.terrain.heightAt(cible.body.x)
        g.ball.x = cible.body.x - 6f
        g.ball.y = sol + (cible.body.topY() - sol) * 0.33f + 0.5f
        g.ball.vx = 150f
        g.ball.vy = 0f
        g.world.forgetContacts(g.ball)
        var t = 0f
        while (g.phase == TrebuchetGame.Phase.FLIGHT && t < 40f) {
            g.step(1f / 60f)
            t += 1f / 60f
        }
        g.rebuild()
        return true
    }
}
