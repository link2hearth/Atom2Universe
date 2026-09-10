package com.Atom2Universe.app.games.caves.render

import android.content.res.AssetManager
import android.graphics.*
import android.graphics.drawable.Drawable
import com.Atom2Universe.app.games.caves.node.ItemRarity

/** Icône indépendante de la sélection : la rareté reste lisible sur toutes les cases. */
internal class WeaponIconDrawable(assets: AssetManager, type: String, private val rarity: ItemRarity) : Drawable() {
    private val icon = synchronized(cache) {
        cache.getOrPut(type) {
            runCatching { assets.open("caves/weapon_icons/$type.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
                ?: runCatching { assets.open("caves/items/$type.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
                ?: Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
        }
    }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var opacity=255
    private var filter: ColorFilter?=null
    private val color=when(rarity) {
        ItemRarity.COMMON -> 0xFFAAAAAA.toInt()
        ItemRarity.MAGIC -> 0xFF4488FF.toInt()
        ItemRarity.RARE -> 0xFFFFDD00.toInt()
        ItemRarity.EPIC -> 0xFFCC44FF.toInt()
        ItemRarity.LEGENDARY -> 0xFFFF8800.toInt()
    }
    override fun draw(canvas: Canvas) {
        if(bounds.isEmpty) return
        val save=canvas.save()
        canvas.translate(bounds.left.toFloat(),bounds.top.toFloat())
        canvas.scale(bounds.width()/100f,bounds.height()/100f)
        paint.alpha=opacity;paint.colorFilter=filter;paint.style=Paint.Style.FILL
        paint.shader=LinearGradient(0f,0f,100f,100f,0xFF293648.toInt(),0xFF101822.toInt(),Shader.TileMode.CLAMP)
        canvas.drawRoundRect(2f,2f,98f,98f,9f,9f,paint)
        paint.shader=RadialGradient(50f,48f,62f,(color and 0x00FFFFFF) or 0x33000000,color and 0x00FFFFFF,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(3f,3f,97f,97f,8f,8f,paint)
        paint.shader=null
        canvas.drawBitmap(icon,null,RectF(2f,0f,98f,96f),paint)
        paint.color=color;paint.alpha=opacity;paint.style=Paint.Style.STROKE;paint.strokeWidth=2.6f
        canvas.drawRoundRect(2f,2f,98f,98f,9f,9f,paint)
        paint.style=Paint.Style.FILL
        // De un à cinq points : indice de rareté en complément de la couleur.
        val dots=rarity.ordinal+1
        for(i in 0 until dots) canvas.drawCircle(50f+(i-(dots-1)/2f)*7f,92f,2f,paint)
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { opacity=alpha;invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { filter=colorFilter;invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    companion object { private val cache=mutableMapOf<String,Bitmap>() }
}
