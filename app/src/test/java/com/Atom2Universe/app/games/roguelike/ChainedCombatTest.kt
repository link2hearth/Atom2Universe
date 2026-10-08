package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Les combats enchaînés. Un monstre qui nous poursuit et qui est tout proche à la fin d'un
 * combat nous saute dessus sans nous laisser souffler (voir DONJON.md) — mais il doit
 * mourir pour de bon, comme le premier.
 *
 * Bug corrigé le 18/09/2026 : `finishCombat` rangeait le groupe vaincu **après** avoir
 * lancé l'enchaînement, donc la remise à zéro effaçait le groupe que l'enchaînement venait
 * de désigner. Le deuxième monstre n'était jamais marqué mort : il restait sur la carte,
 * collé au joueur et toujours en chasse, et le combat repartait sans fin.
 */
class ChainedCombatTest {

    /** Vide l'écran de butin s'il y en a, pour revenir à la carte. */
    private fun clearLoot(g: RoguelikeGame) {
        while (g.pendingLoot.isNotEmpty()) g.stashPendingDrop()
    }

    /** Tue tous les ennemis du combat en cours, sans laisser le héros mourir. */
    private fun win(g: RoguelikeGame, remainingHp: Int? = null) {
        val c = g.combat ?: error("aucun combat en cours")
        var guard = 0
        while (c.phase == CombatPhase.PLAYER_TURN || c.phase == CombatPhase.ENEMY_TURN) {
            assertTrue("le combat ne se termine pas", guard++ < 500)
            if (c.phase == CombatPhase.PLAYER_TURN) {
                c.hero.healFull()
                c.attack(c.aliveIndices().first(), Timing.PERFECT)
            } else {
                val (_, attackers) = c.startEnemyTurn()
                for (a in attackers) {
                    if (c.phase != CombatPhase.ENEMY_TURN) break
                    c.resolveStrike(a, Timing.PERFECT)
                }
                c.endEnemyTurn()
            }
        }
        assertEquals("le héros devait gagner", CombatPhase.VICTORY, c.phase)
        remainingHp?.let { g.hero.hp = it }
        g.finishCombat()
    }

    @Test
    fun unMonstreEnchaineMeurtAussi() {
        val g = RoguelikeGame(rng = Random(7))
        // Deux groupes collés au héros, tous deux à nos trousses : le premier au combat,
        // le second assez près pour enchaîner.
        g.level.packs.forEach { it.alive = false }
        val here = g.playerPos
        val first  = MonsterPack(listOf(MonsterType.RAT), Pos(here.x + 1, here.y))
        val second = MonsterPack(listOf(MonsterType.RAT), Pos(here.x, here.y + 1))
        for (p in listOf(first, second)) { p.state = PackState.CHASING; g.level.packs += p }

        var fights = 0
        g.tryMove(1, 0)                      // on fonce sur le premier
        while (g.combat != null) {
            assertTrue("le combat repart en boucle : $fights combats", fights++ < 10)
            win(g)
            clearLoot(g)
        }

        assertEquals("un combat, puis un seul enchaînement", 2, fights)
        assertTrue("les deux groupes doivent être morts", g.level.packs.none { it.alive })
        assertNull(g.combat)
        assertTrue("on doit pouvoir rejouer sur la carte", g.isExploring)
    }

    /**
     * Le cas qui cachait le bug en jeu : quand le premier combat lâche un objet, le
     * ramassage passe par un autre chemin (`stashPendingDrop`) qui, lui, enchaînait
     * correctement. La boucle n'apparaissait donc que quand le premier monstre ne lâchait
     * rien — quatre fois sur cinq.
     */
    @Test
    fun laBoucleNApparaitPasNonPlusAvecDuButin() {
        val g = RoguelikeGame(rng = Random(9))
        g.level.packs.forEach { it.alive = false }
        val here = g.playerPos
        val first  = MonsterPack(listOf(MonsterType.RAT), Pos(here.x + 1, here.y))
        val second = MonsterPack(listOf(MonsterType.RAT), Pos(here.x, here.y + 1))
        for (p in listOf(first, second)) { p.state = PackState.CHASING; g.level.packs += p }

        var fights = 0
        g.tryMove(1, 0)
        while (g.combat != null) {
            assertTrue("le combat repart en boucle : $fights combats", fights++ < 10)
            win(g)
            // On force du butin à ramasser après chaque victoire.
            g.pendingLoot.addLast(LootSystem.generate(1, 0, Random(3)))
            clearLoot(g)
        }
        assertEquals(2, fights)
        assertTrue(g.level.packs.none { it.alive })
    }
    @Test fun victoryHealsAfterLootButNeverBeforeAnIncomingFight() {
        for (chained in listOf(false, true)) for (withLoot in listOf(false, true)) {
            val g = RoguelikeGame(rng = Random(7), levelSeed = 7L)
            g.level.packs.forEach { it.alive = false }
            val here = g.playerPos
            val first = MonsterPack(listOf(MonsterType.RAT), Pos(here.x + 1, here.y))
            first.state = PackState.CHASING
            g.level.packs += first
            if (chained) {
                val next = MonsterPack(listOf(MonsterType.RAT), Pos(here.x, here.y + 1))
                next.state = PackState.CHASING
                g.level.packs += next
            }
            g.tryMove(1, 0)
            if (withLoot) g.pendingLoot.addLast(LootSystem.generate(1, 0, Random(3)))
            win(g, remainingHp = 7)
            if (g.pendingLoot.isNotEmpty()) assertEquals(7, g.hero.hp)
            clearLoot(g)
            if (chained) {
                assertTrue(g.combat != null)
                assertEquals("no healing between chained fights", 7, g.hero.hp)
                win(g, remainingHp = 3)
                clearLoot(g)
            }
            assertNull(g.combat)
            assertEquals("full recovery after the final victory", g.hero.maxHp, g.hero.hp)
        }
    }
}