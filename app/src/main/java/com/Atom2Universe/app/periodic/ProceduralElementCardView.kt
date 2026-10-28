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
import android.view.ViewConfiguration
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.gacha.rarityOf
import com.Atom2Universe.app.periodic.illuminated.EngravedScene
import com.Atom2Universe.app.periodic.illuminated.EngravedScenes
import com.Atom2Universe.app.periodic.illuminated.IlluminatedCard
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.min

/**
 * La carte d'un élément, à la manière d'un livre d'heures : le dessin vit dans
 * [IlluminatedCard], cette vue lui donne les textes traduits, le temps et la lumière du doigt.
 *
 * La carte se cuit hors du fil de l'écran (des milliers de hachures) ; tant qu'elle n'est pas
 * prête, seul le vélin s'affiche.
 */
class ProceduralElementCardView @JvmOverloads constructor(context: Context, attrs: android.util.AttributeSet? = null) : View(context, attrs) {
    /** Le même élément redonné (un appui de plus sur sa case) ne recuit rien. */
    var element = getPeriodicElements()[0]
        set(value) { if (labelled && value == field) return; field = value; updateLabels(); invalidate() }

    var motionEnabled = true
        set(value) { field = value; syncAnimation(); invalidate() }

    // Les textes se préparent au premier besoin : le nom traduit lit un gros fichier.
    private var labelled = false
    private lateinit var engraved: EngravedScene
    private val card = IlluminatedCard(fontsFor(context))

    val sceneNameRes: Int get() { ensureLabels(); return engraved.nameRes }
    private val motifNote: String get() { ensureLabels(); return context.getString(engraved.noteRes) }
    private val hazardNote: String get() = listOfNotNull(
        if(ElementCardHazards.isToxic(element.atomicNumber)) context.getString(R.string.card_badge_toxic) else null,
        if(ElementCardHazards.hasNoStableIsotope(element.atomicNumber)) context.getString(
            if(element.atomicNumber == 83) R.string.card_badge_radio_bismuth else R.string.card_badge_radioactive) else null
    ).joinToString("\n")
    val sceneNote: String get() = listOf(motifNote,hazardNote).filter { it.isNotEmpty() }.joinToString("\n")

    /** Le texte caché sous la carte : le nom du motif, ce que montre l'image, puis le lien avec l'élément. */
    val sceneExplanation: CharSequence get() = SpannableStringBuilder().apply {
        append(context.getString(sceneNameRes), StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append("\n").append(context.getString(engraved.explainRes))
        append("\n\n")
        append(sceneNote, StyleSpan(Typeface.ITALIC), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** La hauteur de la carte dessinée, en pixels, sans la marge laissée pour l'inclinaison. */
    val drawnHeight: Float get() = min(width / 372f, height / 538f) * IlluminatedCard.H

    /** Le temps de l'animation, en secondes ; il ne court que quand la carte est visible. */
    private var seconds = 0.35f
    /** La carte ne se redessine qu'à la cadence de sa scène ; le temps, lui, reste exact. */
    private var frameTime = 1f / 30
    private var sinceFrame = 0f
    private var animating = false
    private var lightX = 180f
    private var lightY = 220f
    private var artScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var bakingScale = 0f
    /** Une cuisson ratée (mémoire) n'est pas relancée à chaque image pour la même taille. */
    private var failedScale = 0f
    /** Seule la dernière cuisson demandée est posée ; les autres sont périmées. */
    private var bakeToken = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragged = false
    private val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF1E4C6.toInt() }
    private val animator = TimeAnimator().apply {
        setTimeListener { _, _, delta ->
            // Un retour d'arrière-plan ne doit pas faire sauter la scène d'un coup.
            val dt = min(delta, 100L) / 1000f
            seconds += dt
            sinceFrame += dt
            // Un peu de marge : les images de l'écran arrivent à la milliseconde près.
            if (sinceFrame >= frameTime - FRAME_SLACK) {
                sinceFrame = (sinceFrame - frameTime).coerceIn(0f, frameTime)
                invalidate()
            }
        }
    }

    init { isClickable = true }

    private fun ensureLabels() { if (!labelled) updateLabels() }

    private fun updateLabels() {
        labelled = true
        val z = element.atomicNumber
        engraved = EngravedScenes.forElement(z)
        frameTime = 1f / engraved.fps.coerceAtLeast(1)
        val locale = resources.configuration.locales[0]
        val rarity = rarityOf(z)
        val name = element.localizedName(context)
        val mass = context.getString(R.string.card_studio_mass,
            java.text.NumberFormat.getNumberInstance(locale).format(element.atomicMass))
        card.set(IlluminatedCard.Content(
            rarity = rarity,
            number = context.getString(R.string.card_medallion_number, z),
            name = name,
            symbol = element.symbol,
            mass = mass,
            rarityLabel = context.getString(rarity.nameRes).uppercase(locale),
            toxic = ElementCardHazards.isToxic(z),
            radioactive = ElementCardHazards.hasNoStableIsotope(z)
        ), engraved)
        bakingScale = 0f; failedScale = 0f; bakeToken++
        contentDescription = context.getString(R.string.card_studio_element, z, element.symbol, name) + "\n" + sceneNote
    }

    private fun syncAnimation() {
        val active = motionEnabled && isAttachedToWindow && windowVisibility == VISIBLE && isShown && hasWindowFocus() && ValueAnimator.areAnimatorsEnabled()
        animating = active
        if (active) {
            if (animator.isPaused) animator.resume() else if (!animator.isStarted) animator.start()
        } else if (animator.isStarted && !animator.isPaused) animator.pause()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation() }
    override fun onDetachedFromWindow() {
        animator.cancel(); animating = false
        card.release(); bakingScale = 0f; failedScale = 0f; bakeToken++
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
        ensureLabels()
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
        if (bakingScale == scale || failedScale == scale) return
        bakingScale = scale
        val token = ++bakeToken
        baker.execute {
            val baked = try { card.prepare(scale) } catch (e: Throwable) { null }
            post {
                if (token != bakeToken) return@post
                bakingScale = 0f
                if (baked == null) failedScale = scale
                else if (card.install(baked)) invalidate()
            }
        }
    }

    /** Pose la lumière sous le doigt ; ([x], [y]) en pixels de la vue. */
    fun moveLight(x: Float, y: Float) {
        lightX = ((x - offsetX) / artScale).coerceIn(0f, 360f)
        lightY = ((y - offsetY) / artScale).coerceIn(0f, 520f)
        // En mouvement, l'image suivante de la scène la prendra.
        if (!animating) invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    downX = event.x; downY = event.y; dragged = false
                    // Une grande carte garde le doigt ; la miniature laisse défiler ce qui la porte.
                    if (motionEnabled) parent?.requestDisallowInterceptTouchEvent(true)
                } else if (hypot(event.x - downX, event.y - downY) > touchSlop) dragged = true
                moveLight(event.x, event.y)
                rotationY = (lightX / 360f - 0.5f) * 9f
                rotationX = -(lightY / 520f - 0.5f) * 7f
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                rotationX = 0f; rotationY = 0f
                // Un glissé n'est pas un appui.
                if (event.actionMasked == MotionEvent.ACTION_UP && !dragged) performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    private companion object {
        const val FRAME_SLACK = .002f

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
