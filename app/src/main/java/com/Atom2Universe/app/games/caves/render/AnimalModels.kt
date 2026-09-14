package com.Atom2Universe.app.games.caves.render

/** Modèles et palettes mis en cache : aucune reconstruction de géométrie par image. */
internal object AnimalModels {
    private val wool = intArrayOf(0xFFF0EEE8.toInt(), 0xFFBFC0C2.toInt(), 0xFF83858B.toInt(), 0xFF44464D.toInt())
    private val patches = intArrayOf(0xFF45444B.toInt(), 0xFF947263.toInt(), 0xFFC48769.toInt())
    private val feathers = intArrayOf(0xFFF4EADB.toInt(), 0xFFC7A17F.toInt(), 0xFF68636B.toInt())
    private val skin = intArrayOf(0xFFE9BABB.toInt(), 0xFFCDAAA2.toInt(), 0xFFBFB3B5.toInt())
    private val eye = 0xFF303039.toInt()
    private val pink = 0xFFE0A8AB.toInt()
    private val cream = 0xFFF2E8D5.toInt()

    fun coatCount(species: String) = if (species == "sheep") 4 else 3

    private val cache by lazy {
        buildMap {
            for (species in listOf("sheep", "cow", "chicken", "pig"))
                for (young in listOf(false, true)) for (coat in 0 until coatCount(species)) {
                    val adult = when (species) {
                        "chicken" -> chicken(coat, young)
                        "pig" -> pig(coat)
                        else -> ruminant(species == "cow", coat, young)
                    }
                    put(Triple(species, young, coat), if (young) juvenile(adult) else adult)
                }
        }
    }

    fun get(species: String, young: Boolean, coat: Int): MobModel =
        cache.getValue(Triple(species, young, coat.coerceIn(0, coatCount(species)-1)))

    private fun p(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: Int,
                  limb: Limb = Limb.NONE, side: Int = 0, pivot: Float = y+h/2) =
        MobPart(x, y, z, w, h, d, color, limb, side, pivot)

    /** Corps raccourci, petites pattes, tête relativement plus grande : pas une réduction uniforme. */
    private fun juvenile(model: MobModel): MobModel {
        val parts = model.parts.map { part ->
            val head = part.limb == Limb.HEAD
            val factor = if (head) .82f else .58f
            val y = if (head) part.pivotY*.62f+(part.cy-part.pivotY)*factor else part.cy*.58f
            val z = if (head) model.headPivotZ*.62f+(part.cz-model.headPivotZ)*factor else part.cz*.58f
            MobPart(part.cx*factor, y, z, part.w*factor, part.h*factor, part.d*factor,
                part.color, part.limb, part.side, part.pivotY*if(head) .62f else .58f)
        }
        return MobModel(parts, parts.maxOf { it.cy+it.h/2 }, headPivotZ=model.headPivotZ*.62f,
            feedingDrop=model.feedingDrop*.55f)
    }

    private fun legs(parts: MutableList<MobPart>, color: Int, height: Float, rear: Float, front: Float,
                     hoof: Int = 0xFF79747D.toInt()) {
        for (x in listOf(-4.5f, 4.5f)) for (z in listOf(rear, front)) {
            val side = if (x*z>0) 1 else -1
            parts += p(x,height/2,z,3f,height,3f,color,Limb.LEG,side,height)
            parts += p(x,.8f,z,3.2f,1.6f,3.2f,hoof,Limb.LEG,side,height)
        }
    }

    private fun ruminant(cow: Boolean, coat: Int, young: Boolean): MobModel {
        val body = if (cow) cream else wool[coat]
        val patch = if (cow) patches[coat] else body
        val parts = mutableListOf(p(0f,13f,-1f,13f,12f,21f,body))
        val sheepSkin = 0xFFDFC4B6.toInt()
        legs(parts, if(cow) patch else sheepSkin, 8f, -8f, 6f,
            if(cow) 0xFF79747D.toInt() else 0xFF625A60.toInt())
        parts += p(0f,17f,10f,8f,8f,8f,patch,Limb.HEAD,pivot=18f)
        if (cow) {
            parts += p(0f,14.5f,14f,7f,4f,3f,pink,Limb.HEAD,pivot=18f)
        } else {
            // Chanfrein étroit et allongé, avec deux narines mates sur le bout du nez.
            parts += p(0f,14.5f,15f,4.5f,3.5f,5f,sheepSkin,Limb.HEAD,pivot=18f)
            for (side in listOf(-1,1))
                parts += p(side*1.1f,15.5f,17.55f,.65f,.45f,.2f,
                    0xFF8B7774.toInt(),Limb.HEAD,pivot=18f)
        }
        for (side in listOf(-1,1)) {
            parts += p(side*2.5f,18f,14.2f,1.3f,1.5f,.5f,eye,Limb.HEAD,pivot=18f)
            parts += p(side*5.5f,18f,10f,4f,2f,3f,pink,Limb.HEAD,pivot=18f)
        }
        parts += p(0f,12f,-12f,2f,if(cow) 9f else 4f,2f,patch,Limb.TAIL,pivot=17f)
        if (cow) {
            // Taches irrégulières sur les deux flancs et sur le dos.
            parts += p(-6.6f,14f,-4f,.4f,7f,8f,patch)
            parts += p(-6.6f,16f,1f,.4f,3f,4f,patch)
            parts += p(6.6f,12f,3f,.4f,6f,7f,patch)
            parts += p(6.6f,15f,-7f,.4f,5f,4f,patch)
            parts += p(-2f,19.1f,-5f,7f,.3f,7f,patch)
            if (!young) for (side in listOf(-1,1))
                parts += p(side*3f,22f,9f,2f,3f,2f,cream,Limb.HEAD,pivot=18f)
        } else {
            // Relief de laine, toujours dans la même gamme neutre.
            parts += p(0f,19f,-2f,11f,3f,17f,body)
        }
        return MobModel(parts,parts.maxOf { it.cy+it.h/2 })
    }

    private fun chicken(coat: Int, young: Boolean): MobModel {
        val feather = if (young) intArrayOf(0xFFF4DEA0.toInt(),0xFFE4C58F.toInt(),0xFFD1C5AA.toInt())[coat] else feathers[coat]
        val beak = 0xFFE2B16E.toInt()
        val parts = mutableListOf(p(0f,8f,-1f,8f,9f,11f,feather))
        for (side in listOf(-1,1)) {
            parts += p(side*2f,2f,0f,1.2f,4f,1.2f,beak,Limb.LEG,side,4f)
            parts += p(side*2f,.5f,1f,2.5f,1f,3f,beak,Limb.LEG,side,4f)
            parts += p(side*4.5f,8f,-1f,2f,6f,8f,feather,Limb.WING,side,11f)
            parts += p(side*2f,14f,7.2f,1f,1.2f,.5f,eye,Limb.HEAD,pivot=13f)
        }
        parts += p(0f,13.5f,4f,6f,6f,6f,feather,Limb.HEAD,pivot=13f)
        parts += p(0f,12.8f,8f,3f,2f,3f,beak,Limb.HEAD,pivot=13f)
        parts += p(0f,10f,-7f,5f,5f,3f,feather,Limb.TAIL,pivot=11f)
        if (!young) {
            parts += p(0f,17.3f,4f,1.5f,2.5f,4f,0xFFCF8B8D.toInt(),Limb.HEAD,pivot=13f)
            parts += p(0f,10.8f,6.7f,2f,2f,1f,0xFFCF8B8D.toInt(),Limb.HEAD,pivot=13f)
        }
        return MobModel(parts,parts.maxOf { it.cy+it.h/2 },headPivotZ=2f,feedingDrop=4.5f)
    }

    private fun pig(coat: Int): MobModel {
        val color=skin[coat]
        val parts=mutableListOf(p(0f,10f,-1f,14f,11f,19f,color))
        legs(parts,color,5f,-7f,5f)
        parts += p(0f,11f,9f,10f,9f,8f,color,Limb.HEAD,pivot=13f)
        parts += p(0f,9f,14f,7f,4f,3f,pink,Limb.HEAD,pivot=13f)
        for (side in listOf(-1,1)) {
            parts += p(side*2f,9f,15.6f,1f,1.1f,.3f,0xFF9E707B.toInt(),Limb.HEAD,pivot=13f)
            parts += p(side*3.5f,12f,13.2f,1.2f,1.3f,.4f,eye,Limb.HEAD,pivot=13f)
            parts += p(side*4f,16f,9f,3f,4f,2f,pink,Limb.HEAD,pivot=13f)
        }
        // Trois petits segments forment une boucle de queue.
        parts += p(0f,11f,-11f,1.3f,1.3f,3f,pink,Limb.TAIL,pivot=12f)
        parts += p(1f,12f,-12f,1.3f,3f,1.3f,pink,Limb.TAIL,pivot=12f)
        parts += p(0f,13f,-12f,2f,1.3f,1.3f,pink,Limb.TAIL,pivot=12f)
        return MobModel(parts,18f,headPivotZ=7f,feedingDrop=2.5f)
    }
}
