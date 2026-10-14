package com.Atom2Universe.app.games.hamlet

import org.junit.Test

/** Sonde d'économie : d'où viennent les pièces du joueur simulé, par catégorie de bâtiment et par tranche de jours. */
class HamletIncomeBenchTest {
    @Test fun incomeBySource() {
        val rhythm = listOf(Rhythm.PASSIONATE, Rhythm.REGULAR, Rhythm.CASUAL, Rhythm.MINIMAL).first { it.name == (System.getProperty("hamlet.rhythm") ?: "passionne") }
        val days = (System.getProperty("hamlet.days") ?: "120").toInt()
        val bot = HamletBot(rhythm, 1)
        val buckets = sortedMapOf<Int, HashMap<String, Long>>()
        val start = bot.clock.t
        bot.g.listener = object : HamletListener {
            override fun onReward(o: Obj?, coins: Int, xp: Int, gems: Int) {
                val bucket = ((bot.clock.t - start) / DAY / 20).toInt() * 20
                val key = o?.def?.cat?.name ?: "autre"
                buckets.getOrPut(bucket) { HashMap() }.merge(key, coins.toLong(), Long::plus)
            }
            override fun onLevelUp(level: Int, gems: Int) = Unit
            override fun onMessage(code: String, arg: String) = Unit
            override fun onChanged() = Unit
        }
        bot.run(days, 5.0)
        for ((day, map) in buckets) {
            val total = map.values.sum().coerceAtLeast(1)
            println("jours $day-${day + 19} : total ${total / 1000} k · " + map.entries.sortedByDescending { it.value }.joinToString(" ") { "${it.key}=${it.value * 100 / total}%" })
        }
    }
}
