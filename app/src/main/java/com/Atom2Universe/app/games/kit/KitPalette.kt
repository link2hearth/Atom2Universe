package com.Atom2Universe.app.games.kit

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R

/**
 * Les couleurs des jeux du kit, lues **une fois** dans le thème de l'écran : clair ou sombre,
 * teinte choisie, surfaces teintées ou neutres. Les plateaux ne dessinent qu'avec elles, jamais
 * avec une couleur figée, pour suivre le thème comme le reste de l'appli.
 *
 * Les illustrations du hub, elles, sont cuites une fois pour toutes : elles prennent une palette
 * sombre fixe ([artwork]) avec la couleur propre au jeu.
 */
class KitPalette(
    val isLight: Boolean,
    val background: Int,
    val surface: Int,
    val raised: Int,
    val text: Int,
    val secondary: Int,
    val tertiary: Int,
    val outline: Int,
    val accent: Int,
    val accentDim: Int,
) {
    val onAccent: Int = contrasting(accent)

    /** Fines lignes de grille, et lignes épaisses (blocs, bords). */
    val gridLine: Int = ColorUtils.blendARGB(surface, text, if (isLight) 0.16f else 0.18f)
    val gridBold: Int = ColorUtils.blendARGB(surface, text, if (isLight) 0.62f else 0.55f)

    /** Une case vide, une case donnée (indice fixe), une case mise en avant. */
    val cell: Int = surface
    val cellFixed: Int = ColorUtils.blendARGB(surface, raised, 0.85f)
    val cellHighlight: Int = ColorUtils.blendARGB(surface, accent, if (isLight) 0.16f else 0.22f)

    /** Ce que le joueur a posé lui-même : la couleur du thème, assez contrastée pour se lire. */
    val ink: Int = ensureContrast(accent, surface, 3.0)

    val error: Int = if (isLight) 0xFFC62828.toInt() else 0xFFFF6B6B.toInt()
    val success: Int = if (isLight) 0xFF2E7D32.toInt() else 0xFF6BD98A.toInt()

    /**
     * Couleurs de pièces (Flood, Same Game, Map…) : des teintes bien séparées, un peu plus
     * sombres sur fond clair pour garder le contraste.
     */
    fun piece(index: Int): Int {
        val hues = PIECE_HUES
        val hue = hues[Math.floorMod(index, hues.size)]
        val hsl = floatArrayOf(hue, if (isLight) 0.62f else 0.68f, if (isLight) 0.50f else 0.60f)
        return ColorUtils.HSLToColor(hsl)
    }

    fun withAlpha(color: Int, alpha: Float): Int =
        ColorUtils.setAlphaComponent(color, (alpha.coerceIn(0f, 1f) * 255).toInt())

    fun blend(a: Int, b: Int, t: Float): Int = ColorUtils.blendARGB(a, b, t)

    companion object {
        private val PIECE_HUES = floatArrayOf(4f, 32f, 52f, 128f, 178f, 212f, 262f, 318f, 92f, 236f)

        fun from(context: Context): KitPalette {
            val accent = AppearanceStyle.color(context, R.attr.a2uMusicAccent)
            return KitPalette(
                isLight = AppearanceStyle.isLight(context),
                background = AppearanceStyle.color(context, R.attr.a2uBackgroundColor),
                surface = AppearanceStyle.color(context, R.attr.a2uSurfaceColor),
                raised = AppearanceStyle.color(context, R.attr.a2uRaisedColor),
                text = AppearanceStyle.color(context, R.attr.a2uTextColor),
                secondary = AppearanceStyle.color(context, R.attr.a2uSecondaryTextColor),
                tertiary = AppearanceStyle.color(context, R.attr.a2uTertiaryTextColor),
                outline = AppearanceStyle.color(context, R.attr.a2uOutlineColor),
                accent = accent,
                accentDim = AppearanceStyle.color(context, R.attr.a2uMusicAccentDim),
            )
        }

        /** Palette fixe des illustrations du hub : fond nuit, couleur propre au jeu. */
        fun artwork(accent: Int): KitPalette = KitPalette(
            isLight = false,
            background = 0xFF10141C.toInt(),
            surface = 0xFF1C2330.toInt(),
            raised = 0xFF2B3442.toInt(),
            text = 0xFFF5F7FA.toInt(),
            secondary = 0xFFB9C3D2.toInt(),
            tertiary = 0xFF7C8799.toInt(),
            outline = 0x28FFFFFF,
            accent = accent,
            accentDim = ColorUtils.blendARGB(0xFF10141C.toInt(), accent, 0.35f),
        )

        fun contrasting(background: Int): Int =
            if (ColorUtils.calculateContrast(Color.BLACK, ColorUtils.setAlphaComponent(background, 255)) >=
                ColorUtils.calculateContrast(Color.WHITE, ColorUtils.setAlphaComponent(background, 255)))
                Color.BLACK else Color.WHITE

        fun ensureContrast(color: Int, background: Int, minimum: Double): Int {
            val opaque = ColorUtils.setAlphaComponent(color, 255)
            val bg = ColorUtils.setAlphaComponent(background, 255)
            if (ColorUtils.calculateContrast(opaque, bg) >= minimum) return opaque
            val target = contrasting(bg)
            var low = 0f
            var high = 1f
            repeat(12) {
                val mid = (low + high) * 0.5f
                if (ColorUtils.calculateContrast(ColorUtils.blendARGB(opaque, target, mid), bg) >= minimum)
                    high = mid else low = mid
            }
            return ColorUtils.blendARGB(opaque, target, high)
        }
    }
}
