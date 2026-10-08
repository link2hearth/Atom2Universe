package com.Atom2Universe.app.midi.practice.themes

import android.graphics.Color

/**
 * Thèmes gratuits (correspondant aux anciens VisualStyle)
 */

/**
 * Thème Classique - Simple et épuré
 */
class ClassicTheme : BasePracticeTheme() {
    override val id = "classic"
    override val displayName = "Classique"
    override val description = "Style simple et épuré"


    override fun getBackgroundColors() = Pair(
        Color.parseColor("#1A1A2E"),
        Color.parseColor("#16213E")
    )

    override fun getHitZoneColor() = Color.parseColor("#4CAF50")
    override fun getGridLineColor() = Color.parseColor("#15FFFFFF")

    override fun getNoteColor(pitchClass: Int): Int {
        // Vert classique pour toutes les notes
        return Color.parseColor("#4CAF50")
    }
}

/**
 * Thème Arc-en-ciel - Couleurs chromatiques
 */
class RainbowTheme : BasePracticeTheme() {
    override val id = "rainbow"
    override val displayName = "Arc-en-ciel"
    override val description = "Couleurs arc-en-ciel basées sur la note"


    companion object {
        val CHROMATIC_COLORS = intArrayOf(
            0xFFE40303.toInt(),  // C  - Rouge
            0xFFFF5500.toInt(),  // C# - Rouge-Orange
            0xFFFF8C00.toInt(),  // D  - Orange
            0xFFFFBB00.toInt(),  // D# - Orange-Jaune
            0xFFFFED00.toInt(),  // E  - Jaune
            0xFF8BC34A.toInt(),  // F  - Jaune-Vert
            0xFF008026.toInt(),  // F# - Vert
            0xFF009688.toInt(),  // G  - Vert-Bleu
            0xFF004DFF.toInt(),  // G# - Bleu
            0xFF2E00FF.toInt(),  // A  - Bleu-Violet
            0xFF750787.toInt(),  // A# - Violet
            0xFFAA00AA.toInt()   // B  - Violet-Rouge
        )
    }

    override fun getBackgroundColors() = Pair(
        Color.parseColor("#0D0D1A"),
        Color.parseColor("#1A1A2E")
    )

    override fun getHitZoneColor() = Color.parseColor("#00E5CC")
    override fun getGridLineColor() = Color.parseColor("#15FFFFFF")

    override fun getNoteColor(pitchClass: Int) = CHROMATIC_COLORS[pitchClass % 12]

    override fun hasGlowEffect() = true
    override fun hasParticles() = true
}

/**
 * Thème Néon - Couleurs vibrantes
 */
class NeonTheme : BasePracticeTheme() {
    override val id = "neon"
    override val displayName = "Néon"
    override val description = "Couleurs néon vibrantes"


    companion object {
        val NEON_COLORS = intArrayOf(
            0xFFFF00FF.toInt(),  // C  - Magenta néon
            0xFFFF00CC.toInt(),  // C#
            0xFFFF0099.toInt(),  // D  - Rose néon
            0xFFFF0066.toInt(),  // D#
            0xFFFF3300.toInt(),  // E  - Orange néon
            0xFFFF6600.toInt(),  // F
            0xFFFFFF00.toInt(),  // F# - Jaune néon
            0xFF00FF00.toInt(),  // G  - Vert néon
            0xFF00FFFF.toInt(),  // G# - Cyan néon
            0xFF00CCFF.toInt(),  // A  - Bleu néon
            0xFF0099FF.toInt(),  // A#
            0xFF6600FF.toInt()   // B  - Violet néon
        )
    }

    override fun getBackgroundColors() = Pair(
        Color.parseColor("#0A0A0F"),
        Color.parseColor("#15151F")
    )

    override fun getHitZoneColor() = Color.parseColor("#00FFFF")
    override fun getGridLineColor() = Color.parseColor("#20FFFFFF")

    override fun getNoteColor(pitchClass: Int) = NEON_COLORS[pitchClass % 12]

    override fun hasGlowEffect() = true
    override fun getGlowIntensity() = 0.6f
    override fun getGlowRadiusDp() = 10f
    override fun hasParticles() = true
}

/**
 * Thème Océan - Dégradés bleu-turquoise (version gratuite simple)
 */
class OceanTheme : BasePracticeTheme() {
    override val id = "ocean"
    override val displayName = "Océan"
    override val description = "Dégradés bleu-turquoise apaisants"


    companion object {
        val OCEAN_COLORS = intArrayOf(
            0xFF006994.toInt(),  // C  - Bleu océan profond
            0xFF0077A3.toInt(),  // C#
            0xFF0088B2.toInt(),  // D  - Bleu océan
            0xFF009999.toInt(),  // D#
            0xFF00A8A8.toInt(),  // E  - Turquoise
            0xFF00B8B8.toInt(),  // F
            0xFF00C8C8.toInt(),  // F# - Cyan
            0xFF00D4AA.toInt(),  // G  - Turquoise clair
            0xFF00E0CC.toInt(),  // G# - Aqua
            0xFF40E0D0.toInt(),  // A  - Turquoise
            0xFF48D1CC.toInt(),  // A#
            0xFF20B2AA.toInt()   // B  - Bleu-vert
        )
    }

    override fun getBackgroundColors() = Pair(
        Color.parseColor("#001219"),
        Color.parseColor("#002233")
    )

    override fun getHitZoneColor() = Color.parseColor("#00D4AA")
    override fun getGridLineColor() = Color.parseColor("#15FFFFFF")
    override fun getSheetMusicNoteColor() = Color.parseColor("#1E88E5")

    override fun getNoteColor(pitchClass: Int) = OCEAN_COLORS[pitchClass % 12]

    override fun hasGlowEffect() = true
    override fun hasParticles() = true
}

/**
 * Thème Feu - Dégradés rouge-orange-jaune
 */
class FireTheme : BasePracticeTheme() {
    override val id = "fire"
    override val displayName = "Feu"
    override val description = "Dégradés rouge-orange-jaune"


    companion object {
        val FIRE_COLORS = intArrayOf(
            0xFFFF0000.toInt(),  // C  - Rouge feu
            0xFFFF1A00.toInt(),  // C#
            0xFFFF3300.toInt(),  // D  - Rouge-orange
            0xFFFF4D00.toInt(),  // D#
            0xFFFF6600.toInt(),  // E  - Orange
            0xFFFF8000.toInt(),  // F  - Orange vif
            0xFFFF9900.toInt(),  // F#
            0xFFFFB300.toInt(),  // G  - Or
            0xFFFFCC00.toInt(),  // G# - Jaune-or
            0xFFFFE600.toInt(),  // A  - Jaune
            0xFFFFFF00.toInt(),  // A# - Jaune vif
            0xFFFFFF66.toInt()   // B  - Jaune clair
        )
    }

    override fun getBackgroundColors() = Pair(
        Color.parseColor("#1A0A00"),
        Color.parseColor("#2A1500")
    )

    override fun getHitZoneColor() = Color.parseColor("#FF6600")
    override fun getGridLineColor() = Color.parseColor("#20FF6600")
    override fun getSheetMusicNoteColor() = Color.parseColor("#FF5722")

    override fun getNoteColor(pitchClass: Int) = FIRE_COLORS[pitchClass % 12]

    override fun hasGlowEffect() = true
    override fun getGlowIntensity() = 0.5f
    override fun hasParticles() = true
}
