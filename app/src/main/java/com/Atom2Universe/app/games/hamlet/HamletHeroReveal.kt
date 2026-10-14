package com.Atom2Universe.app.games.hamlet

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.hamlet.HamletUi.Weight
import com.Atom2Universe.app.util.followImmersiveMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * La révélation d'un héros : plein écran, courte pour un commun, de plus en plus longue et brillante avec la rareté.
 * Une légende est un événement : le cercle de runes s'allume dans le noir, une colonne de lumière monte, puis la
 * silhouette prend ses couleurs. Un appui pendant l'animation la saute ; sans effets, seule la dernière image reste.
 */
internal class HamletHeroReveal(private val context: Context, private val hero: Int, private val legend: Boolean,
                                private val effects: Boolean, private val haptics: Boolean, private val done: () -> Unit) {
    private val def = HamletHeroes.defs[hero]
    private val rarity = def.rarity
    private val tier = rarity.ordinal
    /** Durée de l'animation avant que le bouton n'apparaisse. */
    private val length = when {
        !effects -> 0f
        legend && rarity == HeroRarity.MYTHIC -> 7.5f
        legend -> 5.6f
        tier >= HeroRarity.EPIC.ordinal -> 2.8f
        tier >= HeroRarity.RARE.ordinal -> 2.0f
        else -> 1.4f
    }
    private val dialog = Dialog(context)
    private val view: RevealView
    private val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; alpha = 0f }
    private val button: View
    private var started = 0L
    private var finished = false
    private fun dp(v: Int) = HamletUi.dp(context, v)
    private fun byName(name: String): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return context.getString(if (id == 0) R.string.hamlet_title else id)
    }

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = FrameLayout(context).apply { setBackgroundColor(0xFF081311.toInt()) }
        view = RevealView(context)
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        val color = HamletHeroFx.light(rarity)
        fun line(text: CharSequence, size: Float, textColor: Int, bold: Boolean = false, top: Int = 0) = TextView(context).apply {
            this.text = text; textSize = size; setTextColor(textColor); gravity = Gravity.CENTER
            if (bold) setTypeface(null, Typeface.BOLD); setLineSpacing(dp(2).toFloat(), 1f)
            setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), 0xCC000000.toInt())
        }.also { texts.addView(it, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(top) }) }
        line(context.getString(if (legend) R.string.hamlet_reveal_legend else R.string.hamlet_reveal_new), 13f, HamletUi.GOLD, true)
        line(byName("hamlet_a_hero_$hero"), if (legend) 34f else 28f, color, true, 4)
        val faction = byName("hamlet_a_faction_${def.faction.ordinal}")
        line(byName("hamlet_a_rarity_${def.rarity.ordinal}") + context.getString(R.string.hamlet_ui_dot) + faction + context.getString(R.string.hamlet_ui_dot) +
            byName("hamlet_a_role_${def.role.name.lowercase()}"), 14f, 0xFFE9E2CF.toInt(), false, 4)
        line(byName("hamlet_a_hero_story_$hero"), 13f, 0xFFC9D3C6.toInt(), false, 10).apply { maxLines = 4 }
        button = HamletUi.button(context, context.getString(R.string.hamlet_reveal_continue), Weight.MAIN, glyph = "tick") { close() }
        button.visibility = View.INVISIBLE
        texts.addView(button, LinearLayout.LayoutParams(dp(220), -2).apply { topMargin = dp(16) })
        texts.setPadding(dp(24), 0, dp(24), dp(28))
        root.addView(texts, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.setOnClickListener { if (!finished) skip() }
        dialog.setContentView(root, ViewGroup.LayoutParams(-1, -1))
        dialog.setCancelable(false)
    }

    fun show() {
        dialog.followImmersiveMode()
        dialog.show()
        dialog.window?.apply { setLayout(-1, -1); setBackgroundDrawable(ColorDrawable(0)); clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); setWindowAnimations(0) }
        started = SystemClock.uptimeMillis()
        if (!effects) skip() else view.postOnAnimation(tickRunner)
    }

    private val tickRunner = object : Runnable {
        override fun run() {
            if (finished || !dialog.isShowing) return
            val t = (SystemClock.uptimeMillis() - started) / 1000f
            view.time = t
            view.invalidate()
            val textsAt = length * .62f
            if (t >= textsAt) texts.alpha = min(1f, (t - textsAt) / .5f)
            if (t >= view.flashAt && !view.flashed) { view.flashed = true; if (haptics) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
            if (t >= length) { finish(); return }
            view.postOnAnimation(this)
        }
    }

    private fun skip() { view.time = length + 1f; view.flashed = true; view.invalidate(); finish() }
    private fun finish() {
        finished = true; texts.alpha = 1f; view.time = length + 1f; view.invalidate()
        button.visibility = View.VISIBLE
        if (effects) HamletMotion.pop(button, 1.1f)
    }
    private fun close() { dialog.dismiss(); done() }

    /** Le décor et le héros ; tout se calcule à partir du temps écoulé. */
    private inner class RevealView(context: Context) : View(context) {
        var time = 0f
        var flashed = false
        val flashAt = when { legend && rarity == HeroRarity.MYTHIC -> 3.4f; legend -> 2.5f; else -> 0.1f }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val silhouette = Paint().apply { isFilterBitmap = false; colorFilter = PorterDuffColorFilter(0xFF050B0A.toInt(), PorterDuff.Mode.SRC_ATOP) }
        private val rim = Paint().apply { isFilterBitmap = false }
        private val rect = RectF()
        private val path = Path()
        private val sparks = Random(hero * 977 + 5).let { r -> List(70) { RevealSpark(r.nextFloat() * 2 * PI.toFloat(), .35f + r.nextFloat() * .9f, r.nextFloat() * .5f, r.nextInt(3)) } }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val cx = w / 2; val cy = h * .42f
            val big = min(w * .8f, h * .46f)
            val main = HamletHeroFx.main(rarity); val light = HamletHeroFx.light(rarity)
            // 1. la lueur de fond, qui monte avec le temps
            val glow = when { !effects -> 1f; legend -> smooth((time - 1.1f) / 1.6f); else -> smooth(time / .6f) }
            if (glow > 0f) {
                paint.shader = RadialGradient(cx, cy, max(w, h) * (.45f + .25f * glow), (((60 + tier * 25) * glow).toInt().coerceIn(0, 255) shl 24) or (main and 0xFFFFFF), 0, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, paint); paint.shader = null
            }
            // 2. les rayons, à partir de l'épique
            if (tier >= 2 && glow > 0f) {
                paint.style = Paint.Style.FILL
                val reach = w + h
                paint.color = (((30 + tier * 10) * glow).toInt() shl 24) or 0xFFFFFF
                path.reset()
                for (k in 0 until 14) {
                    val a = time * .22f + k * PI.toFloat() / 7
                    path.moveTo(cx, cy); path.lineTo(cx + cos(a) * reach, cy + sin(a) * reach); path.lineTo(cx + cos(a + .13f) * reach, cy + sin(a + .13f) * reach); path.close()
                }
                c.drawPath(path, paint)
            }
            // 3. le cercle de runes et la colonne de lumière d'une légende
            if (legend) {
                val ring = smooth(time / 1.2f)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2).toFloat()
                for (k in 0..2) {
                    paint.color = (((120 + 40 * k) * ring).toInt() shl 24) or (light and 0xFFFFFF)
                    val rx = big * (.55f + k * .13f) * (.6f + .4f * ring); val ry = rx * .28f
                    val spin = time * (.5f + k * .3f) * if (k % 2 == 0) 1f else -1f
                    for (seg in 0 until 12) {
                        val a0 = spin + seg * PI.toFloat() / 6; val a1 = a0 + PI.toFloat() / 9
                        rect.set(cx - rx, cy + big * .5f - ry, cx + rx, cy + big * .5f + ry)
                        c.drawArc(rect, Math.toDegrees(a0.toDouble()).toFloat(), Math.toDegrees((a1 - a0).toDouble()).toFloat(), false, paint)
                    }
                }
                val pillar = smooth((time - 1.4f) / 1.0f) * (1f - smooth((time - 3.6f) / 1.4f) * .6f)
                if (pillar > 0f) {
                    paint.style = Paint.Style.FILL
                    paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, cy + big * .5f, 0, (((170 * pillar).toInt()) shl 24) or (light and 0xFFFFFF), Shader.TileMode.CLAMP)
                    val half = big * .22f * pillar
                    c.drawRect(cx - half, 0f, cx + half, cy + big * .5f, paint); paint.shader = null
                }
            }
            // 4. le héros : silhouette sombre, puis couleurs
            val reveal = when { !effects -> 1f; legend -> smooth((time - flashAt) / .45f); else -> smooth((time - .15f) / .55f) }
            val rise = if (!effects) 1f else if (legend) smooth((time - .5f) / 1.4f) else overshoot(time / .7f)
            val size = big * (.7f + .3f * rise)
            val foot = cy + big * .52f + (1f - rise) * dp(40)
            if (reveal < 1f) {
                HamletCharacterArt.bounds(cx, foot, size, rect)
                silhouette.alpha = ((1f - reveal) * 255).toInt()
                c.drawBitmap(HamletCharacterArt.bitmap(true, hero), null, rect, silhouette)
            }
            if (reveal > 0f) HamletCharacterArt.draw(c, true, hero, cx, foot, size, time = time, alpha = (reveal * 255).toInt())
            // 5. les étincelles
            val burstAt = if (legend) flashAt else .1f
            if (effects && tier >= 1 && time > burstAt) {
                paint.style = Paint.Style.FILL
                val span = if (legend) 2.4f else 1.2f
                for (i in 0 until if (legend) sparks.size else min(sparks.size, 12 + tier * 10)) {
                    val s = sparks[i]
                    val age = time - burstAt - s.delay
                    if (age < 0f || age > span) continue
                    val d = s.speed * big * (age / span) * 1.3f
                    val px = cx + cos(s.angle) * d; val py = cy + big * .1f + sin(s.angle) * d * .8f + age * age * dp(12)
                    paint.color = (((1f - age / span) * 230).toInt() shl 24) or (if (s.size == 0) 0xFFFFFF else light and 0xFFFFFF)
                    val q = dp(2 + s.size).toFloat()
                    c.drawRect(px - q, py - q, px + q, py + q, paint)
                }
            }
            // 6. l'éclair blanc de la révélation
            if (effects && time >= flashAt && time < flashAt + .5f) {
                paint.style = Paint.Style.FILL; paint.color = (((1f - (time - flashAt) / .5f) * (if (legend) 255 else 90)).toInt() shl 24) or 0xFFFFFF
                c.drawRect(0f, 0f, w, h, paint)
            }
        }
        private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
        private fun overshoot(x: Float): Float { val t = x.coerceIn(0f, 1f); val s = 1.7f; return 1f + (s + 1f) * (t - 1f) * (t - 1f) * (t - 1f) + s * (t - 1f) * (t - 1f) }
    }

    companion object {
        /** Révèle plusieurs héros l'un après l'autre. */
        fun sequence(context: Context, heroes: List<Pair<Int, Boolean>>, effects: Boolean, haptics: Boolean, done: () -> Unit) {
            if (heroes.isEmpty()) { done(); return }
            HamletHeroReveal(context, heroes.first().first, heroes.first().second, effects, haptics) { sequence(context, heroes.drop(1), effects, haptics, done) }.show()
        }
    }
}

private class RevealSpark(val angle: Float, val speed: Float, val delay: Float, val size: Int)
