package com.Atom2Universe.app.games.cosmorun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Mesure le jeu avec des robots. Le rapport chiffré est écrit dans app/build/cosmo-sim/report.txt. */
class CosmoRunSimTest {
    private val runs = 120

    private fun median(values: List<Float>) = values.sorted()[values.size / 2]

    private fun report(name: String, results: List<RunResult>): String {
        val deaths = results.filter { it.cause != null }
        val byCause = deaths.groupingBy { it.cause }.eachCount().entries.joinToString(", ") { "${it.key}=${it.value}" }
        return "%-14s parties=%d  médiane=%4.0f m  p10=%4.0f m  p90=%4.0f m  morts=%d  [%s]  chocs/partie=%.1f  chaîne max médiane=%d  temps médian=%3.0f s".format(
            name, results.size, median(results.map { it.distance }),
            results.map { it.distance }.sorted()[results.size / 10], results.map { it.distance }.sorted()[results.size * 9 / 10],
            deaths.size, byCause, results.sumOf { it.stumbles } / results.size.toFloat(), results.map { it.maxChain }.sorted()[results.size / 2], median(results.map { it.seconds }))
    }

    private fun measure(roofs: Boolean = false): Map<String, List<RunResult>> = mapOf(
        "paresseux" to (1..runs).map { CosmoRunSim.run(it, 6000f, roofs) { g, _ -> IdleBot(g) } },
        "suiveur" to (1..runs).map { CosmoRunSim.run(it, 6000f, roofs) { g, _ -> AtomFollowerBot(g) } },
        "humain" to (1..runs).map { CosmoRunSim.run(it, 6000f, roofs) { g, r -> ReaderBot(g, r, .05f, .05f, reaction = .25f) } },
        "exact" to (1..runs).map { CosmoRunSim.run(it, 6000f, roofs) { g, r -> ReaderBot(g, r, 0f, 0f) } },
    ) + if (roofs) mapOf(
        "gourmand" to (1..runs).map { CosmoRunSim.run(it, 6000f, true) { g, r -> ReaderBot(g, r, .03f, .02f, greedy = true) } },
    ) else emptyMap()

    @Test fun report() {
        val out = StringBuilder()
        for (roofs in listOf(false, true)) {
            out.appendLine(if (roofs) "── avec toits ──" else "── sol seul ──")
            for ((name, results) in measure(roofs)) {
                out.appendLine(report(name, results))
                if (name == "exact" || name == "humain" || name == "gourmand") {
                    val hits = results.flatMap { it.hitSources }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                    out.appendLine("      chocs par motif : " + hits.take(8).joinToString(", ") { "${it.key}=${it.value}" })
                }
                if (name == "gourmand") {
                    val total = results.sumOf { it.seconds.toDouble() }
                    val roof = results.sumOf { it.roofSeconds.toDouble() }
                    val ground = total - roof
                    val roofAtoms = results.sumOf { it.roofAtoms }
                    val groundAtoms = results.sumOf { it.atoms } - roofAtoms
                    val roofHits = results.sumOf { it.roofHits }
                    val groundHits = results.sumOf { it.stumbles } - roofHits
                    out.appendLine("      atomes/s toit=%.2f sol=%.2f  |  chocs/s toit=%.4f sol=%.4f".format(
                        roofAtoms / roof, groundAtoms / ground, roofHits / roof, groundHits / ground))
                }
                if (name == "gourmand") out.appendLine("      part du temps sur les toits : %.0f %%".format(100f * results.sumOf { it.roofSeconds.toDouble() }.toFloat() / results.sumOf { it.seconds.toDouble() }.toFloat()) +
                    "  ;  chocs par motif : " + results.flatMap { it.hitSources }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8).joinToString(", ") { "${it.key}=${it.value}" })
            }
        }
        File("build/cosmo-sim").apply { mkdirs() }.let { File(it, "report.txt").writeText(out.toString()) }
        println(out)
    }

    @Test fun autopilotIsNoLongerPossible() {
        val idle = (1..runs).map { CosmoRunSim.run(it) { g, _ -> IdleBot(g) } }
        val follower = (1..runs).map { CosmoRunSim.run(it) { g, _ -> AtomFollowerBot(g) } }
        assertTrue("le paresseux doit mourir avant 600 m dans 95 % des parties",
            idle.count { it.cause != null && it.distance < 600f } >= runs * .95)
        assertTrue("le suiveur d'atomes doit mourir avant 900 m dans 90 % des parties",
            follower.count { it.cause != null && it.distance < 900f } >= runs * .90)
    }

    @Test fun anExactReaderNeverDiesUnfairly() {
        val exact = (1..runs).map { CosmoRunSim.run(it) { g, r -> ReaderBot(g, r, 0f, 0f) } }
        val deaths = exact.filter { it.cause != null }
        assertTrue("le lecteur exact meurt ${deaths.size} fois sur $runs : ${deaths.take(5).map { "${it.cause}@${it.distance.toInt()}" }}",
            deaths.size <= runs * .02)
        assertEquals("aucun rebond de flanc pour un joueur exact", 0, exact.sumOf { it.scrapes })
    }

    @Test fun sameSeedSamePlay() {
        val a = CosmoRunSim.run(9) { g, r -> ReaderBot(g, r, .05f, .05f) }
        val b = CosmoRunSim.run(9) { g, r -> ReaderBot(g, r, .05f, .05f) }
        assertEquals(a.distance, b.distance, 0f)
        assertEquals(a.atoms, b.atoms)
        assertEquals(Random(1).nextInt(), Random(1).nextInt())
    }
}
