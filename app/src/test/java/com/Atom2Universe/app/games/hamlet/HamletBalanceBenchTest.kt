package com.Atom2Universe.app.games.hamlet

import java.io.File
import org.junit.Test
import org.junit.Assert.*

/** Opt-in balance measurements, using the real game and paid actions, never an injected stock. */
class HamletBalanceBenchTest {
    private val out = File("build/reports/hamlet-balance").also { it.mkdirs() }

    @Test fun progression() {
        val continuous = Rhythm("continu", listOf(0 to 1440))
        val rows = mutableListOf("profile,seed,days,active_hours,level,hall,chapter,frontier,pulls,deliveries,work_used,outfits,craft_trades")
        for ((rhythm, farm, speed) in listOf(Triple(Rhythm.REGULAR, true, 1.4), Triple(Rhythm.PASSIONATE, true, 1.4),
            Triple(continuous, true, 0.7), Triple(Rhythm("passionne_sans_farm", Rhythm.PASSIONATE.sessions), false, 1.4))) for (seed in 1..3) {
            val bot = HamletBot(rhythm, seed, farming = farm, actionSeconds = speed)
            bot.run(2, .25)
            val g = bot.g
            rows += listOf(rhythm.name, seed, 2, bot.activeSeconds / 3600, g.level(), g.hall, g.journey.chapter,
                g.adventure.frontier, g.adventure.recruitmentPulls, g.stats["delivered"] ?: 0, g.stats["work_used"] ?: 0,
                bot.outfits, bot.craftTrades).joinToString(",")
            File(out, "${rhythm.name}_$seed.csv").writeText(bot.timeline.toString())
            println(rows.last()); println(bot.chapterReport())
            println("bulk_work_trades=${bot.bulkWorkTrades} goods=" + AdventureResource.entries.associateWith { g.adventure[it] })
            assertTrue("${rhythm.name}/$seed finished chapters in 48h", g.journey.chapter < HamletJourney.chapters.size)
            assertTrue("${rhythm.name}/$seed reached final village level in 48h", g.level() < Hamlet.MAX_LEVEL)
            if (farm && rhythm.minutesPerDay >= Rhythm.PASSIONATE.minutesPerDay) {
                assertTrue("Expedition outfit chain was not exercised", bot.outfits > 0)
                assertTrue("Artisan caravan chain was not repeated", bot.craftTrades >= 2)
            }
        }
        File(out, "progression.csv").writeText(rows.joinToString("\n"))
    }

    @Test fun sustainedProgress() {
        val rows = mutableListOf("profile,days,active_hours,level,hall,chapter,frontier,outfits,craft_trades")
        for ((rhythm, days) in listOf(Rhythm.PASSIONATE to 7, Rhythm.PASSIONATE to 30)) {
            val bot = HamletBot(rhythm, 1, farming = true)
            bot.run(days, 1.0)
            rows += listOf(rhythm.name, days, bot.activeSeconds / 3600, bot.g.level(), bot.g.hall,
                bot.g.journey.chapter, bot.a.frontier, bot.outfits, bot.craftTrades).joinToString(",")
            File(out, "${rhythm.name}_${days}days.csv").writeText(bot.timeline.toString())
            println(rows.last()); println(bot.chapterReport()); println(bot.legendReport())
            assertTrue("No sustained progression", bot.g.journey.chapter >= if (days >= 30) 9 else 6)
            assertTrue("Artisan route stopped working", bot.craftTrades >= days)
        }
        File(out, "sustained.csv").writeText(rows.joinToString("\n"))
    }

    @Test fun recipeRates() {
        val rows = mutableListOf("building,level,hours,coins_per_hour,output_per_hour,ingredients")
        for (d in Hamlet.defs.filter { it.cat == Cat.SHOP }) {
            val r = HamletIndustry.recipe(d.id, 1)
            val hours = d.job / 3600.0
            rows += listOf(d.id, d.lvl, hours, (d.pay - r.cost.coins) / hours,
                r.output.entries.joinToString(";") { "${it.key}:${it.value / hours}" },
                (r.cost.resources.entries.map { "${it.key}:${it.value}" } + r.cost.crops.entries.map { "${it.key}:${it.value}" }).joinToString(";")).joinToString(",")
        }
        File(out, "recipes.csv").writeText(rows.joinToString("\n"))
    }
}
