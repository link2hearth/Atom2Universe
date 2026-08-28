package com.Atom2Universe.app.games.trebuchet

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fou de performance : une cible qui attend ne doit rien coûter.
 *
 * Le boulet file à cent cinquante mètres par seconde, ce qui force le moteur à découper
 * chaque image en une vingtaine de sous-pas. Si les quatre-vingts pierres d'un château
 * sont résolues à chacun de ces sous-pas alors qu'elles dorment à trois cents mètres, une
 * image de vol passe de 30 µs à 7 300 — c'est mesuré, et sur un téléphone ça donne un ou
 * deux tirs par seconde au lieu de soixante.
 *
 * On vérifie ici la **propriété** plutôt que le chrono, qui dépendrait de la machine :
 * pendant le vol, la cible doit être en veille, et elle doit se réveiller quand le boulet
 * approche. Les durées sont affichées à titre indicatif.
 */
class TrebuchetFlightCostTest {

    @Test
    fun `une cible lointaine dort pendant le vol`() {
        val g = TrebuchetGame()
        g.loadLevel(8L)          // un château : le pire cas
        g.release()

        val dt = 1f / 60f
        var total = 0L
        var frames = 0
        var dormantFrames = 0
        repeat(120) {
            if (g.phase != TrebuchetGame.Phase.FLIGHT) return@repeat
            val t0 = System.nanoTime()
            g.step(dt)
            total += System.nanoTime() - t0
            frames++
            if (g.targets.dormant) dormantFrames++
        }
        val corps = g.world.bodies.count { it.inWorld }
        println(
            "VOL château : $dormantFrames/$frames images en veille, " +
                "corps actifs=$corps, image moyenne=${total / maxOf(frames, 1) / 1000} µs"
        )
        assertTrue("la cible n'a jamais dormi pendant le vol", dormantFrames > frames / 2)
        assertTrue("des pierres tournent encore dans la simulation : $corps", corps < 12)
    }

    @Test
    fun `la cible se reveille avant que le boulet arrive`() {
        val g = TrebuchetGame()
        g.loadLevel(8L)
        g.release()
        // On attend que la boucle ait quitté le crochet : tant que le boulet est dans
        // la fronde, la corde le ramène et le téléporter ne servirait à rien.
        var t = 0
        while (!g.ballFree && t < 600) {
            g.step(1f / 60f)
            t++
        }
        assertTrue("le boulet n'a jamais été largué", g.ballFree)
        repeat(30) { g.step(1f / 60f) }
        assertTrue("la cible ne s'est pas endormie", g.targets.dormant)

        // On amène le boulet à l'entrée du champ de veille, comme le ferait un tir juste.
        g.ball.x = g.targets.left - TargetRules.WATCH_MARGIN + 2f
        g.ball.y = 3f
        g.ball.vx = 150f
        g.ball.vy = 0f
        g.world.forgetContacts(g.ball)
        g.step(1f / 60f)

        assertTrue("la cible ne s'est pas réveillée devant le boulet", !g.targets.dormant)
        // Et elle doit être debout dans la simulation, pas seulement marquée éveillée.
        assertTrue(
            "les pierres n'ont pas été remises dans le monde",
            g.targets.pieces.all { it.body.inWorld }
        )
    }

    /**
     * Le quart de seconde qui précède l'impact : celui que le joueur regarde.
     *
     * La mise en veille sort la cible de la simulation tant que rien n'approche, mais
     * elle la remet **quarante mètres avant le boulet**, soit un quart de seconde à la
     * vitesse d'un vrai tir. Si les pierres redevenaient actives à ce moment-là,
     * soixante-dix d'entre elles seraient résolues à chacun des trente-deux sous-pas
     * qu'impose le boulet, et l'image passerait de 200 µs à 7 300 pile au moment où le
     * joueur suit son coup des yeux. C'est le défaut qu'il a rapporté : « c'est fluide
     * au début, puis ça rame de plus en plus quand le boulet approche ».
     *
     * La cible rentre donc dans le monde **endormie**, et seul ce que le boulet touche
     * se réveille, de proche en proche.
     */
    @Test
    fun `la cible reste endormie jusqu au contact`() {
        val g = TrebuchetGame()
        g.loadLevel(8L)
        g.release()
        var t = 0
        while (!g.ballFree && t < 600) {
            g.step(1f / 60f)
            t++
        }
        repeat(30) { g.step(1f / 60f) }
        assertTrue("la cible ne s'est pas endormie", g.targets.dormant)

        // Le boulet arrive en visant la construction, à la vitesse d'un tir juste.
        val f = g.targets
        g.ball.x = f.left - TargetRules.WATCH_MARGIN + 2f
        g.ball.y = f.baseHeight * 0.5f
        g.ball.vx = 130f
        g.ball.vy = 0f
        g.world.forgetContacts(g.ball)
        g.ball.wake()

        val dt = 1f / 120f
        var approche = 0L
        var images = 0
        var actifs = 0
        var touche = false
        repeat(120) {
            if (g.phase != TrebuchetGame.Phase.FLIGHT) return@repeat
            val t0 = System.nanoTime()
            g.step(dt)
            val el = System.nanoTime() - t0
            if (f.pieces.any { !it.body.sleeping }) {
                touche = true
            } else if (!f.dormant) {
                approche += el
                images++
                val n = g.world.bodies.count { it.inWorld && !it.frozen }
                if (n > actifs) actifs = n
            }
        }

        assertTrue("le boulet n'a jamais atteint la construction", touche)
        assertTrue("la cible n'a jamais eu d'images d'approche", images > 3)
        println(
            "APPROCHE château : $images images entre le réveil et le choc, " +
                "corps actifs=$actifs, image moyenne=${approche / images / 1000} µs"
        )
        // Comme ailleurs dans ce fichier, on vérifie la **propriété** et non le chrono,
        // qui dépendrait de la machine : pendant l'approche, la construction entière
        // doit être hors du solveur.
        assertTrue("des pierres tournent dans le solveur avant le choc : $actifs corps", actifs < 12)
    }
}
