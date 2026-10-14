package com.Atom2Universe.app.games.hamlet

import org.junit.Test

/**
 * Banc de l'économie du village : une base, une courbe de durée, et rien d'autre. Les tableaux servent à régler
 * `HamletEconomy` ; les promesses (court = 2 à 3 × la base, long = 0,5 à 1 ×, niveau de déblocage hors du prix)
 * sont gardées par `HamletEconomyTest`. Exclu des tests ordinaires.
 * ./gradlew testDebugUnitTest -PbancsMesure --tests "*HamletEconomyBenchTest*"
 */
class HamletEconomyBenchTest {
    @Test fun cropTable() {
        println("culture            niv  durée     gain  graine  xp   pièces/h  ×base")
        for (c in Hamlet.crops.filter { !it.magic }.sortedBy { it.time }) {
            val perHour = HamletEconomy.perHour(c) / HamletEconomy.BASE_PER_HOUR
            println("%-18s %3d %6ds %8d %7d %4d %9.0f %6.2f".format(c.id, c.lvl, c.time, c.earn, c.cost, c.xp, HamletEconomy.perHour(c), perHour))
        }
    }

    @Test fun upgradeLadder() {
        for (id in listOf("wheatfield", "cottage", "farm", "market")) {
            val def = Hamlet.byId[id] ?: continue
            val line = (2..HamletEconomy.MAX_TIER).joinToString("  ") { tier ->
                val needs = HamletEconomy.upgradeNeeds(def, tier).joinToString("+") { "${it.units}×${it.crop}" }
                "T$tier: ${HamletEconomy.upgradeCoins(def.cost, tier - 1)} pièces, $needs"
            }
            println("$id (niv ${def.lvl}, coût ${def.cost}) → $line")
        }
        println("grange : " + (1..8).joinToString(" ") { "halle$it=${HamletEconomy.pantryCap(it)}" })
    }
}
