package com.Atom2Universe.app.games.caves.node

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject

internal object BlockRegistry {

    private val defs = HashMap<Short, BlockDef>()

    // Tables O(1) pour le hot path du rendu — indexées par id.toInt() and 0xFFFF
    private val decorationTable = BooleanArray(65536)
    private val transparentTable = BooleanArray(65536)
    private val waterTable = BooleanArray(65536)
    private val fallingTable = BooleanArray(65536)

    // Textures uniques ordonnées → index = couche GL dans la texture array
    private val textureOrder = mutableListOf<String>()
    private val textureIndexMap = HashMap<String, Int>()

    // Textures générées par le renderer (ex: torch, ward_stone)
    private val generatedProviders = HashMap<String, (Int) -> Bitmap>()

    fun load(assets: AssetManager) {
        if (defs.isNotEmpty()) return
        val files = assets.list("caves/blocks") ?: return
        for (file in files) {
            if (!file.endsWith(".json")) continue
            val json = assets.open("caves/blocks/$file").bufferedReader().readText()
            val def = BlockDef.fromJson(JSONObject(json))
            defs[def.id] = def
            val idx = def.id.toInt() and 0xFFFF
            if (def.decoration) decorationTable[idx] = true
            if (def.transparent) transparentTable[idx] = true
            if (def.water) waterTable[idx] = true
            if (def.falling) fallingTable[idx] = true
        }
    }

    fun registerGeneratedTexture(name: String, provider: (Int) -> Bitmap) {
        generatedProviders[name] = provider
    }

    fun buildTextureAtlas(assets: AssetManager, tileSize: Int): List<Bitmap> {
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
            def.layerSideGrass = def.textureSideGrass?.let { register(it) } ?: def.layerSide
            def.layerSideSand  = def.textureSideSand?.let  { register(it) } ?: def.layerSide
            def.layerSideSnow  = def.textureSideSnow?.let  { register(it) } ?: def.layerSide
        }

        return bitmaps
    }

    fun layerCount() = textureOrder.size

    fun get(id: Short): BlockDef? = defs[id]

    fun isDecoration(block: Short) = decorationTable[block.toInt() and 0xFFFF]
    fun isTransparent(block: Short) = transparentTable[block.toInt() and 0xFFFF]
    fun isWater(block: Short)       = waterTable[block.toInt() and 0xFFFF]
    fun isFalling(block: Short)     = fallingTable[block.toInt() and 0xFFFF]

    // face : 0=dessus, 1=dessous, 2-5=côtés
    // above : bloc voisin au-dessus (pour les transitions de biome sur les côtés)
    fun getLayerForFace(id: Short, face: Int, above: Short): Int {
        val def = defs[id] ?: return 0
        if (face == 1) return def.layerBottom
        if (face == 0) return def.layerTop
        // Faces latérales : sélection selon le voisin du dessus
        if (above == 4000.toShort() || above == 4001.toShort()) return def.layerSideSand   // SAND, REDSAND
        if (above == 5001.toShort() || above == 5000.toShort()) return def.layerSideSnow   // SNOW, ICE
        if (above == 0.toShort()) return def.layerSideGrass                                // AIR
        return def.layerSide
    }

    fun getLayerForDecoration(id: Short): Int = defs[id]?.layerTop ?: 0

    fun getColor(id: Short): Int = defs[id]?.color ?: 0xFF444444.toInt()

    fun getHardness(id: Short): Float = defs[id]?.hardness ?: 1f

    fun getTextureTop(id: Short): String? = defs[id]?.textureTop

    fun getSpriteMargin(id: Short): Float = defs[id]?.spriteMargin ?: 0.10f

    fun getSpriteHeight(id: Short): Float = defs[id]?.spriteHeight ?: 0.90f

    fun creativeList(): List<Short> =
        defs.values
            .filter { !it.water }
            .sortedBy { it.id }
            .map { it.id }

    fun all(): Collection<BlockDef> = defs.values
}
