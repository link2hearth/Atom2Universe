package com.Atom2Universe.app.games.caves.node

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import com.Atom2Universe.app.games.caves.world.SpriteHitMask

internal object BlockRegistry {

    private val defs = HashMap<Short, BlockDef>()
    private val harvestDrops = HashMap<Short, Pair<Short, Int>>()

    fun harvestDrop(id: Short): Pair<Short, Int>? = harvestDrops[id]

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
    private var climateMasks = IntArray(0)
    var vividStyle = false
        private set
    private var knotLayers = IntArray(0)
    val torchLayers = IntArray(4)

    fun knotLayer(layer: Int): Int = knotLayers.getOrNull(layer) ?: layer

    fun climateMask(layer: Int): Int = climateMasks.getOrNull(layer) ?: 0

    // Textures générées par le renderer (ex: torch, ward_stone)
    private val generatedProviders = HashMap<String, (Int) -> Bitmap>()

    // Copies des faces top pour l'UI — stockées avant le recycle GL dans CaveRenderer
    private val topBitmapById = HashMap<Short, Bitmap>()
    private val decorationMasks = HashMap<Short, SpriteHitMask>()

    fun decorationMask(id: Short): SpriteHitMask? = decorationMasks[id]

    fun load(assets: AssetManager) {
        if (defs.isNotEmpty()) return
        FarmSoil.registerTextures()
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
        for (def in FarmShowcasePlants.definitions(requireNotNull(defs[7020.toShort()]))) {
            require(def.id !in defs) { "Duplicate farm showcase block ${def.id}" }
            defs[def.id] = def
            decorationTable[def.id.toInt()] = true
        }
        for (def in FarmItems.definitions(requireNotNull(defs[FarmSoil.HOE]))) {
            require(def.id !in defs) { "Duplicate farm item ${def.id}" }
            defs[def.id] = def
        }
        val byName = defs.values.associateBy { it.name }
        for (def in defs.values) {
            require(def.harvestCategory in setOf("recoverable", "covered_soil", "fractured_stone",
                "fragile", "liquid", "unharvestable_crop", "technical", "ore", "resource", "plant", "resource_block")) {
                "Unknown harvest category for ${def.name}: ${def.harvestCategory}"
            }
            require(def.dropCount > 0) { "Invalid drop count for ${def.name}" }
            require(def.placementRule in setOf("any", "solid", "soil", "farmland", "sand", "cactus", "reeds"))
            require(def.hardness > 0f && def.spriteMargin >= 0f && def.spriteMargin < .5f && def.spriteHeight > 0f)
            require(def.spriteWidth > 0f && def.spriteWidth.isFinite())
            require(def.blockHeight > 0f && def.blockHeight <= 1f)
            if (def.drop.isBlank()) continue
            val target = requireNotNull(byName[def.drop]) { "Unknown drop '${def.drop}' for ${def.name}" }
            harvestDrops[def.id] = target.id to def.dropCount
        }
    }

    fun registerGeneratedTexture(name: String, provider: (Int) -> Bitmap) {
        generatedProviders[name] = provider
    }

    fun buildTextureAtlas(assets: AssetManager, tileSize: Int, vivid: Boolean = false): List<Bitmap> {
        vividStyle = vivid
        // Réinitialise l'état texture pour chaque reconstruction (recréation de surface GL)
        textureIndexMap.clear()
        textureOrder.clear()
        topBitmapById.values.forEach { it.recycle() }
        topBitmapById.clear()
        decorationMasks.clear()
        val masksByLayer = HashMap<Int, SpriteHitMask>()

        val bitmaps = mutableListOf<Bitmap>()

        fun register(name: String): Int {
            textureIndexMap[name]?.let { return it }
            val idx = bitmaps.size
            textureIndexMap[name] = idx
            textureOrder += name
            bitmaps += when {
                generatedProviders.containsKey(name) -> generatedProviders[name]!!(tileSize)
                name.startsWith("cozy:") -> MeadowTextures.texture(name, tileSize, vivid = vivid)
                name.startsWith("Items/") -> assets.open("caves/items/${name.removePrefix("Items/")}").use { BitmapFactory.decodeStream(it) }
                else -> error("Unknown Cave World block texture: $name")
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
            if (def.decoration) {
                decorationMasks[def.id] = masksByLayer.getOrPut(def.layerTop) {
                    val pixels = IntArray(src.width * src.height)
                    src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
                    SpriteHitMask(src.width, src.height, pixels)
                }
            }
            topBitmapById[def.id] = src.copy(src.config ?: Bitmap.Config.ARGB_8888, false)
        }

        // Climate now travels with mesh vertices; only rare bark variants need extra layers.
        val baseCount = bitmaps.size
        climateMasks = IntArray(baseCount) { MeadowTextures.climateMask(textureOrder[it]) }
        knotLayers = IntArray(baseCount) { it }
        for (layer in 0 until baseCount) {
            val name = textureOrder[layer]
            if (MeadowTextures.hasKnotVariant(name)) {
                knotLayers[layer] = bitmaps.size
                textureOrder += "$name:knot"
                bitmaps += MeadowTextures.texture("$name:knot", tileSize, vivid = vivid)
            }
        }
        // Small opaque procedural materials for the volumetric torch; keep its inventory icon.
        val colors = intArrayOf(0xFF89502B.toInt(), 0xFF49434A.toInt(),
            0xFFFF941F.toInt(), 0xFFFFDF79.toInt())
        for (material in colors.indices) {
            torchLayers[material] = bitmaps.size
            textureOrder += "torch_model_$material"
            val pixels = IntArray(tileSize * tileSize) { index ->
                val color = colors[material]
                val px = index % tileSize; val py = index / tileSize
                val shade = when (material) {
                    0 -> if ((px / 3 + py / 11) % 3 == 0) .78f else 1f
                    1 -> if (py < tileSize / 5 || py > tileSize * 4 / 5) 1.3f else .85f
                    else -> 1f - .12f * py / tileSize
                }
                0xFF000000.toInt() or
                    ((((color shr 16) and 255) * shade).toInt().coerceAtMost(255) shl 16) or
                    ((((color shr 8) and 255) * shade).toInt().coerceAtMost(255) shl 8) or
                    (((color and 255) * shade).toInt().coerceAtMost(255))
            }
            bitmaps += Bitmap.createBitmap(pixels, tileSize, tileSize, Bitmap.Config.ARGB_8888)
        }
        return bitmaps
    }

    fun layerCount() = textureOrder.size

    fun get(id: Short): BlockDef? = defs[id]

    fun isDecoration(block: Short)  = decorationTable[block.toInt() and 0xFFFF]
    fun isWaterlogged(block: Short) = waterloggedTable[block.toInt() and 0xFFFF]
    fun isWood(block: Short)  = block.toInt() in 1000..1009
    fun isLeaf(block: Short)  = block.toInt() in 1020..1035
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

    fun getColor(id: Short): Int {
        val color = defs[id]?.color ?: 0xFF444444.toInt()
        return if (vividStyle) CavePalette.vivid(color) else color
    }

    fun getHardness(id: Short): Float = defs[id]?.hardness ?: 1f

    fun getTextureTop(id: Short): String? = defs[id]?.textureTop

    fun getBitmap(id: Short): Bitmap? = topBitmapById[id]

    fun getSpriteMargin(id: Short): Float = defs[id]?.let {
        (1f - (1f - 2f * it.spriteMargin) * it.spriteWidth) / 2f
    } ?: 0.10f

    fun getSpriteHeight(id: Short): Float = defs[id]?.spriteHeight ?: 0.90f

    fun creativeList(): List<Short> =
        defs.values
            .filter { !it.water && FarmShowcasePlants.sample(it.id) == null }
            .sortedBy { it.id }
            .map { it.id }

    fun all(): Collection<BlockDef> = defs.values
}
