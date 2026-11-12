package com.Atom2Universe.app.gallery

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.Atom2Universe.app.pixelart.core.FloodFill
import java.nio.ByteBuffer
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.roundToInt

internal object PhotoSelections {
    private class Sample(source: Bitmap, edge: Int) {
        private val scale = minOf(1f, edge.toFloat() / maxOf(source.width, source.height))
        val w = (source.width * scale).roundToInt().coerceAtLeast(1)
        val h = (source.height * scale).roundToInt().coerceAtLeast(1)
        val pixels = IntArray(w*h)
        init {
            val small = Bitmap.createScaledBitmap(source, w, h, true)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            if (small !== source) small.recycle()
        }
        fun mask(bits: ByteArray): PhotoMask {
            val bitmap = Bitmap.createBitmap(w,h,Bitmap.Config.ALPHA_8)
            val bytes = ByteArray(bitmap.rowBytes*h)
            for (y in 0 until h) for (x in 0 until w) {
                if (bits[y*w+x].toInt() != 0) bytes[y*bitmap.rowBytes+x] = -1
            }
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
            return PhotoMask(listOf(MaskPart.Region(bitmap)))
        }
    }

    fun wand(source: Bitmap, at: PointF, tolerance: Int): PhotoMask {
        val s = Sample(source, 1024)
        return s.mask(FloodFill.region(s.pixels, s.w, s.h, (at.x*s.w).toInt().coerceIn(0,s.w-1),
            (at.y*s.h).toInt().coerceIn(0,s.h-1), tolerance, contiguous = true))
    }

    private fun bin(c: Int) = ((c ushr 12) and 0xf00) or ((c ushr 8) and 0xf0) or ((c ushr 4) and 15)
    private fun difference(a: Int, b: Int): Int =
        abs(((a shr 16) and 255)-((b shr 16) and 255)) + abs(((a shr 8) and 255)-((b shr 8) and 255)) + abs((a and 255)-(b and 255))
    private data class Front(val i: Int, val cost: Int)

    /** Competing interior and border seeds propagate cheaply across similar pixels, stopping at edges. */
    fun subject(source: Bitmap, box: RectF): PhotoMask {
        val s = Sample(source, 512)
        val left = (box.left*s.w).toInt().coerceIn(0,s.w-1)
        val right = (box.right*s.w).toInt().coerceIn(left,s.w-1)
        val top = (box.top*s.h).toInt().coerceIn(0,s.h-1)
        val bottom = (box.bottom*s.h).toInt().coerceIn(top,s.h-1)
        val bw = right-left+1
        val bh = bottom-top+1
        val centerX = (right+left)/2
        val centerY = (bottom+top)/2
        val outside = IntArray(4096)
        val inside = IntArray(4096)
        var nOut = 0
        var nIn = 0
        for (y in top..bottom) for (x in left..right) {
            val b = bin(s.pixels[y*s.w+x])
            if (minOf(x-left,right-x) <= bw/18 || minOf(y-top,bottom-y) <= bh/18) {
                outside[b]++
                nOut++
            } else if (abs(x-centerX) <= bw/4 && abs(y-centerY) <= bh/3) {
                inside[b]++
                nIn++
            }
        }
        val label = ByteArray(s.w*s.h)
        val costs = IntArray(label.size) { Int.MAX_VALUE }
        val queue = PriorityQueue<Front>(compareBy { it.cost })
        fun seed(i: Int, foreground: Boolean) {
            costs[i] = 0
            label[i] = if (foreground) 1 else 0
            queue.add(Front(i,0))
        }
        for (y in top..bottom) for (x in left..right) {
            val i = y*s.w+x
            val b = bin(s.pixels[i])
            if (x == left || x == right || y == top || y == bottom) seed(i,false)
            else if (abs(x-centerX) <= bw/3 && abs(y-centerY) <= bh*2/5 &&
                inside[b] >= 2 && inside[b].toLong()*nOut > 3L*outside[b]*nIn) seed(i,true)
        }
        seed(centerY*s.w+centerX,true)
        fun extend(from: Front, i: Int) {
            val delta = difference(s.pixels[from.i], s.pixels[i])
            val cost = from.cost + 4 + delta*delta/12
            if (cost < costs[i]) {
                costs[i] = cost
                label[i] = label[from.i]
                queue.add(Front(i,cost))
            }
        }
        while (queue.isNotEmpty()) {
            val front = queue.remove()
            if (front.cost != costs[front.i]) continue
            val x = front.i % s.w
            val y = front.i / s.w
            if (x > left) extend(front,front.i-1)
            if (x < right) extend(front,front.i+1)
            if (y > top) extend(front,front.i-s.w)
            if (y < bottom) extend(front,front.i+s.w)
        }
        return s.mask(label)
    }
}
