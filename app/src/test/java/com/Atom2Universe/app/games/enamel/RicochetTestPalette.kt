package com.Atom2Universe.app.games.enamel

import android.content.Context
import com.Atom2Universe.app.science.SciencePalette
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** Les tests JVM du projet n'embarquent pas les thèmes : palette fixe pour les tests de vue. */
@Implements(SciencePalette::class)
class RicochetTestPalette {
    @Implementation fun __constructor__(context: Context) = Unit
    @Implementation fun getBackground() = EnamelPainter.INK
    @Implementation fun getText() = EnamelPainter.CREAM
    @Implementation fun getSecondary() = EnamelPainter.CREAM
    @Implementation fun getAccent() = EnamelPainter.GOLD
    @Implementation fun getOnAccent() = EnamelPainter.INK
    @Implementation fun getOutline() = 0xFF416162.toInt()
    @Implementation fun ink(color: Int, on: Int) = color
}
