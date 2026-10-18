package com.Atom2Universe.app.games.trebuchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Le vent : d'où il vient, ce qu'il pousse, et ce qu'il ne pousse pas.
 *
 * Trois promesses, dont deux sont des pièges classiques. Un vent tiré au sort doit
 * rester **une donnée du niveau** — même graine, même vent — sans quoi rejouer un site
 * ne veut plus rien dire. Et un vent qui souffle dans le moteur doit pousser ce qui a
 * une prise sur l'air, et **rien d'autre** : le jour où un château se met à glisser
 * sous la brise, personne ne comprendra d'où ça vient.
 */
class TrebuchetWindTest {

    @Test
    fun `un vent fort reste horizontal`() {
        var pireForce = 0f
        var pirePente = 0f
        for (seed in 1L..400L) {
            val w = Wind.forSeed(seed)
            // La pente, en degrés depuis l'horizontale, quel que soit le côté d'où il
            // souffle : un vent d'ouest à 170° est aussi plat qu'un vent d'est à 10°.
            val pente = Math.toDegrees(kotlin.math.asin(abs(sin(w.angle)).toDouble())).toFloat()
            if (w.speed > Wind.MAX_SPEED * 0.5f && pente > pirePente) {
                pirePente = pente
                pireForce = w.speed
            }
            assertTrue("un vent dépasse l'échelle : ${w.speed}", w.speed <= Wind.MAX_SPEED)
            assertTrue("un vent penche trop : $pente°", pente <= Wind.MAX_TILT_DEG + 0.01f)
        }
        println(
            "VENT sur 400 graines, la pire pente d'un vent fort : " +
                "${"%.1f".format(pirePente)}° à ${"%.1f".format(pireForce)} m/s"
        )
        // Un vent fort est franchement horizontal : c'est ce qui rend la flèche lisible.
        assertTrue("un vent fort part à la verticale : $pirePente°", pirePente < 10f)
    }

    @Test
    fun `la meme graine donne le meme vent`() {
        for (seed in 1L..20L) {
            assertTrue(
                "la graine $seed ne redonne pas le même vent",
                Wind.forSeed(seed).sameAs(Wind.forSeed(seed))
            )
        }
        // Et deux graines voisines ne donnent pas le même : un vent qui ne change
        // jamais est un vent qu'on cesse de regarder.
        val vents = (1L..20L).map { Wind.forSeed(it).speed }
        println("VENT vingt premières graines : ${vents.map { "%.1f".format(it) }}")
        assertTrue("tous les niveaux ont le même vent", vents.toSet().size > 15)
        assertTrue("aucun niveau n'est calme", vents.any { it < 1f })
        assertTrue("aucun niveau n'est venteux", vents.any { it > 4f })
    }

    @Test
    fun `le vent porte le boulet et laisse le chateau tranquille`() {
        fun portee(w: Wind): Float {
            TargetRules.style = TargetStyle.JEU
            val g = TrebuchetGame()
            g.world.windX = w.vx
            g.world.windY = w.vy
            return g.simulateShot(1f / 120f)
        }

        val calme = portee(Wind.CALM)
        val arriere = portee(Wind(7f, 0f))
        val face = portee(Wind(7f, Math.PI.toFloat()))
        println(
            "VENT portée : calme ${"%.0f".format(calme)} m, " +
                "vent arrière ${"%.0f".format(arriere)} m, " +
                "vent de face ${"%.0f".format(face)} m"
        )
        assertTrue("le vent arrière ne porte pas plus loin", arriere > calme + 5f)
        assertTrue("le vent de face ne raccourcit pas le tir", face < calme - 5f)
    }

    /**
     * Ce qu'on mesure ici est une **dérive**, et il faut donc suivre chaque pierre et
     * pas la place qu'elle occupait dans une liste : une pierre qui casse change tout
     * l'ordre, et la comparaison rang par rang se met à mesurer la distance entre deux
     * pierres différentes.
     */
    private fun derive(g: TrebuchetGame, vent: Float, seconds: Float, tirer: Boolean = true): Float {
        g.world.windX = vent
        g.world.windY = 0f
        val depart = g.targets.pieces.map { it.body to it.body.x }
        if (tirer) g.release()
        repeat((seconds * 60).toInt()) { g.step(1f / 60f) }
        var pire = 0f
        for ((corps, x0) in depart) pire = maxOf(pire, abs(corps.x - x0))
        return pire
    }

    @Test
    fun `un chateau ne s envole pas`() {
        TargetRules.style = TargetStyle.JEU
        val g = TrebuchetGame()
        g.loadLevel(15L)
        // On force le pire vent possible, bien au-delà de ce que le jeu tire — et on ne
        // tire justement pas : ce test isole l'effet du vent sur la construction, il ne
        // doit pas dépendre de savoir si un boulet, poussé assez loin par une tempête
        // hors barème, finit par atteindre une cible dont la distance n'a rien de
        // spécial. Un vrai impact est un test à lui tout seul (`un boulet qui arrive sur
        // la cible l abime`), pas un accident de tempête.
        val pire = derive(g, 40f, 10f, tirer = false)
        println("VENT château dans une tempête de 40 m/s : dérive maximale ${"%.2f".format(pire)} m")
        assertTrue("le château a pris le vent : $pire m", pire < 1f)
        assertEquals("le château s'est abîmé tout seul", 0f, g.targets.brokenRatio, 1e-4f)
    }

    @Test
    fun `un village de bois tient dans le vent que le jeu tire`() {
        // La tempête de quarante mètres par seconde ci-dessus est un test de maçonnerie :
        // elle vaut vingt fois la poussée du vent le plus fort que le jeu tire, et une
        // charpente d'arcade — quatorze fois plus légère que le bois réel — s'envole
        // pour de bon dedans. Ce qui doit tenir, c'est le vent **du jeu**, et c'est ce
        // village-ci qui le vérifie : palissades, granges et toits de chaume compris.
        //
        // **Et on ne tire pas**, pour la même raison que le château ci-dessus. Ce test
        // tirait à l'origine, et le boulet n'atteignait pas la cible ; depuis que les
        // villages font trois cents mètres de front, le même tir poussé par un vent
        // arrière retombe **dedans** et casse 1,5 % du site. La mesure ne disait donc
        // plus rien du vent, elle mesurait un impact.
        TargetRules.style = TargetStyle.JEU
        val g = TrebuchetGame()
        g.loadLevel(4L)
        val pire = derive(g, Wind.MAX_SPEED, 10f, tirer = false)
        println("VENT village à ${Wind.MAX_SPEED} m/s : dérive maximale ${"%.2f".format(pire)} m")
        assertTrue("le village a pris le vent : $pire m", pire < 1f)
        assertEquals("le village s'est abîmé tout seul", 0f, g.targets.brokenRatio, 1e-4f)
    }
    @Test
    fun `le vent emporte la fumee`() {
        val calme = TrebuchetEffects(4L)
        calme.explosion(0f, 10f, 8f)
        val venteux = TrebuchetEffects(4L)
        venteux.setWind(8f, 0f)
        venteux.explosion(0f, 10f, 8f)
        repeat(120) {
            calme.update(1f / 60f)
            venteux.update(1f / 60f)
        }
        fun centre(fx: TrebuchetEffects): Float {
            val f = fx.sparks.filter { it.alive && it.kind == Puff.SMOKE }
            return if (f.isEmpty()) 0f else f.map { it.x }.average().toFloat()
        }
        val a = centre(calme)
        val b = centre(venteux)
        println("FUMÉE centre du panache : calme ${"%.1f".format(a)} m, vent ${"%.1f".format(b)} m")
        assertTrue("le vent n'emporte pas la fumée : $a puis $b", b > a + 5f)
    }

    @Test
    fun `le niveau porte son vent`() {
        TargetRules.style = TargetStyle.JEU
        val g = TrebuchetGame()
        g.loadLevel(6L)
        val attendu = Wind.forSeed(6L)
        assertTrue("le niveau n'a pas le vent de sa graine", g.wind.sameAs(attendu))
        assertEquals("le monde ignore le vent du niveau", attendu.vx, g.world.windX, 1e-4f)
        // Et régler la machine ne l'emporte pas : le vent souffle sur le site, pas sur
        // la poutre.
        g.setBeamLength(g.config.beamLength + 1f)
        assertEquals("remonter la machine a chassé le vent", attendu.vx, g.world.windX, 1e-4f)
    }
}
