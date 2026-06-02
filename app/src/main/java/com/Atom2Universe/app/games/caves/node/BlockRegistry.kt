package com.Atom2Universe.app.games.caves.node

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject

internal object BlockRegistry {

    private val defs = HashMap<Short, BlockDef>()

    // Tables O(1) pour le hot path du rendu — indexées par id.toInt() and 0xFFFF
    private val decorationTable  = BooleanArray(65536)
    private val transparentTable = BooleanArray(65536)
    private val waterTable       = BooleanArray(65536)
    private val fallingTable     = BooleanArray(65536)
    private val waterloggedTable = BooleanArray(65536)

    // Tables de layers pré-calculées après buildTextureAtlas()
    private val layerTopTable       = IntArray(65536)
    private val layerBottomTable    = IntArray(65536)
    private val layerSideTable      = IntArray(65536)
    private val layerFrontTable     = IntArray(65536)
    private val layerSideGrassTable = IntArray(65536)
    private val layerSideSandTable  = IntArray(65536)
    private val layerSideSnowTable  = IntArray(65536)

    // Table d'orientation
    private val orientModeTable = ByteArray(65536)

    // Textures uniques ordonnées → index = couche GL dans la texture array
    private val textureOrder = mutableListOf<String>()
    private val textureIndexMap = HashMap<String, Int>()

    // Textures générées par le renderer (ex: torch, ward_stone)
    private val generatedProviders = HashMap<String, (Int) -> Bitmap>()

    // Copies des faces top pour l'UI — stockées avant le recycle GL dans CaveRenderer
    private val topBitmapById = HashMap<Short, Bitmap>()

    fun load(assets: AssetManager) {
        if (defs.isNotEmpty()) return
        val files = assets.list("caves/blocks") ?: return
        for (file in files) {
            if (!file.endsWith(".json")) continue
            val json = assets.open("caves/blocks/$file").bufferedReader().readText()
            val def = BlockDef.fromJson(JSONObject(json))
            defs[def.id] = def
            val idx = def.id.toInt() and 0xFFFF
            if (def.decoration)  decorationTable[idx]  = true
            if (def.transparent) transparentTable[idx] = true
            if (def.water)       waterTable[idx]       = true
            if (def.falling)     fallingTable[idx]     = true
            if (def.waterlogged) waterloggedTable[idx] = true
            orientModeTable[idx] = def.orientMode
        }
    }

    fun registerGeneratedTexture(name: String, provider: (Int) -> Bitmap) {
        generatedProviders[name] = provider
    }

    fun buildTextureAtlas(assets: AssetManager, tileSize: Int): List<Bitmap> {
        // Réinitialise l'état texture pour chaque reconstruction (recréation de surface GL)
        textureIndexMap.clear()
        textureOrder.clear()
        topBitmapById.values.forEach { it.recycle() }
        topBitmapById.clear()

        val bitmaps = mutableListOf<Bitmap>()

        fun register(name: String): Int {
            textureIndexMap[name]?.let { return it }
            val idx = bitmaps.size
            textureIndexMap[name] = idx
            textureOrder += name
            bitmaps += when {
                generatedProviders.containsKey(name) -> generatedProviders[name]!!(tileSize)
                else -> {
                    val path = if (name.startsWith("Items/")) "Cave World/$name"
                               else "Cave World/Tiles/$name"
                    BitmapFactory.decodeStream(assets.open(path))
                }
            }
            return idx
        }

        for (def in defs.values.sortedBy { it.id }) {
            def.layerTop    = register(def.textureTop)
            def.layerSide   = register(def.textureSide)
            def.layerBottom = register(def.textureBottom)
            def.layerFront     = def.textureFront?.let { register(it) } ?: def.layerSide
            def.layerSideGrass = def.textureSideGrass?.let { register(it) } ?: def.layerSide
            def.layerSideSand  = def.textureSideSand?.let  { register(it) } ?: def.layerSide
            def.layerSideSnow  = def.textureSideSnow?.let  { register(it) } ?: def.layerSide

            val idx = def.id.toInt() and 0xFFFF
            layerTopTable[idx]       = def.layerTop
            layerBottomTable[idx]    = def.layerBottom
            layerSideTable[idx]      = def.layerSide
            layerFrontTable[idx]     = def.layerFront
            layerSideGrassTable[idx] = def.layerSideGrass
            layerSideSandTable[idx]  = def.layerSideSand
            layerSideSnowTable[idx]  = def.layerSideSnow

            // Copie indépendante pour l'UI : survivra au recycle GL dans CaveRenderer
            val src = bitmaps[def.layerTop]
            topBitmapById[def.id] = src.copy(src.config ?: Bitmap.Config.ARGB_8888, false)
        }

        return bitmaps
    }

    fun layerCount() = textureOrder.size

    fun get(id: Short): BlockDef? = defs[id]

    fun isDecoration(block: Short)  = decorationTable[block.toInt() and 0xFFFF]
    fun isWaterlogged(block: Short) = waterloggedTable[block.toInt() and 0xFFFF]
    fun isWood(block: Short)  = block.toInt() in 1000..1009
    fun isLeaf(block: Short)  = block.toInt() in 1020..1031
    fun isTransparent(block: Short) = transparentTable[block.toInt() and 0xFFFF]
    fun isWater(block: Short)       = waterTable[block.toInt() and 0xFFFF]
    fun isFalling(block: Short)     = fallingTable[block.toInt() and 0xFFFF]

    // face : 0=dessus, 1=dessous, 2=est(+X), 3=ouest(-X), 4=sud(+Z), 5=nord(-Z)
    // above : bloc voisin au-dessus (transitions biome côtés)
    // meta  : octet d'orientation du bloc (0 = défaut)
    fun getLayerForFace(id: Short, face: Int, above: Short, meta: Byte = 0): Int {
        val idx = id.toInt() and 0xFFFF
        when (orientModeTable[idx]) {
            ORIENT_AXIS -> when (meta.toInt()) {
                1 -> return when (face) { 2, 3 -> layerTopTable[idx]; else -> layerSideTable[idx] }
                2 -> return when (face) { 4, 5 -> layerTopTable[idx]; else -> layerSideTable[idx] }
            }
            ORIENT_FACING -> {
                val frontFace = when (meta.toInt()) { 0 -> 5; 1 -> 4; 2 -> 2; else -> 3 }
                if (face == frontFace) return layerFrontTable[idx]
            }
        }
        if (face == 1) return layerBottomTable[idx]
        if (face == 0) return layerTopTable[idx]
        if (above == 4000.toShort() || above == 4001.toShort()) return layerSideSandTable[idx]
        if (above == 5001.toShort() || above == 5000.toShort()) return layerSideSnowTable[idx]
        if (above == 0.toShort()) return layerSideGrassTable[idx]
        return layerSideTable[idx]
    }

    fun isOrientable(id: Short): Boolean = orientModeTable[id.toInt() and 0xFFFF] != ORIENT_NONE
    fun getOrientMode(id: Short): Byte   = orientModeTable[id.toInt() and 0xFFFF]

    fun getLayerForDecoration(id: Short): Int = layerTopTable[id.toInt() and 0xFFFF]

    fun getColor(id: Short): Int = defs[id]?.color ?: 0xFF444444.toInt()

    fun getHardness(id: Short): Float = defs[id]?.hardness ?: 1f

    fun getTextureTop(id: Short): String? = defs[id]?.textureTop

    fun getBitmap(id: Short): Bitmap? = topBitmapById[id]

    fun getSpriteMargin(id: Short): Float = defs[id]?.spriteMargin ?: 0.10f

    fun getSpriteHeight(id: Short): Float = defs[id]?.spriteHeight ?: 0.90f

    fun creativeList(): List<Short> =
        defs.values
            .filter { !it.water }
            .sortedBy { it.id }
            .map { it.id }

    fun all(): Collection<BlockDef> = defs.values
}
