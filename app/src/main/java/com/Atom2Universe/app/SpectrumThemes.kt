package com.Atom2Universe.app

// GÉNÉRÉ par tools/theme/generate_spectrum_themes.py : ne pas modifier à la main.

/**
 * Les thèmes de couleur libre : [HUES] teintes × [LEVELS] intensités, rangés intensité par
 * intensité. Les couleurs d'accent sont celles des styles, pour dessiner la barre de teintes et
 * l'aperçu sans avoir à ouvrir chaque thème.
 */
object SpectrumThemes {
    const val HUES = 36
    const val LEVELS = 3

    private val styles = intArrayOf(
        R.style.Theme_A2U_Spectrum_Vivid_00, R.style.Theme_A2U_Spectrum_Vivid_01, R.style.Theme_A2U_Spectrum_Vivid_02, R.style.Theme_A2U_Spectrum_Vivid_03,
        R.style.Theme_A2U_Spectrum_Vivid_04, R.style.Theme_A2U_Spectrum_Vivid_05, R.style.Theme_A2U_Spectrum_Vivid_06, R.style.Theme_A2U_Spectrum_Vivid_07,
        R.style.Theme_A2U_Spectrum_Vivid_08, R.style.Theme_A2U_Spectrum_Vivid_09, R.style.Theme_A2U_Spectrum_Vivid_10, R.style.Theme_A2U_Spectrum_Vivid_11,
        R.style.Theme_A2U_Spectrum_Vivid_12, R.style.Theme_A2U_Spectrum_Vivid_13, R.style.Theme_A2U_Spectrum_Vivid_14, R.style.Theme_A2U_Spectrum_Vivid_15,
        R.style.Theme_A2U_Spectrum_Vivid_16, R.style.Theme_A2U_Spectrum_Vivid_17, R.style.Theme_A2U_Spectrum_Vivid_18, R.style.Theme_A2U_Spectrum_Vivid_19,
        R.style.Theme_A2U_Spectrum_Vivid_20, R.style.Theme_A2U_Spectrum_Vivid_21, R.style.Theme_A2U_Spectrum_Vivid_22, R.style.Theme_A2U_Spectrum_Vivid_23,
        R.style.Theme_A2U_Spectrum_Vivid_24, R.style.Theme_A2U_Spectrum_Vivid_25, R.style.Theme_A2U_Spectrum_Vivid_26, R.style.Theme_A2U_Spectrum_Vivid_27,
        R.style.Theme_A2U_Spectrum_Vivid_28, R.style.Theme_A2U_Spectrum_Vivid_29, R.style.Theme_A2U_Spectrum_Vivid_30, R.style.Theme_A2U_Spectrum_Vivid_31,
        R.style.Theme_A2U_Spectrum_Vivid_32, R.style.Theme_A2U_Spectrum_Vivid_33, R.style.Theme_A2U_Spectrum_Vivid_34, R.style.Theme_A2U_Spectrum_Vivid_35,
        R.style.Theme_A2U_Spectrum_Soft_00, R.style.Theme_A2U_Spectrum_Soft_01, R.style.Theme_A2U_Spectrum_Soft_02, R.style.Theme_A2U_Spectrum_Soft_03,
        R.style.Theme_A2U_Spectrum_Soft_04, R.style.Theme_A2U_Spectrum_Soft_05, R.style.Theme_A2U_Spectrum_Soft_06, R.style.Theme_A2U_Spectrum_Soft_07,
        R.style.Theme_A2U_Spectrum_Soft_08, R.style.Theme_A2U_Spectrum_Soft_09, R.style.Theme_A2U_Spectrum_Soft_10, R.style.Theme_A2U_Spectrum_Soft_11,
        R.style.Theme_A2U_Spectrum_Soft_12, R.style.Theme_A2U_Spectrum_Soft_13, R.style.Theme_A2U_Spectrum_Soft_14, R.style.Theme_A2U_Spectrum_Soft_15,
        R.style.Theme_A2U_Spectrum_Soft_16, R.style.Theme_A2U_Spectrum_Soft_17, R.style.Theme_A2U_Spectrum_Soft_18, R.style.Theme_A2U_Spectrum_Soft_19,
        R.style.Theme_A2U_Spectrum_Soft_20, R.style.Theme_A2U_Spectrum_Soft_21, R.style.Theme_A2U_Spectrum_Soft_22, R.style.Theme_A2U_Spectrum_Soft_23,
        R.style.Theme_A2U_Spectrum_Soft_24, R.style.Theme_A2U_Spectrum_Soft_25, R.style.Theme_A2U_Spectrum_Soft_26, R.style.Theme_A2U_Spectrum_Soft_27,
        R.style.Theme_A2U_Spectrum_Soft_28, R.style.Theme_A2U_Spectrum_Soft_29, R.style.Theme_A2U_Spectrum_Soft_30, R.style.Theme_A2U_Spectrum_Soft_31,
        R.style.Theme_A2U_Spectrum_Soft_32, R.style.Theme_A2U_Spectrum_Soft_33, R.style.Theme_A2U_Spectrum_Soft_34, R.style.Theme_A2U_Spectrum_Soft_35,
        R.style.Theme_A2U_Spectrum_Pastel_00, R.style.Theme_A2U_Spectrum_Pastel_01, R.style.Theme_A2U_Spectrum_Pastel_02, R.style.Theme_A2U_Spectrum_Pastel_03,
        R.style.Theme_A2U_Spectrum_Pastel_04, R.style.Theme_A2U_Spectrum_Pastel_05, R.style.Theme_A2U_Spectrum_Pastel_06, R.style.Theme_A2U_Spectrum_Pastel_07,
        R.style.Theme_A2U_Spectrum_Pastel_08, R.style.Theme_A2U_Spectrum_Pastel_09, R.style.Theme_A2U_Spectrum_Pastel_10, R.style.Theme_A2U_Spectrum_Pastel_11,
        R.style.Theme_A2U_Spectrum_Pastel_12, R.style.Theme_A2U_Spectrum_Pastel_13, R.style.Theme_A2U_Spectrum_Pastel_14, R.style.Theme_A2U_Spectrum_Pastel_15,
        R.style.Theme_A2U_Spectrum_Pastel_16, R.style.Theme_A2U_Spectrum_Pastel_17, R.style.Theme_A2U_Spectrum_Pastel_18, R.style.Theme_A2U_Spectrum_Pastel_19,
        R.style.Theme_A2U_Spectrum_Pastel_20, R.style.Theme_A2U_Spectrum_Pastel_21, R.style.Theme_A2U_Spectrum_Pastel_22, R.style.Theme_A2U_Spectrum_Pastel_23,
        R.style.Theme_A2U_Spectrum_Pastel_24, R.style.Theme_A2U_Spectrum_Pastel_25, R.style.Theme_A2U_Spectrum_Pastel_26, R.style.Theme_A2U_Spectrum_Pastel_27,
        R.style.Theme_A2U_Spectrum_Pastel_28, R.style.Theme_A2U_Spectrum_Pastel_29, R.style.Theme_A2U_Spectrum_Pastel_30, R.style.Theme_A2U_Spectrum_Pastel_31,
        R.style.Theme_A2U_Spectrum_Pastel_32, R.style.Theme_A2U_Spectrum_Pastel_33, R.style.Theme_A2U_Spectrum_Pastel_34, R.style.Theme_A2U_Spectrum_Pastel_35,
    )

    private val accents = intArrayOf(
        0xFFF76565.toInt(), 0xFFF6684B.toInt(), 0xFFF46C27.toInt(), 0xFFE5780C.toInt(),
        0xFFC8890B.toInt(), 0xFFAF9309.toInt(), 0xFF9B9B08.toInt(), 0xFF86A008.toInt(),
        0xFF70A409.toInt(), 0xFF58A809.toInt(), 0xFF3FAB09.toInt(), 0xFF24AD09.toInt(),
        0xFF09AE09.toInt(), 0xFF09AD24.toInt(), 0xFF09AD40.toInt(), 0xFF09AC5A.toInt(),
        0xFF09AA74.toInt(), 0xFF09A88E.toInt(), 0xFF09A6A6.toInt(), 0xFF0AA3C1.toInt(),
        0xFF0C9EE6.toInt(), 0xFF3C98F5.toInt(), 0xFF6093F7.toInt(), 0xFF788EF8.toInt(),
        0xFF8989F9.toInt(), 0xFF9884F9.toInt(), 0xFFA77FF8.toInt(), 0xFFB777F8.toInt(),
        0xFFC96DF7.toInt(), 0xFFDD5EF7.toInt(), 0xFFF543F5.toInt(), 0xFFF64EDA.toInt(),
        0xFFF656C1.toInt(), 0xFFF65CA9.toInt(), 0xFFF75F91.toInt(), 0xFFF7637B.toInt(),
        0xFFD18585.toInt(), 0xFFCD887B.toInt(), 0xFFC88C6D.toInt(), 0xFFC2905D.toInt(),
        0xFFBA9448.toInt(), 0xFFAB9A41.toInt(), 0xFF9E9E3C.toInt(), 0xFF91A23D.toInt(),
        0xFF82A53E.toInt(), 0xFF74A840.toInt(), 0xFF64AB41.toInt(), 0xFF53AD41.toInt(),
        0xFF42AF42.toInt(), 0xFF42AE54.toInt(), 0xFF42AD66.toInt(), 0xFF41AD77.toInt(),
        0xFF41AB88.toInt(), 0xFF40AA98.toInt(), 0xFF40A9A9.toInt(), 0xFF46A6B9.toInt(),
        0xFF60A2C3.toInt(), 0xFF729EC9.toInt(), 0xFF7F9ACE.toInt(), 0xFF8A97D3.toInt(),
        0xFF9494D6.toInt(), 0xFF9C91D5.toInt(), 0xFFA58ED4.toInt(), 0xFFAF8BD3.toInt(),
        0xFFB887D1.toInt(), 0xFFC382CF.toInt(), 0xFFCE7DCE.toInt(), 0xFFCE7FC1.toInt(),
        0xFFCF80B5.toInt(), 0xFFCF82A8.toInt(), 0xFFD0839C.toInt(), 0xFFD18491.toInt(),
        0xFFF2B7B7.toInt(), 0xFFF1B9AD.toInt(), 0xFFEEBBA1.toInt(), 0xFFEBBD8E.toInt(),
        0xFFE7C174.toInt(), 0xFFDFC547.toInt(), 0xFFCCCC24.toInt(), 0xFFB5D225.toInt(),
        0xFF9CD826.toInt(), 0xFF87DB32.toInt(), 0xFF75DE41.toInt(), 0xFF64DF4C.toInt(),
        0xFF52E152.toInt(), 0xFF50E068.toInt(), 0xFF4DE07E.toInt(), 0xFF47DF93.toInt(),
        0xFF41DEA9.toInt(), 0xFF3BDCC2.toInt(), 0xFF30DBDB.toInt(), 0xFF74D3E6.toInt(),
        0xFF91CEEC.toInt(), 0xFFA4CAEF.toInt(), 0xFFB1C6F1.toInt(), 0xFFBAC4F3.toInt(),
        0xFFC1C1F4.toInt(), 0xFFC8BFF4.toInt(), 0xFFCFBDF3.toInt(), 0xFFD7BAF3.toInt(),
        0xFFDFB8F2.toInt(), 0xFFE7B4F2.toInt(), 0xFFF1B0F1.toInt(), 0xFFF1B2E7.toInt(),
        0xFFF2B3DD.toInt(), 0xFFF2B4D3.toInt(), 0xFFF2B5C9.toInt(), 0xFFF2B6C0.toInt(),
    )

    private fun index(hue: Int, level: Int) =
        level.coerceIn(0, LEVELS - 1) * HUES + Math.floorMod(hue, HUES)

    fun style(hue: Int, level: Int): Int = styles[index(hue, level)]
    fun accent(hue: Int, level: Int): Int = accents[index(hue, level)]
}
