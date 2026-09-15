package com.Atom2Universe.app.periodic

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.Atom2Universe.app.R
import kotlin.math.*
import kotlin.random.Random

/** One reusable native renderer, no bitmap assets. Coordinates use a 360 × 520 artboard.
 * Scenes are decorative compositions, not scientific diagrams.
 */
class ProceduralElementCardView @JvmOverloads constructor(context: Context, attrs: android.util.AttributeSet? = null) : View(context, attrs) {
    var element = getPeriodicElements()[0]
        set(value) { field = value; updateLabels(); invalidate() }

    var motionEnabled = true
        set(value) { field = value; syncAnimation(); invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val art = ElementCardArt()
    val sceneNameRes: Int get() = when (art.scene) {
        ElementCardArt.Scene.STAR -> R.string.card_scene_star
        ElementCardArt.Scene.VAPOUR -> R.string.card_scene_vapour
        ElementCardArt.Scene.DISCHARGE -> R.string.card_scene_discharge
        ElementCardArt.Scene.FORGE -> R.string.card_scene_forge
        ElementCardArt.Scene.GEARS -> R.string.card_scene_gears
        ElementCardArt.Scene.FOUNDRY -> R.string.card_scene_foundry
        ElementCardArt.Scene.TREASURE -> R.string.card_scene_treasure
        ElementCardArt.Scene.GARDEN -> R.string.card_scene_garden
        ElementCardArt.Scene.MINERAL -> when(element.atomicNumber) {
            5 -> R.string.card_scene_boron
            15 -> R.string.card_scene_phosphorus
            16 -> R.string.card_scene_sulfur
            33 -> R.string.card_scene_arsenic
            34 -> R.string.card_scene_selenium
            51 -> R.string.card_scene_stibnite
            52 -> R.string.card_scene_tellurium
            53 -> R.string.card_scene_iodine
            83 -> R.string.card_scene_bismuth
            else -> R.string.card_scene_mineral
        }
        ElementCardArt.Scene.LIQUID -> R.string.card_scene_liquid
        ElementCardArt.Scene.CIRCUIT -> R.string.card_scene_circuit
        ElementCardArt.Scene.AURORA -> R.string.card_scene_aurora
        ElementCardArt.Scene.REACTOR -> R.string.card_scene_reactor
        ElementCardArt.Scene.ACCELERATOR -> R.string.card_scene_accelerator
        ElementCardArt.Scene.BATTERY -> R.string.card_scene_battery
        ElementCardArt.Scene.SALT -> R.string.card_scene_salt
        ElementCardArt.Scene.BONES -> R.string.card_scene_bones
        ElementCardArt.Scene.AIRCRAFT -> R.string.card_scene_aircraft
        ElementCardArt.Scene.CHROME -> R.string.card_scene_chrome
        ElementCardArt.Scene.SHIELD -> R.string.card_scene_shield
        ElementCardArt.Scene.MAGNET -> R.string.card_scene_magnet
        ElementCardArt.Scene.COIL -> R.string.card_scene_coil
        ElementCardArt.Scene.SPECIMEN -> R.string.card_scene_specimen
        ElementCardArt.Scene.BALLOON -> R.string.card_scene_balloon
        ElementCardArt.Scene.TELESCOPE -> R.string.card_scene_telescope
        ElementCardArt.Scene.WHEAT -> R.string.card_scene_wheat
        ElementCardArt.Scene.LUNGS -> R.string.card_scene_lungs
        ElementCardArt.Scene.TOOTH -> R.string.card_scene_tooth
        ElementCardArt.Scene.FLASH -> R.string.card_scene_flash
        ElementCardArt.Scene.FOIL -> R.string.card_scene_foil
        ElementCardArt.Scene.WATER -> R.string.card_scene_water
        ElementCardArt.Scene.BULB -> R.string.card_scene_bulb
        ElementCardArt.Scene.FRUIT -> R.string.card_scene_fruit
        ElementCardArt.Scene.BICYCLE -> R.string.card_scene_bicycle
        ElementCardArt.Scene.SPRING -> R.string.card_scene_spring
        ElementCardArt.Scene.DRYCELL -> R.string.card_scene_drycell
        ElementCardArt.Scene.FLAME -> R.string.card_scene_flame
        ElementCardArt.Scene.MELTING -> R.string.card_scene_melting
        ElementCardArt.Scene.COINS -> R.string.card_scene_coins
        ElementCardArt.Scene.CAN -> R.string.card_scene_can
        ElementCardArt.Scene.BARS -> R.string.card_scene_bars
        ElementCardArt.Scene.RING -> R.string.card_scene_ring
        ElementCardArt.Scene.CERAMIC -> R.string.card_scene_ceramic
    }
    private val motifNote: String get() = context.getString(when (art.scene) {
        ElementCardArt.Scene.STAR -> if (element.atomicNumber == 1) R.string.card_note_hydrogen else R.string.card_note_helium
        ElementCardArt.Scene.BATTERY -> R.string.card_note_battery
        ElementCardArt.Scene.SALT -> R.string.card_note_salt
        ElementCardArt.Scene.BONES -> R.string.card_note_bones
        ElementCardArt.Scene.AIRCRAFT -> R.string.card_note_aircraft
        ElementCardArt.Scene.CHROME -> R.string.card_note_chrome
        ElementCardArt.Scene.SHIELD -> R.string.card_note_shield
        ElementCardArt.Scene.MAGNET -> R.string.card_note_magnet
        ElementCardArt.Scene.COIL -> R.string.card_note_coil
        ElementCardArt.Scene.GARDEN -> R.string.card_note_garden
        ElementCardArt.Scene.VAPOUR -> R.string.card_note_diatomic
        ElementCardArt.Scene.ACCELERATOR -> R.string.card_note_research
        ElementCardArt.Scene.GEARS -> R.string.card_note_alloys
        ElementCardArt.Scene.SPECIMEN -> R.string.card_note_specimen
        ElementCardArt.Scene.BALLOON -> R.string.card_note_balloon
        ElementCardArt.Scene.TELESCOPE -> R.string.card_note_telescope
        ElementCardArt.Scene.WHEAT -> R.string.card_note_wheat
        ElementCardArt.Scene.LUNGS -> R.string.card_note_lungs
        ElementCardArt.Scene.TOOTH -> R.string.card_note_tooth
        ElementCardArt.Scene.FLASH -> R.string.card_note_flash
        ElementCardArt.Scene.FOIL -> R.string.card_note_foil
        ElementCardArt.Scene.WATER -> R.string.card_note_water
        ElementCardArt.Scene.BULB -> R.string.card_note_bulb
        ElementCardArt.Scene.FRUIT -> R.string.card_note_fruit
        ElementCardArt.Scene.BICYCLE -> R.string.card_note_bicycle
        ElementCardArt.Scene.SPRING -> R.string.card_note_spring
        ElementCardArt.Scene.DRYCELL -> R.string.card_note_drycell
        ElementCardArt.Scene.FLAME -> R.string.card_note_flame
        ElementCardArt.Scene.MELTING -> R.string.card_note_melting
        ElementCardArt.Scene.COINS -> R.string.card_note_coins
        ElementCardArt.Scene.CAN -> R.string.card_note_can
        ElementCardArt.Scene.BARS -> R.string.card_note_bars
        ElementCardArt.Scene.RING -> R.string.card_note_ring
        ElementCardArt.Scene.CERAMIC -> R.string.card_note_ceramic
        ElementCardArt.Scene.MINERAL -> when(element.atomicNumber) {
            15 -> R.string.card_note_phosphorus
            51 -> R.string.card_note_stibnite
            53 -> R.string.card_note_iodine
            83 -> R.string.card_note_bismuth
            else -> R.string.card_note_mineral
        }
        else -> R.string.card_note_art
    })
    private val hazardNote: String get() = listOfNotNull(
        if(ElementCardHazards.isToxic(element.atomicNumber)) context.getString(R.string.card_badge_toxic) else null,
        if(ElementCardHazards.hasNoStableIsotope(element.atomicNumber)) context.getString(
            if(element.atomicNumber == 83) R.string.card_badge_radio_bismuth else R.string.card_badge_radioactive) else null
    ).joinToString("\n")
    val sceneNote: String get() = listOf(motifNote,hazardNote).filter { it.isNotEmpty() }.joinToString("\n")
    private val clip = Path()
    private val serif = Typeface.create("serif", Typeface.BOLD)
    private var rarityLabel = ""
    private val mono = Typeface.create("monospace", Typeface.NORMAL)
    private val random = Random(118)
    private val stars = FloatArray(180) { random.nextFloat() }
    private var phase = 0f
    private var lightX = 180f
    private var lightY = 220f
    private var artScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var name = ""
    private var number = ""
    private var mass = ""
    private var edition = ""
    private var accent = Color.CYAN
    private var secondary = Color.MAGENTA
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 24000L; repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init { isClickable = true; updateLabels() }

    private fun updateLabels() {
        art.configure(element)
        rarityLabel = context.getString(com.Atom2Universe.app.crypto.gacha.rarityOf(element.atomicNumber).nameRes)
        name = element.localizedName(context)
        number = context.getString(R.string.card_studio_number, element.atomicNumber)
        mass = context.getString(R.string.card_studio_mass,
            java.text.NumberFormat.getNumberInstance(resources.configuration.locales[0]).format(element.atomicMass))
        if(element.atomicNumber == 1) {
            mass = context.getString(R.string.card_studio_isotope_mass,
                java.text.NumberFormat.getNumberInstance(resources.configuration.locales[0]).format(2.014101778))
        }
        edition = context.getString(R.string.card_studio_edition, element.atomicNumber)
        contentDescription = context.getString(R.string.card_studio_element, element.atomicNumber, element.symbol, name) + "\n" + sceneNote
        val hue = when (element.category) {
            "noble-gas" -> 268f; "actinide" -> 115f; "lanthanide" -> 320f
            "halogen" -> 178f; "alkali-metal" -> 12f; "alkaline-earth-metal" -> 32f
            "metalloid" -> 155f; "nonmetal" -> 205f
            else -> if (element.atomicNumber == 79) 43f else 215f
        } + (element.atomicNumber % 7 - 3) * 3f
        accent = Color.HSVToColor(floatArrayOf(hue, 0.57f, 1f))
        secondary = Color.HSVToColor(floatArrayOf((hue + 65f) % 360f, 0.68f, 1f))
        // Material-specific palettes override the family tint when colour is recognisable.
        if (element.atomicNumber == 16) {
            accent = 0xFFFFDD32.toInt()
            secondary = 0xFFECAF23.toInt()
        }
        if (element.atomicNumber == 6) {
            accent = 0xFF67CC78.toInt()
            secondary = 0xFFB6E788.toInt()
        }
    }

    private fun syncAnimation() {
        val active = motionEnabled && isAttachedToWindow && windowVisibility == VISIBLE && isShown && hasWindowFocus() && ValueAnimator.areAnimatorsEnabled()
        if (active) {
            if (animator.isPaused) animator.resume() else if (!animator.isStarted) animator.start()
        } else if (animator.isStarted && !animator.isPaused) animator.pause()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) syncAnimation()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); syncAnimation() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); syncAnimation() }

    private fun ink(color: Int, width: Float = 0f) {
        paint.reset(); paint.isAntiAlias = true; paint.color = color
        paint.style = if (width > 0f) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = width
    }
    private fun alpha(color: Int, opacity: Int) = (color and 0x00ffffff) or (opacity.coerceIn(0, 255) shl 24)
    private fun line(c: Canvas, x: Float, y: Float, x2: Float, y2: Float, color: Int, width: Float = 1f) {
        ink(color, width); c.drawLine(x, y, x2, y2, paint)
    }
    private fun glow(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        ink(color); paint.shader = RadialGradient(x, y, radius, intArrayOf(color, alpha(color, 0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, radius, paint); paint.shader = null
    }
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, font: Typeface, maxWidth: Float = 280f) {
        ink(color); paint.typeface = font; paint.textSize = size; paint.textAlign = Paint.Align.CENTER
        val measured = paint.measureText(value)
        if (measured > maxWidth) paint.textSize *= maxWidth / measured
        c.drawText(value, x, y, paint)
    }
    private fun diamond(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        path.reset(); path.moveTo(x, y - radius); path.lineTo(x + radius * 0.65f, y)
        path.lineTo(x, y + radius); path.lineTo(x - radius * 0.65f, y); path.close()
        ink(color); c.drawPath(path, paint)
        line(c, x, y - radius, x, y + radius, alpha(Color.WHITE, 160))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        artScale = min(width / 384f, height / 550f)
        if (artScale <= 0f) return
        offsetX = (width - 360f * artScale) / 2; offsetY = (height - 520f * artScale) / 2
        canvas.save(); canvas.translate(offsetX, offsetY); canvas.scale(artScale, artScale)
        glow(canvas, 180f, 270f, 230f, alpha(accent, 32))
        clip.reset(); clip.addRoundRect(4f, 4f, 356f, 516f, 23f, 23f, Path.Direction.CW)
        canvas.clipPath(clip)
        ink(Color.BLACK)
        paint.shader = LinearGradient(0f, 0f, 360f, 520f,
            intArrayOf(0xFF171C30.toInt(), 0xFF080C17.toInt(), alpha(accent, 255) and 0xff242424.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 360f, 520f, paint)
        glow(canvas, lightX, lightY, 225f, alpha(accent, 52))
        glow(canvas, 280f, 350f, 155f, alpha(secondary, 35))
        val t = phase * (2 * PI).toFloat()
        // No dust in an atomic diagram: every point must be an actual counted particle.
        for (i in 0 until if (art.scene == ElementCardArt.Scene.STAR) 0 else 60) {
            val x = 28 + stars[i * 3] * 304
            val y = 100 + stars[i * 3 + 1] * 275 + sin(t + i) * 8f
            val a = (55 + 130 * (0.5 + 0.5 * sin(t * 2 + i))).toInt()
            ink(alpha(accent, a)); canvas.drawCircle(x, y, 0.5f + stars[i * 3 + 2], paint)
        }
        art.draw(canvas, accent, secondary, t)
        art.frame(canvas, com.Atom2Universe.app.crypto.gacha.rarityOf(element.atomicNumber), t)
        if (art.scene == ElementCardArt.Scene.STAR) {
            text(canvas, context.getString(if(element.atomicNumber == 1) R.string.card_atom_hydrogen else R.string.card_atom_helium),
                180f, 308f, 11f, 0xFFDADCE4.toInt(), mono)
        }
        // Foil sheen follows both the slow animation and the finger.
        val sweep = -400f + phase * 1150f + (lightX - 180f) * 0.6f
        ink(Color.WHITE)
        paint.shader = LinearGradient(sweep, 0f, sweep + 175f, 100f,
            intArrayOf(0x00FFFFFF, 0x13FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 360f, 520f, paint)
        val trim = 0xFFE4C88C.toInt()
        art.badges(canvas)
        text(canvas, number, 180f, 49f, 11f, trim, mono)
        text(canvas, rarityLabel.uppercase(resources.configuration.locales[0]),
            180f, 78f, 12f, 0xFFDEE3EF.toInt(), mono)
        // Dark lower panel keeps metadata legible over the effects.
        ink(Color.BLACK); paint.shader = LinearGradient(0f, 353f, 0f, 500f,
            intArrayOf(0x00080B14, 0xF0080B14.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(28f, 350f, 332f, 499f, paint)
        text(canvas, element.symbol, 180f, 374f, 66f, 0xFFF8F3E8.toInt(), serif)
        text(canvas, name, 180f, 413f, 25f, Color.WHITE, serif)
        line(canvas, 100f, 430f, 260f, 430f, alpha(trim, 100))
        diamond(canvas, 180f, 430f, 4f, trim)
        text(canvas, mass, 180f, 455f, 10f, 0xFFBEC8D9.toInt(), mono)
        text(canvas, edition, 180f, 483f, 8f, trim, mono)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lightX = ((event.x - offsetX) / artScale).coerceIn(0f, 360f)
                lightY = ((event.y - offsetY) / artScale).coerceIn(0f, 520f)
                rotationY = (lightX / 360f - 0.5f) * 9f
                rotationX = -(lightY / 520f - 0.5f) * 7f
                invalidate(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                rotationX = 0f; rotationY = 0f
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
