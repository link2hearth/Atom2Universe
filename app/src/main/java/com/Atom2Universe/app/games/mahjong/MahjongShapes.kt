package com.Atom2Universe.app.games.mahjong

import kotlin.random.Random

/**
 * Formes des plateaux de Solitaire et de Triples, en demi-tuiles : une tuile occupe 2 × 2
 * demi-cases. Dans le texte, chaque caractère est une demi-colonne ('X' = bord gauche d'une
 * tuile), chaque ligne une rangée entière ; une ligne qui commence par une apostrophe est
 * décalée d'une demi-rangée vers le bas. Chaque forme compte un multiple de 6 tuiles.
 */
internal object MahjongShapes {
    data class Spot(val x: Int, val y: Int, val z: Int)

    private val raw: List<List<List<String>>> = listOf(
        // 0 · Pyramide (48)
        listOf(
            listOf("XxXxXxXxXxXx", "XxXxXxXxXxXx", "XxXxXxXxXxXx", "XxXxXxXxXxXx", "XxXxXxXxXxXx"),
            listOf("", "..XxXxXxXx..", "..XxXxXxXx..", "..XxXxXxXx.."),
            listOf("", "'...XxXxXx...", "'...XxXxXx...")),
        // 1 · Lanterne (54)
        listOf(
            listOf("..XxXxXxXx..", "XxXxXxXxXxXx", "XxXxXxXxXxXx", "XxXxXxXxXxXx", "XxXxXxXxXxXx", "..XxXxXxXx.."),
            listOf("", "..XxXxXxXx..", "..XxXxXxXx..", "..XxXxXxXx..", "..XxXxXxXx.."),
            listOf("", "", "...XxXxXx...", "...XxXxXx...")),
        // 2 · Pont (72)
        listOf(
            listOf("XxXxXx....XxXxXx", "XxXxXx....XxXxXx", "XxXxXxXxXxXxXxXx", "XxXxXxXxXxXxXxXx", "XxXxXx....XxXxXx", "XxXxXx....XxXxXx"),
            listOf("XxXx........XxXx", "XxXx........XxXx", "..XxXxXxXxXxXx..", "..XxXxXxXxXxXx..", "XxXx........XxXx", "XxXx........XxXx"),
            listOf("'.Xx..........Xx.", "", "", "", "'.Xx..........Xx.")),
        // 3 · Cœur (72)
        listOf(
            listOf("..XxXx....XxXx..", "XxXxXxXxXxXxXxXx", "XxXxXxXxXxXxXxXx", "XxXxXxXxXxXxXxXx", "..XxXxXxXxXxXx..", "....XxXxXxXx....", "......XxXx......"),
            listOf("", "..XxXx....XxXx..", "..XxXxXxXxXxXx..", "..XxXxXxXxXxXx..", "....XxXxXxXx....", "......XxXx......"),
            listOf("", "'...Xx......Xx...", "'....XxXxXxXx....", "'......XxXx......"),
            listOf("", "", "", "......XxXx......")),
        // 4 · Papillon (90)
        listOf(
            listOf("XxXx........XxXx", "XxXxXx....XxXxXx", "XxXxXxXxXxXxXxXx", "..XxXxXxXxXxXx..", "......XxXx......",
                "..XxXxXxXxXxXx..", "XxXxXxXxXxXxXxXx", "XxXxXx....XxXxXx", "XxXx........XxXx"),
            listOf("", "XxXx........XxXx", "XxXxXx....XxXxXx", "....XxXxXxXx....", "......XxXx......", "....XxXxXxXx....",
                "XxXxXx....XxXxXx", "XxXx........XxXx"),
            listOf("", "'.Xx..........Xx.", "", "'......XxXx......", "'......XxXx......", "", "'.Xx..........Xx."),
            listOf("", "", "", "", "......XxXx......")),
        // 5 · Lotus (90)
        listOf(
            listOf("....XxXxXxXx....", "..XxXxXxXxXxXx..", "XxXxXxXxXxXxXxXx", "XxXxXxXxXxXxXxXx", "XxXxXxXxXxXxXxXx", "..XxXxXxXxXxXx..", "....XxXxXxXx...."),
            listOf("", "....XxXxXxXx....", "..XxXxXxXxXxXx..", "..XxXxXxXxXxXx..", "..XxXxXxXxXxXx..", "....XxXxXxXx...."),
            listOf("", "'.....XxXxXx.....", "'.....XxXxXx.....", "'.....XxXxXx.....", "'.....XxXxXx....."),
            listOf("", "", "......XxXx......", "......XxXx......", "......XxXx......"),
            listOf("", "", "'.......Xx.......", "'.......Xx.......")),
        // 6 · Pagode (120)
        listOf(
            listOf("......XxXx......", "....XxXxXxXx....", "XxXxXxXxXxXxXxXx", "..XxXxXxXxXxXx..", "XxXxXxXxXxXxXxXx",
                "..XxXxXxXxXxXx..", "XxXxXxXxXxXxXxXx", "..XxXxXxXxXxXx..", "XxXxXxXxXxXxXxXx"),
            listOf("", "......XxXx......", "....XxXxXxXx....", "..XxXxXxXxXxXx..", "....XxXxXxXx....", "..XxXxXxXxXxXx..",
                "....XxXxXxXx....", "..XxXxXxXxXxXx..", "....XxXxXxXx...."),
            listOf("", "", "'.....XxXxXx.....", "'.....XxXxXx.....", "'.....XxXxXx.....", "'.....XxXxXx.....",
                "'.....XxXxXx.....", "'.....XxXxXx....."),
            listOf("", "", "", "......XxXx......", "......XxXx......", "......XxXx......", "......XxXx......", "......XxXx......")),
        // 7 · Forteresse (102)
        listOf(
            listOf("XxXx........XxXx", "XxXxXxXxXxXxXxXx", "..XxXxXxXxXxXx..", "..XxXxXxXxXxXx..", "..XxXxXxXxXxXx..",
                "..XxXxXxXxXxXx..", "..XxXxXxXxXxXx..", "XxXxXxXxXxXxXxXx", "XxXx........XxXx"),
            listOf("XxXx........XxXx", "XxXx........XxXx", "..XxXxXxXxXxXx..", "..Xx........Xx..", "..Xx..XxXx..Xx..",
                "..Xx........Xx..", "..XxXxXxXxXxXx..", "XxXx........XxXx", "XxXx........XxXx"),
            listOf("'.Xx..........Xx.", "", "..Xx........Xx..", "", "......XxXx......", "", "", "'.Xx..........Xx."),
            listOf("'.Xx..........Xx.", "", "", "", "", "", "", "'.Xx..........Xx.")),
        // 8 · Grande pyramide (120)
        listOf(
            List(8) { "XxXxXxXxXxXxXxXx" },
            listOf("") + List(6) { "..XxXxXxXxXxXx.." },
            listOf("", "") + List(4) { "....XxXxXxXx...." },
            listOf("", "", "", "......XxXx......", "......XxXx......"))
    )

    val all: List<List<Spot>> by lazy { raw.map(::parse) }

    /** Petites formes au début, grandes ensuite ; le niveau suit la difficulté et les victoires. */
    fun tier(stage: Int): List<Int> = when {
        stage <= 2 -> listOf(0, 1)
        stage <= 5 -> listOf(2, 3, 4, 5)
        else -> listOf(6, 7, 8)
    }

    fun pick(stage: Int, random: Random): List<Spot> = all[tier(stage).random(random)]

    private fun parse(layers: List<List<String>>): List<Spot> {
        val spots = mutableListOf<Spot>()
        layers.forEachIndexed { z, rows ->
            rows.forEachIndexed { r, row ->
                val half = row.startsWith("'")
                val body = if (half) row.substring(1) else row
                body.forEachIndexed { c, ch -> if (ch == 'X') spots += Spot(c, r * 2 + if (half) 1 else 0, z) }
            }
        }
        val minX = spots.minOf { it.x }; val minY = spots.minOf { it.y }
        return spots.map { Spot(it.x - minX, it.y - minY, it.z) }
            .sortedWith(compareBy<Spot> { it.z }.thenBy { it.y }.thenBy { it.x })
    }
}
