package com.Atom2Universe.app.midi.practice.themes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Interface définissant un thème visuel complet pour le mode Practice.
 *
 * Un thème peut personnaliser :
 * - Le fond (statique ou animé)
 * - La forme et le style des notes tombantes
 * - Les couleurs du clavier piano
 * - Le style de la partition
 * - Les effets de particules
 */
interface PracticeTheme {

    /** Identifiant unique du thème */
    val id: String

    /** Nom d'affichage du thème */
    val displayName: String

    /** Description courte */
    val description: String

    // ========== COULEURS DE BASE ==========

    /** Couleurs de fond (top, bottom pour gradient) */
    fun getBackgroundColors(): Pair<Int, Int>

    /** Couleur de la hit zone */
    fun getHitZoneColor(): Int

    /** Couleur des lignes de grille */
    fun getGridLineColor(): Int

    // ========== NOTES TOMBANTES ==========

    /** Couleur de base pour une note (basée sur le pitch, 0-11) */
    fun getNoteColor(pitchClass: Int): Int

    /** Le thème utilise-t-il des formes personnalisées pour les notes ? */
    fun hasCustomNoteShape(): Boolean = false

    /** Dessine une note avec forme personnalisée (si hasCustomNoteShape = true) */
    fun drawNote(
        canvas: Canvas,
        rect: RectF,
        pitchClass: Int,
        velocity: Int,
        paint: Paint,
        cornerRadius: Float
    ) {
        // Implémentation par défaut : rectangle arrondi standard
        paint.color = getNoteColor(pitchClass)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    }

    /** Le thème a-t-il un effet glow sur les notes ? */
    fun hasGlowEffect(): Boolean = false

    /** Intensité du glow (0.0 - 1.0) */
    fun getGlowIntensity(): Float = 0.4f

    /** Rayon du glow en dp */
    fun getGlowRadiusDp(): Float = 8f

    // ========== FOND ANIMÉ ==========

    /** Le thème a-t-il un fond animé ? */
    fun hasAnimatedBackground(): Boolean = false

    /** Met à jour l'état de l'animation du fond */
    fun updateBackgroundAnimation(deltaMs: Long) {}

    /** Dessine le fond animé (appelé avant les notes) */
    fun drawAnimatedBackground(canvas: Canvas, width: Int, height: Int) {}

    /** Dessine les éléments de premier plan (appelé après les notes) */
    fun drawForegroundElements(canvas: Canvas, width: Int, height: Int) {}

    // ========== PARTICULES ==========

    /** Le thème utilise-t-il des particules ? */
    fun hasParticles(): Boolean = false

    /** Nombre de particules par hit */
    fun getParticlesPerHit(): Int = 12

    /** Durée de vie des particules en ms */
    fun getParticleLifetimeMs(): Long = 800L

    /** Couleur des particules pour une note */
    fun getParticleColor(noteColor: Int): Int = noteColor

    // ========== PIANO ==========

    /** Couleur des touches blanches */
    fun getWhiteKeyColor(): Int = 0xFFFFFFFF.toInt()

    /** Couleur des touches noires */
    fun getBlackKeyColor(): Int = 0xFF000000.toInt()

    /** Couleur des touches blanches pressées */
    fun getPressedWhiteKeyColor(): Int = 0xFFE0E0E0.toInt()

    /** Couleur des touches noires pressées */
    fun getPressedBlackKeyColor(): Int = 0xFF333333.toInt()

    /** Le piano a-t-il un style personnalisé ? */
    fun hasCustomPianoStyle(): Boolean = false

    /** Dessine une touche blanche personnalisée */
    fun drawWhiteKey(canvas: Canvas, rect: RectF, isPressed: Boolean, isActive: Boolean, paint: Paint) {}

    /** Dessine une touche noire personnalisée */
    fun drawBlackKey(canvas: Canvas, rect: RectF, isPressed: Boolean, isActive: Boolean, paint: Paint) {}

    // ========== PARTITION ==========

    /** Couleur de fond de la partition */
    fun getSheetMusicBackgroundColor(): Int = 0xFF919191.toInt()

    /** Couleur des lignes de portée */
    fun getStaffLineColor(): Int = 0x50FFFFFF

    /** Couleur de l'indicateur de position courante */
    fun getCurrentTimeIndicatorColor(): Int = 0xFF4CAF50.toInt()

    /** Couleur des notes de la partition */
    fun getSheetMusicNoteColor(): Int = 0xFF000000.toInt()

    /** Couleur des symboles de la partition (clés, altérations, 8va/8vb) - noir sur fond clair par défaut */
    fun getSheetMusicSymbolColor(): Int = 0xFF222222.toInt()

    // ========== LIFECYCLE ==========

    /** Appelé quand le thème est activé */
    fun onActivate() {}

    /** Appelé quand le thème est désactivé */
    fun onDeactivate() {}

    /** Libère les ressources du thème */
    fun release() {}
}

/**
 * Classe de base pour les thèmes avec implémentation par défaut
 */
abstract class BasePracticeTheme : PracticeTheme {

    // Paint réutilisables pour éviter les allocations
    protected val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val tempPath = Path()
    protected val tempRect = RectF()

    override fun release() {
        tempPath.reset()
    }
}
