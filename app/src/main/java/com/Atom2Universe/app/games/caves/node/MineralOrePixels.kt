package com.Atom2Universe.app.games.caves.node

import kotlin.math.abs

/** Small native pixel paintings; generated once for the atlas, never during a frame. */
internal object MineralOrePixels {
    const val VARIANTS = 4

    private fun noise(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and Int.MAX_VALUE
    }

    private fun mix(a: Int, b: Int, amount: Int, alpha: Int = 255): Int {
        val t = amount.coerceIn(0, 100)
        fun channel(shift: Int) = (((a ushr shift) and 255) * (100-t) + ((b ushr shift) and 255) * t) / 100
        return (alpha shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun pixels(metal: Int, color: Int, tier: Int, variant: Int): IntArray {
        val seed = metal * 31 + variant * 127 + 19
        val pixels = IntArray(32 * 32)
        // Broad stone planes and short fractures, rather than repeating diagonal stripes.
        for (y in 0..31) for (x in 0..31) {
            val plane = noise((x + variant * 3) / 7, (y + variant) / 6, seed) % 5
            val grit = noise(x, y, seed) % 9
            val stone = mix(0x343D46, 0x68717B, 17 + plane * 6 + grit)
            pixels[y*32+x] = stone
        }
        fun paint(x: Int, y: Int, c: Int) { if (x in 0..31 && y in 0..31) pixels[y*32+x] = c }
        for (crack in 0..3) {
            var x = noise(crack, 1, seed) % 32
            var y = noise(crack, 2, seed) % 32
            for (step in 0..(5 + noise(crack, 3, seed) % 7)) {
                paint(x,y,0xFF2E353E.toInt())
                if (step % 3 == 0) paint(x+1,y,0xFF59636C.toInt())
                x += if (noise(crack,step,seed) % 3 == 0) -1 else 1
                if (step % 2 == 0) y++
            }
        }
        val layouts = arrayOf(
            intArrayOf(8,8, 23,11, 12,24),
            intArrayOf(8,9, 21,7, 22,23, 6,24),
            intArrayOf(7,20, 15,10, 25,21),
            intArrayOf(6,7, 17,17, 26,7, 9,27)
        )
        val centers = layouts[variant % VARIANTS]
        val mask = IntArray(1024) { -1 }
        val coal = metal == 11
        for (i in centers.indices step 2) {
            val cx = centers[i] + noise(i,0,seed) % 3 - 1
            val cy = centers[i+1] + noise(i,1,seed) % 3 - 1
            val size = 3 + noise(i,2,seed) % 2
            val slant = if ((i/2 + variant) % 2 == 0) 1 else -1
            for (dy in -7..7) for (dx in -7..7) {
                val x = cx+dx; val y = cy+dy
                if (x !in 1..30 || y !in 1..30) continue
                val ax=abs(dx); val ay=abs(dy)
                val hit = when (metal) {
                    0 -> ax+ay <= size+1 && dx-dy < size+1 && !(dx < -1 && dy > 1)
                    1 -> abs(dx*2+slant*dy) <= 2 && ay <= size+2 ||
                        dy in -1..2 && abs(dx-slant*dy) <= 1 && ax <= size
                    2 -> dx*dx+dy*dy <= size*size && dx+dy < size+1
                    3 -> ax <= size && ay <= size && ax+ay <= size+2 && dx-dy < size+2
                    4 -> abs(dx+slant*dy) <= 1 && ay <= size+2 ||
                        abs(dx+slant*dy-3) <= 1 && dy in -2..size
                    5 -> ax <= size && ay <= size-1 && ax+ay <= size+1
                    6 -> abs(dx-slant*dy/2) <= 2 && ay <= size+2
                    7 -> ax <= size+2 && ay <= 2 && ax+ay <= size+2
                    8 -> ax+ay <= size+1 && (ax <= 1 || ay <= 1 || abs(ax-ay) <= 1)
                    11 -> ax+ay <= size+2 && ax <= size && ay <= size && dx-dy < size+2
                    12 -> ax <= size-1 && ay <= size+1 && ax+ay <= size+1
                    13 -> ax <= 2 && ay <= size+1 && (ay < size || ax <= 1)
                    14 -> abs(dx+slant*dy/2) <= 2 && ay <= size+2 && ax+ay <= size+3
                    15 -> ax+ay <= size && (ax+ay <= 2 || (x+y)%3 != 0)
                    16 -> ay <= size && ax + (if(dy<0) ay/2 else ay) <= size && (dy > -size || ax < size)
                    else -> abs(dx+slant*(dy/3)) <= 1 && ay <= size+2 ||
                        dy in 0..size && abs(dx-slant*dy) <= 1
                }
                if (hit) mask[y*32+x] = i/2
            }
        }
        // A recessed border and cast shadow anchor every inclusion in the host stone.
        for (y in 1..30) for (x in 1..30) if (mask[y*32+x] >= 0) {
            for ((dx,dy) in arrayOf(-1 to 0,1 to 0,0 to -1,0 to 1,1 to 1)) {
                if (mask[(y+dy)*32+x+dx] < 0) paint(x+dx,y+dy,0xFF252C34.toInt())
            }
        }
        val base = if (coal) 0x252A34 else color
        val shadow = mix(base,0x172538,48) and 0xffffff
        val highlight = mix(base,if(coal) 0x8393A2 else 0xFFF2D0,if(coal) 35 else 53) and 0xffffff
        val alpha = if (metal >= 11) 255 else 254 // only metallic ores use the glow mask
        for (y in 1..30) for (x in 1..30) {
            val i=y*32+x; val patch=mask[i]
            if (patch < 0) continue
            val top=mask[i-32] < 0; val left=mask[i-1] < 0
            val bottom=mask[i+32] < 0; val right=mask[i+1] < 0
            val facets=noise(x/2,y/2,seed+patch) % 7
            pixels[i] = when {
                top && left -> mix(base,highlight,90,alpha)
                top || left && facets < 4 -> mix(base,highlight,65,alpha)
                bottom || right -> mix(base,shadow,65,alpha)
                (x+y+patch*3) % 7 == 0 -> mix(base,highlight,34,alpha)
                facets < 2 -> mix(base,shadow,27,alpha)
                else -> mix(base,highlight,(tier-1)*4 + facets*3,alpha)
            }
        }
        // Tiny satellite fragments break the regular silhouette without filling the whole face.
        for (i in 0..4) {
            val x=2+noise(i,7,seed)%28; val y=2+noise(i,8,seed)%28
            if (mask[y*32+x] < 0 && mask[y*32+x+1] < 0) {
                paint(x,y,mix(base,shadow,25,alpha));paint(x+1,y,mix(base,highlight,35,alpha))
            }
        }
        return pixels
    }
}
