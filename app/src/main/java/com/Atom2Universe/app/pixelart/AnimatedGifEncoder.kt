package com.Atom2Universe.app.pixelart

import android.graphics.Bitmap
import android.graphics.Color
import java.io.OutputStream

/**
 * Encodes a series of Bitmaps into an animated GIF.
 * Based on the widely-used AnimatedGifEncoder implementation.
 */
class AnimatedGifEncoder {
    private var width = 0
    private var height = 0
    private var transparent: Int? = null
    private var transIndex = 0
    private var repeat = 0  // 0 = loop forever, -1 = no repeat
    private var delay = 100  // Frame delay in milliseconds
    private var started = false
    private var out: OutputStream? = null
    private var image: Bitmap? = null
    private var pixels: ByteArray? = null
    private var indexedPixels: ByteArray? = null
    private var colorDepth = 0
    private var colorTab: ByteArray? = null
    private var usedEntry = BooleanArray(256)
    private var palSize = 7  // color table size (bits-1)
    private var dispose = -1  // disposal code (-1 = use default)
    private var closeStream = false
    private var firstFrame = true
    private var sizeSet = false
    private var sample = 10  // default sample interval for quantizer

    /**
     * Sets the delay time between frames.
     * @param ms Delay in milliseconds
     */
    fun setDelay(ms: Int) {
        delay = (ms / 10f).toInt().coerceAtLeast(1)
    }

    /**
     * Sets the GIF frame disposal code.
     * @param code disposal code
     */
    fun setDispose(code: Int) {
        if (code >= 0) dispose = code
    }

    /**
     * Sets the number of times the animation should repeat.
     * @param iter 0 = infinite, -1 = no repeat, n = repeat n times
     */
    fun setRepeat(iter: Int) {
        repeat = if (iter >= 0) iter else -1
    }

    /**
     * Sets the transparent color.
     */
    fun setTransparent(c: Int?) {
        transparent = c
    }

    /**
     * Adds next frame to the GIF.
     * @param im Image to add
     * @return true if successful
     */
    fun addFrame(im: Bitmap): Boolean {
        if (!started) return false

        var ok = true
        try {
            if (!sizeSet) {
                setSize(im.width, im.height)
            }
            image = im
            getImagePixels()
            analyzePixels()
            if (firstFrame) {
                writeLSD()
                writePalette()
                if (repeat >= 0) {
                    writeNetscapeExt()
                }
            }
            writeGraphicCtrlExt()
            writeImageDesc()
            if (!firstFrame) {
                writePalette()
            }
            writePixels()
            firstFrame = false
        } catch (e: Exception) {
            ok = false
        }
        return ok
    }

    /**
     * Writes GIF file header.
     * @param os Output stream
     * @return true if started successfully
     */
    fun start(os: OutputStream): Boolean {
        var ok = true
        closeStream = false
        out = os
        try {
            writeString("GIF89a")
        } catch (e: Exception) {
            ok = false
        }
        started = ok
        return ok
    }

    /**
     * Finishes encoding and flushes output.
     * @return true if finished successfully
     */
    fun finish(): Boolean {
        if (!started) return false
        var ok = true
        started = false
        try {
            out?.write(0x3b)  // GIF trailer
            out?.flush()
            if (closeStream) {
                out?.close()
            }
        } catch (e: Exception) {
            ok = false
        }
        // Reset for subsequent use
        transIndex = 0
        out = null
        image = null
        pixels = null
        indexedPixels = null
        colorTab = null
        closeStream = false
        firstFrame = true
        return ok
    }

    /**
     * Sets frame size.
     */
    fun setSize(w: Int, h: Int) {
        if (started && !firstFrame) return
        width = w
        height = h
        if (width < 1) width = 320
        if (height < 1) height = 240
        sizeSet = true
    }

    /**
     * Extracts image pixels into byte array.
     */
    private fun getImagePixels() {
        val w = image?.width ?: return
        val h = image?.height ?: return
        if (w != width || h != height) {
            val temp = Bitmap.createScaledBitmap(image!!, width, height, false)
            image = temp
        }
        pixels = ByteArray(3 * width * height)
        val pixelsInt = IntArray(width * height)
        image?.getPixels(pixelsInt, 0, width, 0, 0, width, height)

        var k = 0
        for (i in pixelsInt.indices) {
            val color = pixelsInt[i]
            pixels!![k++] = Color.red(color).toByte()
            pixels!![k++] = Color.green(color).toByte()
            pixels!![k++] = Color.blue(color).toByte()
        }
    }

    /**
     * Analyzes image colors and creates color map.
     */
    private fun analyzePixels() {
        val len = pixels?.size ?: return
        val nPix = len / 3
        indexedPixels = ByteArray(nPix)

        // Use simple color quantization (median cut simplified)
        val colorMap = mutableMapOf<Int, Int>()
        val colors = mutableListOf<Int>()

        // First pass: collect unique colors
        var k = 0
        for (i in 0 until nPix) {
            val r = (pixels!![k++].toInt() and 0xff)
            val g = (pixels!![k++].toInt() and 0xff)
            val b = (pixels!![k++].toInt() and 0xff)
            val color = (r shl 16) or (g shl 8) or b
            if (!colorMap.containsKey(color) && colorMap.size < 256) {
                colorMap[color] = colors.size
                colors.add(color)
            }
        }

        // Build color table
        val nColors = colors.size.coerceAtLeast(2)
        palSize = 1
        while ((1 shl palSize) < nColors) palSize++
        palSize = (palSize - 1).coerceAtLeast(1)
        colorDepth = palSize + 1
        val mapSize = 1 shl colorDepth
        colorTab = ByteArray(3 * mapSize)
        usedEntry = BooleanArray(mapSize)

        for (i in colors.indices) {
            val color = colors[i]
            val j = i * 3
            colorTab!![j] = ((color shr 16) and 0xff).toByte()
            colorTab!![j + 1] = ((color shr 8) and 0xff).toByte()
            colorTab!![j + 2] = (color and 0xff).toByte()
            usedEntry[i] = true
        }

        // Map pixels to palette indices
        k = 0
        for (i in 0 until nPix) {
            val r = (pixels!![k++].toInt() and 0xff)
            val g = (pixels!![k++].toInt() and 0xff)
            val b = (pixels!![k++].toInt() and 0xff)
            val color = (r shl 16) or (g shl 8) or b
            val index = colorMap[color] ?: findClosest(color, colors)
            indexedPixels!![i] = index.toByte()
        }
        pixels = null
        colorDepth = palSize + 1
        palSize = colorDepth - 1

        // Get transparent index if needed
        if (transparent != null) {
            transIndex = findClosest(transparent!!, colors)
        }
    }

    /**
     * Finds closest color in palette.
     */
    private fun findClosest(color: Int, colors: List<Int>): Int {
        val r = (color shr 16) and 0xff
        val g = (color shr 8) and 0xff
        val b = color and 0xff
        var minDist = Int.MAX_VALUE
        var minIndex = 0
        for (i in colors.indices) {
            val c = colors[i]
            val cr = (c shr 16) and 0xff
            val cg = (c shr 8) and 0xff
            val cb = c and 0xff
            val d = (r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb)
            if (d < minDist) {
                minDist = d
                minIndex = i
            }
        }
        return minIndex
    }

    /**
     * Writes Graphic Control Extension.
     */
    private fun writeGraphicCtrlExt() {
        out?.write(0x21)  // Extension introducer
        out?.write(0xf9)  // GCE label
        out?.write(4)     // Block size

        val transp = if (transparent != null) 1 else 0
        val disp = if (dispose >= 0) dispose and 7 else 0
        val packed = (disp shl 2) or transp
        out?.write(packed)

        writeShort(delay)  // Delay time
        out?.write(transIndex)  // Transparent color index
        out?.write(0)  // Block terminator
    }

    /**
     * Writes Image Descriptor.
     */
    private fun writeImageDesc() {
        out?.write(0x2c)  // Image separator
        writeShort(0)  // Image position x
        writeShort(0)  // Image position y
        writeShort(width)
        writeShort(height)
        // Packed field
        if (firstFrame) {
            out?.write(0)  // No LCT
        } else {
            out?.write(0x80 or palSize)  // LCT flag + size
        }
    }

    /**
     * Writes Logical Screen Descriptor.
     */
    private fun writeLSD() {
        writeShort(width)
        writeShort(height)
        // Packed field: GCT flag, color resolution, sort flag, GCT size
        out?.write(0x80 or 0x70 or palSize)
        out?.write(0)  // Background color index
        out?.write(0)  // Pixel aspect ratio
    }

    /**
     * Writes Netscape application extension for looping.
     */
    private fun writeNetscapeExt() {
        out?.write(0x21)  // Extension introducer
        out?.write(0xff)  // Application extension label
        out?.write(11)    // Block size
        writeString("NETSCAPE2.0")
        out?.write(3)     // Sub-block size
        out?.write(1)     // Loop sub-block id
        writeShort(repeat)
        out?.write(0)     // Block terminator
    }

    /**
     * Writes color table.
     */
    private fun writePalette() {
        out?.write(colorTab, 0, colorTab?.size ?: 0)
        val n = (3 * (1 shl colorDepth)) - (colorTab?.size ?: 0)
        for (i in 0 until n) {
            out?.write(0)
        }
    }

    /**
     * Encodes and writes pixel data.
     */
    private fun writePixels() {
        val encoder = LZWEncoder(width, height, indexedPixels!!, colorDepth)
        encoder.encode(out!!)
    }

    /**
     * Writes 16-bit value in little-endian format.
     */
    private fun writeShort(value: Int) {
        out?.write(value and 0xff)
        out?.write((value shr 8) and 0xff)
    }

    /**
     * Writes string to output.
     */
    private fun writeString(s: String) {
        for (c in s) {
            out?.write(c.code)
        }
    }
}

/**
 * LZW encoder for GIF format.
 */
private class LZWEncoder(
    private val imgW: Int,
    private val imgH: Int,
    private val pixAry: ByteArray,
    private val initCodeSize: Int
) {
    private val EOF = -1
    private val BITS = 12
    private val HSIZE = 5003

    private var nBits = 0
    private var maxbits = BITS
    private var maxcode = 0
    private var maxmaxcode = 1 shl BITS

    private var htab = IntArray(HSIZE)
    private var codetab = IntArray(HSIZE)

    private var hsize = HSIZE
    private var freeEnt = 0

    private var clearFlg = false

    private var gInitBits = 0
    private var clearCode = 0
    private var eofCode = 0

    private var curAccum = 0
    private var curBits = 0

    private val masks = intArrayOf(
        0x0000, 0x0001, 0x0003, 0x0007, 0x000F,
        0x001F, 0x003F, 0x007F, 0x00FF, 0x010F,
        0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF,
        0x3FFF, 0x7FFF, 0xFFFF
    )

    private var accum = ByteArray(256)
    private var aCount = 0

    private var remaining = 0
    private var curPixel = 0

    fun encode(os: OutputStream) {
        os.write(initCodeSize)
        remaining = imgW * imgH
        curPixel = 0
        compress(initCodeSize + 1, os)
        os.write(0)
    }

    private fun compress(initBits: Int, outs: OutputStream) {
        gInitBits = initBits
        clearFlg = false
        nBits = gInitBits
        maxcode = maxCode(nBits)
        clearCode = 1 shl (initBits - 1)
        eofCode = clearCode + 1
        freeEnt = clearCode + 2
        aCount = 0

        var ent = nextPixel()

        var hshift = 0
        var fcode = hsize
        while (fcode < 65536) {
            hshift++
            fcode *= 2
        }
        hshift = 8 - hshift

        val hsizeReg = hsize
        clHash(hsizeReg)

        output(clearCode, outs)

        var c: Int
        outer@ while ((nextPixel().also { c = it }) != EOF) {
            fcode = (c shl maxbits) + ent
            var i = (c shl hshift) xor ent

            if (htab[i] == fcode) {
                ent = codetab[i]
                continue
            } else if (htab[i] >= 0) {
                var disp = hsizeReg - i
                if (i == 0) disp = 1
                do {
                    i -= disp
                    if (i < 0) i += hsizeReg
                    if (htab[i] == fcode) {
                        ent = codetab[i]
                        continue@outer
                    }
                } while (htab[i] >= 0)
            }

            output(ent, outs)
            ent = c
            if (freeEnt < maxmaxcode) {
                codetab[i] = freeEnt++
                htab[i] = fcode
            } else {
                clBlock(outs)
            }
        }

        output(ent, outs)
        output(eofCode, outs)
    }

    private fun clBlock(outs: OutputStream) {
        clHash(hsize)
        freeEnt = clearCode + 2
        clearFlg = true
        output(clearCode, outs)
    }

    private fun clHash(hsize: Int) {
        for (i in 0 until hsize) {
            htab[i] = -1
        }
    }

    private fun maxCode(nBits: Int): Int {
        return (1 shl nBits) - 1
    }

    private fun nextPixel(): Int {
        if (remaining == 0) return EOF
        remaining--
        val pix = pixAry[curPixel++].toInt() and 0xff
        return pix
    }

    private fun output(code: Int, outs: OutputStream) {
        curAccum = curAccum and masks[curBits]
        curAccum = if (curBits > 0) curAccum or (code shl curBits) else code
        curBits += nBits

        while (curBits >= 8) {
            charOut((curAccum and 0xff).toByte(), outs)
            curAccum = curAccum shr 8
            curBits -= 8
        }

        if (freeEnt > maxcode || clearFlg) {
            if (clearFlg) {
                maxcode = maxCode(gInitBits.also { nBits = it })
                clearFlg = false
            } else {
                nBits++
                maxcode = if (nBits == maxbits) maxmaxcode else maxCode(nBits)
            }
        }

        if (code == eofCode) {
            while (curBits > 0) {
                charOut((curAccum and 0xff).toByte(), outs)
                curAccum = curAccum shr 8
                curBits -= 8
            }
            flushChar(outs)
        }
    }

    private fun charOut(c: Byte, outs: OutputStream) {
        accum[aCount++] = c
        if (aCount >= 254) flushChar(outs)
    }

    private fun flushChar(outs: OutputStream) {
        if (aCount > 0) {
            outs.write(aCount)
            outs.write(accum, 0, aCount)
            aCount = 0
        }
    }
}
