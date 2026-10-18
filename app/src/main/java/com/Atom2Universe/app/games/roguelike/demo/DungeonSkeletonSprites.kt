package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Bitmap

/** Silhouette tournée vers la gauche, pieds au même niveau que ceux des rats. */
internal object DungeonSkeletonSprites {
    private val body = """
        ..........#######........
        ........##hhhhhhm#.......
        .......#hhhhhhhmmm#......
        ......#hhhhhmmmmmms#.....
        ......#h###mm###mms#.....
        ......#h#e#mm#e#mms#.....
        ......#h###mm###mms#.....
        .......#hhm#s#mmms#......
        .......#hmmmmmmmss#......
        ........#h#h#hmss#.......
        ........#########.......
        ...........#hm#.........
        .......####shms####......
        ......#hhhhhmmmmmms#.....
        .....#hm###shms###sm#....
        .....#hm#hhhhmmms#sm#....
        ....#hm#s##shm###ssm#....
        ....#hm#hhhhmmmms#sm#....
        ....#hm#s##shm###ssm#....
        ...#hm#hhhhmmmms#ssm#....
        ...#hm#####shm####sm#....
        ...#hm#....#hm#...#sm#...
        ..#hhm#...#shms#..#sm#...
        ...###...#hhmmms#.#sm#...
        ........#hh###mms##mm#..
        ........#hm#.#mms#.###..
        ........#hm#..#ms#......
        ........#hm#..#ms#......
        .......#hms#..#mss#.....
        ........#hm#...#ms#.....
        ........#hm#...#ms#.....
        ........#hm#...#ms#.....
        ......##hhm#..##mms#....
        .....#hhhhm#.#mmmms#....
        ......######..######....
    """.trimIndent()

    // Poses en cache : le crâne et le thorax restent stables, seuls les membres bougent.
    fun create(style: DungeonDemoSprites.MonsterStyle, pose: Int = 0, jaw: Int = 0): Bitmap {
        val boss = style == DungeonDemoSprites.MonsterStyle.SKELETON_BOSS
        val rows = body.lines().toMutableList()
        when (style) {
            DungeonDemoSprites.MonsterStyle.SKELETON_BOSS -> {
                rows[2] = ".......#hhhh#hhmmm#......"
                rows[3] = "......#hhh##hmm#mms#....."
                rows[4] = "......#h####m####ms#....."
                rows[5] = "......#h#ee#m#ee#ms#....."
                rows[6] = "......#h#o##m#o##ms#....."
                rows[9] = "........#w#w#wmss#......."
            }
            DungeonDemoSprites.MonsterStyle.SKELETON_MOSS -> {
                rows[1] = "........##pphhhhm#......."
                rows[2] = ".......#hppphhhmmm#......"
                rows[13] = "......#hhhhhmmmppps#....."
                rows[24] = "........#pp###mms##mm#.."
            }
            DungeonDemoSprites.MonsterStyle.SKELETON_ASH -> {
                rows[1] = "........##hhh#hhm#......."
                rows[2] = ".......#hhhh#hmmmm#......"
                rows[3] = "......#hhhhh#mmmmms#....."
                rows[17] = "....#hm#hh..mmmms#sm#...."
                rows[9] = "........#h#.#hmss#......."
            }
            else -> Unit
        }
        val colors = mapOf('#' to 0xFF111729.toInt(), 'h' to style.light.toInt(),
            'm' to style.fur.toInt(), 's' to style.shadow.toInt(), 'p' to style.skin.toInt(),
            'e' to style.eye.toInt(), 'o' to 0xFFFFAC45.toInt(), 'w' to 0xFFFFECCD.toInt())
        val width = 38
        val height = if (boss) 43 else 35
        val pixels = IntArray(width * height)
        val swing = intArrayOf(0, 1, 2, 1, 0, -1, -2, -1)[pose % 8]
        // Le boss a un thorax élargi et allongé, des bras longs, mais garde un crâne compact.
        val extraRows = if (boss) setOf(14, 16, 18, 20, 22, 27, 29, 31) else emptySet()
        fun mappedX(x: Int, y: Int): Int = x + 6 + if (!boss || y < 12) 0 else when {
            x < 8 -> -3
            x < 18 -> (x - 8) * 2 / 3 - 3
            else -> 3
        }
        // Bras derrière le corps, pour que leurs épaules restent attachées pendant le balancement.
        for (layer in 0..1) {
            var destY = 0
            rows.forEachIndexed { y, row ->
                val copies = if (y in extraRows) 2 else 1
                row.forEachIndexed pixel@ { x, ch ->
                    val color = colors[ch] ?: return@pixel
                    val leftArm = y in 14..23 && x <= 6
                    val rightArm = y in 14..25 && x >= 18
                    val arm = leftArm || rightArm
                    if (arm != (layer == 0)) return@pixel
                    val dx = when {
                        leftArm -> swing * (y - 14) / 7
                        rightArm -> -swing * (y - 14) / 8
                        y >= 29 && x < 13 -> if (pose in 1..3) -1 else 0
                        y >= 29 -> if (pose in 5..7) 1 else 0
                        else -> 0
                    }
                    val dy = when {
                        y in 9..10 -> jaw
                        y >= 29 && x < 13 && pose in 1..3 -> -1
                        y >= 29 && x >= 13 && pose in 5..7 -> -1
                        else -> 0
                    }
                    val fromX = mappedX(x, y) + dx
                    val toX = mappedX(x + 1, y) + dx
                    repeat(copies) { copy ->
                        val py = destY + copy + dy
                        for (px in fromX until toX) {
                            if (px in 0 until width && py in 0 until height) pixels[py * width + px] = color
                        }
                    }
                }
                destY += copies
            }
        }
        // Charnières et intérieur sombre : la mandibule ouverte reste reliée au crâne.
        for (y in 9 until 9 + jaw) {
            for (x in 8..16) {
                pixels[y * width + mappedX(x, y)] = if (x == 8 || x == 16)
                    style.shadow.toInt() else 0xFF111729.toInt()
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
