package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Les grands nombres du clicker : un resultat faux ici fausse en silence les prix, la
 * production et les sauvegardes, sans jamais faire planter l'app. On verifie l'arithmetique,
 * le passage entre les deux « couches » (mantisse/exposant puis 10^valeur) et l'aller-retour
 * de sauvegarde.
 */
class LayeredNumberTest {

    private fun ln(v: Double) = LayeredNumber(v)
    private fun near(expected: Double, actual: Double) =
        assertEquals(expected, actual, abs(expected) * 1e-9 + 1e-9)

    @Test fun arithmetiqueOrdinaire() {
        near(5.0, (ln(2.0) + ln(3.0)).toNumber())
        near(-1.0, (ln(2.0) - ln(3.0)).toNumber())
        near(6.0, (ln(2.0) * ln(3.0)).toNumber())
        near(2.5, (ln(5.0) / ln(2.0)).toNumber())
        near(1024.0, ln(2.0).pow(10.0).toNumber())
    }

    @Test fun soustraireSoiMemeDonneZero() {
        assertTrue((ln(123456.0) - ln(123456.0)).isZero())
        assertTrue((LayeredNumber.zero() + LayeredNumber.zero()).isZero())
    }

    @Test fun zeroEstNeutre() {
        near(7.0, (ln(7.0) + LayeredNumber.zero()).toNumber())
        near(7.0, (LayeredNumber.zero() + ln(7.0)).toNumber())
        assertTrue((ln(7.0) * LayeredNumber.zero()).isZero())
    }

    @Test fun comparaisons() {
        assertTrue(ln(3.0) > ln(2.0))
        assertTrue(ln(-3.0) < ln(2.0))
        assertTrue(ln(-3.0) < ln(-2.0))
        assertTrue(ln(1e300).greaterThan(ln(1e299)))
        assertTrue(ln(2.0).equalTo(ln(2.0)))
        assertTrue(ln(0.0).lessThan(ln(1e-6)))
        assertTrue("en dessous de 1e-12, un nombre est ramene a zero", ln(1e-300).isZero())
    }

    @Test fun lOrdreSurvitAuxCouchesElevees() {
        val big = ln(10.0).pow(5_000_000.0)       // couche 1 : 10^5 000 000
        val bigger = ln(10.0).pow(5_000_001.0)
        assertTrue(bigger > big)
        assertTrue(big > ln(1e300))
        assertTrue(-big < ln(1.0))
        assertTrue(big.greaterThan(ln(1e300) * ln(1e300)))
    }

    @Test fun multiplierEtDiviserSeCompensentEnHauteCouche() {
        val big = ln(10.0).pow(2_000_000.0)       // couche 1
        val back = (big * ln(1e10)) / ln(1e10)
        assertTrue(back.equalTo(big))
        val scaled = big * ln(1e10)
        assertEquals(1, scaled.layer)
        near(2_000_010.0, scaled.value)
    }

    @Test fun additionAvecUnTermeNegligeable() {
        val big = ln(1e100)
        val sum = big + ln(1.0)
        near(1e100, sum.toNumber())
        assertTrue((ln(1.0) + big).equalTo(big))
    }

    @Test fun sauvegardeEtRelecture() {
        val values = listOf(ln(0.0), ln(1.0), ln(-42.5), ln(1e12), ln(1e300) * ln(1e300), ln(10.0).pow(9_000_000.0))
        for (v in values) {
            val back = LayeredNumber.fromJSON(v.toJSON())
            assertTrue("$v -> $back", back.equalTo(v))
        }
    }

    @Test fun lectureDeTexteEtDeNombres() {
        near(1234.0, LayeredNumber("1234").toNumber())
        assertTrue(LayeredNumber("").isZero())
        assertTrue(LayeredNumber("pas un nombre").isZero())
        near(99.0, LayeredNumber.cast(99).toNumber())
        near(99.0, LayeredNumber.cast(99L).toNumber())
    }

    @Test fun affichageFormatAlphabetique() {
        val old = LayeredNumber.useAlphaFormat
        try {
            LayeredNumber.useAlphaFormat = true
            assertEquals("0", LayeredNumber.zero().toString())
            assertEquals("999", ln(999.0).toString())
            assertTrue(ln(1.5e6).toString().endsWith("a"))   // 1,50 a
            assertTrue(ln(1.5e9).toString().endsWith("b"))
        } finally {
            LayeredNumber.useAlphaFormat = old
        }
    }
}
