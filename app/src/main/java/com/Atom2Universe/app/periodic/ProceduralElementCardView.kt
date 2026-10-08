package com.Atom2Universe.app.periodic

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.gacha.rarityOf
import com.Atom2Universe.app.periodic.illuminated.EngravedScene
import com.Atom2Universe.app.periodic.illuminated.EngravedScenes
import com.Atom2Universe.app.periodic.illuminated.IlluminatedCard
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * La carte d'un élément, à la manière d'un livre d'heures : le dessin vit dans
 * [IlluminatedCard], cette vue lui donne les textes traduits, le temps et la lumière du doigt.
 *
 * Les éléments qui n'ont pas encore leur gravure ([EngravedScenes]) montrent leur ancienne scène
 * ([ElementCardArt]) dans la fenêtre de la miniature, sur fond d'azur.
 *
 * La carte se cuit hors du fil de l'écran (des milliers de hachures) ; tant qu'elle n'est pas
 * prête, seul le vélin s'affiche.
 */
class ProceduralElementCardView @JvmOverloads constructor(context: Context, attrs: android.util.AttributeSet? = null) : View(context, attrs) {
    var element = getPeriodicElements()[0]
        set(value) { field = value; updateLabels(); invalidate() }

    var motionEnabled = true
        set(value) { field = value; syncAnimation(); invalidate() }

    private val art = ElementCardArt()
    private var engraved: EngravedScene? = null
    private val card = IlluminatedCard(fontsFor(context))
    private var accent = Color.CYAN
    private var secondary = Color.MAGENTA

    val sceneNameRes: Int get() = engraved?.nameRes ?: when (art.scene) {
        ElementCardArt.Scene.CYCLOTRON -> R.string.card_scene_cyclotron
        ElementCardArt.Scene.CONTACTS -> R.string.card_scene_contacts
        ElementCardArt.Scene.CATALYTIC_CONVERTER -> R.string.card_scene_catalytic_converter
        ElementCardArt.Scene.HYDROGEN_MEMBRANE -> R.string.card_scene_hydrogen_membrane
        ElementCardArt.Scene.PIGMENTS -> R.string.card_scene_pigments
        ElementCardArt.Scene.TOUCHSCREEN -> R.string.card_scene_touchscreen
        ElementCardArt.Scene.INFRARED -> R.string.card_scene_infrared
        ElementCardArt.Scene.ARSENIC -> R.string.card_scene_arsenic
        ElementCardArt.Scene.LIGHT_METER -> R.string.card_scene_light_meter
        ElementCardArt.Scene.BROMINE -> R.string.card_scene_bromine
        ElementCardArt.Scene.CAMERA_FLASH -> R.string.card_scene_camera_flash
        ElementCardArt.Scene.ATOMIC_CLOCK -> R.string.card_scene_atomic_clock
        ElementCardArt.Scene.SIGNAL_FLARE -> R.string.card_scene_signal_flare
        ElementCardArt.Scene.YAG_LASER -> R.string.card_scene_yag_laser
        ElementCardArt.Scene.CERAMIC_KNIFE -> R.string.card_scene_ceramic_knife
        ElementCardArt.Scene.SUPERCONDUCTOR -> R.string.card_scene_superconductor
        ElementCardArt.Scene.DRILL -> R.string.card_scene_drill
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
    private val motifNote: String get() = context.getString(engraved?.noteRes ?: when (art.scene) {
        ElementCardArt.Scene.CYCLOTRON -> R.string.card_note_cyclotron
        ElementCardArt.Scene.CONTACTS -> R.string.card_note_contacts
        ElementCardArt.Scene.CATALYTIC_CONVERTER -> R.string.card_note_catalytic_converter
        ElementCardArt.Scene.HYDROGEN_MEMBRANE -> R.string.card_note_hydrogen_membrane
        ElementCardArt.Scene.PIGMENTS -> R.string.card_note_pigments
        ElementCardArt.Scene.TOUCHSCREEN -> R.string.card_note_touchscreen
        ElementCardArt.Scene.INFRARED -> R.string.card_note_infrared
        ElementCardArt.Scene.ARSENIC -> R.string.card_note_arsenic
        ElementCardArt.Scene.LIGHT_METER -> R.string.card_note_light_meter
        ElementCardArt.Scene.BROMINE -> R.string.card_note_bromine
        ElementCardArt.Scene.CAMERA_FLASH -> R.string.card_note_camera_flash
        ElementCardArt.Scene.ATOMIC_CLOCK -> R.string.card_note_atomic_clock
        ElementCardArt.Scene.SIGNAL_FLARE -> R.string.card_note_signal_flare
        ElementCardArt.Scene.YAG_LASER -> R.string.card_note_yag_laser
        ElementCardArt.Scene.CERAMIC_KNIFE -> R.string.card_note_ceramic_knife
        ElementCardArt.Scene.SUPERCONDUCTOR -> R.string.card_note_superconductor
        ElementCardArt.Scene.DRILL -> R.string.card_note_drill
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

    /** Le texte caché sous la carte : le nom du motif, ce que montre l'image, puis le lien avec l'élément. */
    val sceneExplanation: CharSequence get() = SpannableStringBuilder().apply {
        append(context.getString(sceneNameRes), StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        engraved?.let { append("\n").append(context.getString(it.explainRes)) }
        append("\n\n")
        append(sceneNote, StyleSpan(Typeface.ITALIC), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** La hauteur de la carte dessinée, en pixels, sans la marge laissée pour l'inclinaison. */
    val drawnHeight: Float get() = min(width / 372f, height / 538f) * IlluminatedCard.H

    /** Le temps de l'animation, en secondes ; il ne court que quand la carte est visible. */
    private var seconds = 0.35f
    private var lightX = 180f
    private var lightY = 220f
    private var artScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var bakingScale = 0f
    private val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF1E4C6.toInt() }
    private val animator = TimeAnimator().apply {
        setTimeListener { _, _, delta ->
            // Un retour d'arrière-plan ne doit pas faire sauter la scène d'un coup.
            seconds += min(delta, 100L) / 1000f
            invalidate()
        }
    }

    init { isClickable = true; updateLabels() }

    private fun updateLabels() {
        val z = element.atomicNumber
        art.configure(element)
        engraved = EngravedScenes.forElement(z)
        val locale = resources.configuration.locales[0]
        val rarity = rarityOf(z)
        val name = element.localizedName(context)
        val mass = context.getString(R.string.card_studio_mass,
            java.text.NumberFormat.getNumberInstance(locale).format(element.atomicMass))
        computeLegacyAccents()
        card.set(IlluminatedCard.Content(
            rarity = rarity,
            number = context.getString(R.string.card_medallion_number, z),
            name = name,
            symbol = element.symbol,
            mass = mass,
            rarityLabel = context.getString(rarity.nameRes).uppercase(locale),
            toxic = ElementCardHazards.isToxic(z),
            radioactive = ElementCardHazards.hasNoStableIsotope(z)
        ), engraved) { c, t ->
            // L'ancienne scène tournait sur une phase de 24 s, en radians.
            art.draw(c, accent, secondary, (t / 24f % 1f) * (2 * Math.PI).toFloat())
        }
        bakingScale = 0f
        contentDescription = context.getString(R.string.card_studio_element, z, element.symbol, name) + "\n" + sceneNote
    }

    /** Les teintes des anciennes scènes, tant qu'elles servent encore. */
    private fun computeLegacyAccents() {
        val hue = when (element.category) {
            "noble-gas" -> 268f; "actinide" -> 115f; "lanthanide" -> 320f
            "halogen" -> 178f; "alkali-metal" -> 12f; "alkaline-earth-metal" -> 32f
            "metalloid" -> 155f; "nonmetal" -> 205f
            else -> if (element.atomicNumber == 79) 43f else 215f
        } + (element.atomicNumber % 7 - 3) * 3f
        accent = Color.HSVToColor(floatArrayOf(hue, 0.57f, 1f))
        secondary = Color.HSVToColor(floatArrayOf((hue + 65f) % 360f, 0.68f, 1f))
        if (element.atomicNumber == 16) { accent = 0xFFFFDD32.toInt(); secondary = 0xFFECAF23.toInt() }
        if (element.atomicNumber == 6) { accent = 0xFF67CC78.toInt(); secondary = 0xFFB6E788.toInt() }
        if (ScientificElementCardArt.supports(element.atomicNumber)) {
            accent = ScientificElementCardArt.accent(element.atomicNumber)
            secondary = ScientificElementCardArt.secondary(element.atomicNumber)
        }
        if (IndustrialElementCardArt.supports(element.atomicNumber)) {
            accent = IndustrialElementCardArt.accent(element.atomicNumber)
            secondary = IndustrialElementCardArt.secondary(element.atomicNumber)
        }
    }

    private fun syncAnimation() {
        val active = motionEnabled && isAttachedToWindow && windowVisibility == VISIBLE && isShown && hasWindowFocus() && ValueAnimator.areAnimatorsEnabled()
        if (active) {
            if (animator.isPaused) animator.resume() else if (!animator.isStarted) animator.start()
        } else if (animator.isStarted && !animator.isPaused) animator.pause()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation() }
    override fun onDetachedFromWindow() {
        animator.cancel()
        card.release(); bakingScale = 0f
        super.onDetachedFromWindow()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) syncAnimation()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); syncAnimation() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); syncAnimation() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Une petite marge autour de la carte pour l'inclinaison au doigt.
        artScale = min(width / 372f, height / 538f)
        if (artScale <= 0f) return
        offsetX = (width - IlluminatedCard.W * artScale) / 2; offsetY = (height - IlluminatedCard.H * artScale) / 2
        canvas.save(); canvas.translate(offsetX, offsetY); canvas.scale(artScale, artScale)
        // Une miniature se cuit sur place ; une grande carte, sur un fil de travail.
        val small = artScale < .7f
        if (!card.draw(canvas, artScale, seconds, lightX, lightY, bakeNow = small)) {
            canvas.drawRoundRect(4f, 4f, IlluminatedCard.W - 4f, IlluminatedCard.H - 4f, 17f, 17f, placeholder)
            requestBake(artScale)
        }
        canvas.restore()
    }

    private fun requestBake(scale: Float) {
        if (bakingScale == scale) return
        bakingScale = scale
        baker.execute {
            val baked = try { card.prepare(scale) } catch (e: Throwable) { null }
            post {
                if (baked != null && baked.scale == bakingScale && card.install(baked)) invalidate()
                else if (bakingScale == scale) bakingScale = 0f
            }
        }
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

    private companion object {
        /** Un seul fil de cuisson pour toutes les cartes : elles passent l'une après l'autre. */
        val baker = Executors.newSingleThreadExecutor { r -> Thread(r, "carte-enluminee").apply { isDaemon = true } }

        @Volatile private var fonts: IlluminatedCard.Fonts? = null

        fun fontsFor(context: Context): IlluminatedCard.Fonts = fonts ?: IlluminatedCard.Fonts(
            // Le nom en lettres gothiques, le symbole et les petites capitales en romain.
            title = try { Typeface.createFromAsset(context.applicationContext.assets, "fonts/Chomsky.otf") }
                catch (e: RuntimeException) { Typeface.create(Typeface.SERIF, Typeface.BOLD) },
            letter = Typeface.create(Typeface.SERIF, Typeface.BOLD),
            caps = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        ).also { fonts = it }
    }
}
