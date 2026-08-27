package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Compare les deux tempéraments de front : réaliste contre arcade.
 *
 * Ce n'est pas un test de plus, c'est **la** justification du mode arcade. On y vérifie
 * la promesse : un coup franc emporte une pierre entière au lieu de la fêler, les
 * pierres sont assez légères pour être renversées, et une construction coûte beaucoup
 * moins de corps au moteur.
 */
class TrebuchetStyleTest {

    @After
    fun rendLeMode() {
        TargetRules.style = TargetStyle.ARCADE
    }

    private fun world(): PhysWorld = PhysWorld().apply {
        iterations = 16
        add(
            PhysBody(400f, 1f, 0f).apply {
                x = 0f; y = -1f
                lockPosition = true; lockRotation = true
                friction = 0.7f
                category = TrebuchetCategory.GROUND
                refreshMass()
            }
        )
    }

    private fun PhysWorld.fireBall(x: Float, y: Float, speed: Float): PhysBody {
        val b = PhysBody.circle(TrebuchetRules.BALL_RADIUS, TrebuchetRules.BALL_MASS).apply {
            this.x = x; this.y = y
            vx = speed
            friction = 0.2f
            restitution = 0.1f
            category = TrebuchetCategory.BALL
            collidesWith = TrebuchetCategory.BALL_FREE_MASK
        }
        add(b)
        return b
    }

    /** Tire un boulet dans le pied d'une courtine et rend ce qu'il en reste. */
    private fun shootWall(style: TargetStyle): Triple<Int, Float, Float> {
        TargetRules.style = style
        val s = TargetField.settle(
            Structure(TargetModules.curtainWall(Random(5), 20f, 10f, 8f), "courtine")
        )
        val w = world()
        val f = TargetField(w)
        f.load(s)
        repeat(90) { w.stepFrame(1f / 60f); f.update(1f / 60f) }
        val avant = f.ruinHeight()

        w.fireBall(10f, 1.2f, 130f)
        repeat(300) { w.stepFrame(1f / 60f); f.update(1f / 60f) }

        return Triple(s.blocks.size, f.brokenRatio, avant - f.ruinHeight())
    }

    @Test
    fun `l arcade emporte des morceaux la ou le realiste egratigne`() {
        val (corpsR, casseR, chuteR) = shootWall(TargetStyle.REALISTE)
        val (corpsA, casseA, chuteA) = shootWall(TargetStyle.ARCADE)

        println(
            "STYLE réaliste : $corpsR corps, un coup détruit ${"%.1f".format(casseR * 100)}%, " +
                "la crête descend de ${"%.2f".format(chuteR)} m"
        )
        println(
            "STYLE arcade   : $corpsA corps, un coup détruit ${"%.1f".format(casseA * 100)}%, " +
                "la crête descend de ${"%.2f".format(chuteA)} m"
        )

        assertTrue(
            "l'arcade ne coûte pas moins de corps : $corpsA contre $corpsR",
            corpsA < corpsR
        )
        assertTrue(
            "un coup d'arcade ne fait pas plus de dégâts qu'en réaliste : $casseA contre $casseR",
            casseA > casseR
        )
    }

    @Test
    fun `une pierre d arcade se casse d un coup et se laisse pousser`() {
        TargetRules.style = TargetStyle.ARCADE
        // La pierre par défaut d'une courtine d'arcade : 2,40 x 1,00 m.
        val pierre = Block.laid(Material.STONE, 20f, 0f, 2.4f, 1f)
        val energie = 0.5f * TrebuchetRules.BALL_MASS * 130f * 130f
        println(
            "ARCADE pierre de ${pierre.mass.toInt()} kg, ${(pierre.hp / 1000).toInt()} kJ de vie, " +
                "boulet à 130 m/s = ${(energie / 1000).toInt()} kJ, " +
                "poussée = ${"%.2f".format(TrebuchetRules.BALL_MASS * 130f / pierre.mass)} m/s"
        )
        assertTrue(
            "une pierre d'arcade encaisse plus qu'un coup franc : ${pierre.hp}",
            pierre.hp < energie
        )
        // Et elle doit rester assez lourde pour tomber sur ses voisines au lieu de
        // s'envoler comme un fétu.
        assertTrue("une pierre d'arcade est devenue une plume", pierre.mass > 1200f)
    }

    @Test
    fun `un site d arcade coute bien moins de corps`() {
        TargetRules.style = TargetStyle.REALISTE
        val realiste = TargetGenerator.generate(8L).structure
        TargetRules.style = TargetStyle.ARCADE
        val arcade = TargetGenerator.generate(8L).structure

        println(
            "SITE château : réaliste ${realiste.blocks.size} corps / " +
                "${(realiste.totalMass / 1000).toInt()} t, " +
                "arcade ${arcade.blocks.size} corps / ${(arcade.totalMass / 1000).toInt()} t"
        )
        assertTrue(
            "l'arcade ne simplifie pas le site : ${arcade.blocks.size} contre ${realiste.blocks.size}",
            arcade.blocks.size < realiste.blocks.size
        )
        assertTrue("le site d'arcade est vide", arcade.blocks.size > 8)
        assertTrue(
            "le site d'arcade s'est mis à flotter",
            arcade.problems().isEmpty()
        )
    }
}
