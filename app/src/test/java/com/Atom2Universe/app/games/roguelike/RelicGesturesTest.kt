package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les gestes de reliques du feu, de la glace, du poison et des éclairs errants, tous joués
 * n'importe où sur l'écran. Même zone que la
 * barre de frappe : milieu à 0,72, ± 0,045 pour Parfait, ± 0,13 pour Bien.
 */
class RelicGesturesTest {

    // ── Le choix du geste ───────────────────────────────────────────────────────

    @Test
    fun chaqueElementASonGeste() {
        assertEquals(RelicGesture.DRAW_BOLT, RelicGesture.of(Relic.LIGHTNING))
        assertEquals(RelicGesture.DIAL, RelicGesture.of(Relic.MAGIC_MISSILE))
        assertEquals(RelicGesture.CHARGE, RelicGesture.of(Relic.FIREBALL))
        assertEquals(RelicGesture.CHARGE, RelicGesture.of(Relic.HOLY_LIGHT))
        assertEquals(RelicGesture.FREEZE, RelicGesture.of(Relic.ICE_SHARD))
        assertEquals(RelicGesture.DOSE, RelicGesture.of(Relic.PESTE))
        assertEquals(RelicGesture.DIAL, RelicGesture.of(Relic.SEISMIC_STRIKE))
        assertEquals(RelicGesture.DIAL, RelicGesture.of(Relic.WHIRLWIND))
        assertEquals(RelicGesture.DIAL, RelicGesture.of(Relic.HUNTERS_MARK))
        assertEquals(RelicGesture.DIAL, RelicGesture.of(Relic.PONCTION))
        assertEquals(RelicGesture.WAVE, RelicGesture.of(Relic.WAR_CRY))
        assertEquals(RelicGesture.DOSE, RelicGesture.of(Relic.POISONED_BLADES))
        assertEquals(RelicGesture.FREEZE, RelicGesture.of(Relic.STONESKIN))
        assertEquals(RelicGesture.DIAL, RelicGesture.of(Relic.HASTE))
        assertEquals(RelicGesture.HOURGLASS, RelicGesture.of(Relic.HOURGLASS))
        assertEquals(2, RelicGesture.dialZones(Relic.SEISMIC_STRIKE))
        assertEquals(3, RelicGesture.dialZones(Relic.HASTE))
        assertEquals(3, RelicGesture.dialZones(Relic.CHAIN_LIGHTNING))
        assertEquals(3, RelicGesture.dialZones(Relic.MAGIC_MISSILE))
    }

    // ── Feu : tenir pour attiser, un coup de doigt pour lancer ─────────────────

    // Le héros en (0, 0), une cible en (300, 0) (pour le dessin) ; jauge pleine en 1 000 ms,
    // un coup de doigt de 30 px lance.
    private fun charge() = ChargeGesture(0f, 0f, floatArrayOf(300f, 0f, 50f),
        fillMs = 1000f, flick = 30f, center = .72f, good = .13f, perfect = .045f, limitMs = 4000f)

    @Test
    fun attiserNoteLeCoupDeDoigt() {
        val perfect = charge().apply { down(0, 500f, 500f, 100f); move(0, 540f, 500f, 100f + 730f) }
        assertEquals(Timing.PERFECT, perfect.result)
        val good = charge().apply { down(0, 0f, 0f, 0f); move(0, 0f, -35f, 620f) }
        assertEquals(Timing.GOOD, good.result)
        val early = charge().apply { down(0, 0f, 0f, 0f); move(0, -40f, 10f, 300f) }
        assertEquals(Timing.MISS, early.result)
    }

    @Test
    fun attiserLeDoigtPeutTremblerSansLancer() {
        val g = charge()
        g.down(0, 200f, 200f, 0f)
        g.move(0, 215f, 210f, 300f)      // 18 px : pas encore un coup de doigt
        assertNull(g.result)
        g.down(1, 0f, 0f, 400f)          // un second doigt ne compte pas
        g.move(1, 100f, 0f, 450f)
        assertNull(g.result)
        g.move(0, 200f, 240f, 720f)      // 40 px du point de départ : lancé
        assertEquals(Timing.PERFECT, g.result)
    }

    @Test
    fun attiserUnCoupDeDoigtQuiFinitEnSeLevantCompte() {
        val g = charge().apply { down(0, 0f, 0f, 0f); up(0, 50f, 0f, 720f) }
        assertEquals(Timing.PERFECT, g.result)
    }

    @Test
    fun attiserLacherOuSemballerEstRate() {
        val dropped = charge().apply { down(0, 0f, 0f, 0f); up(0, 5f, 0f, 720f) }
        assertEquals(Timing.MISS, dropped.result)
        val runaway = charge().apply { down(0, 0f, 0f, 0f); update(999f) }
        assertNull(runaway.result)
        runaway.update(1000f)
        assertEquals(Timing.MISS, runaway.result)
        val nobody = charge().apply { update(4000f) }
        assertEquals(Timing.MISS, nobody.result)
    }

    // ── Glace : le doigt immobile sur la cible ─────────────────────────────────

    private fun freeze() = FreezeGesture(300f, 0f, 50f, stillRadius = 10f,
        fillMs = 1000f, center = .72f, good = .13f, perfect = .045f, limitMs = 4000f)

    @Test
    fun figerNoteLeDoigtLeve() {
        val perfect = freeze().apply { down(0, 300f, 0f, 0f); move(0, 305f, 3f, 300f); up(0, 305f, 3f, 720f) }
        assertEquals(Timing.PERFECT, perfect.result)
        val good = freeze().apply { down(0, 290f, 0f, 0f); up(0, 290f, 0f, 820f) }
        assertEquals(Timing.GOOD, good.result)
    }

    @Test
    fun figerBougerFaitToutFondre() {
        val g = freeze()
        g.down(0, 300f, 0f, 0f)
        g.move(0, 330f, 0f, 600f)        // 30 px : le givre fond, il repart de 600 ms
        assertEquals(0f, g.gauge(600f), 0f)
        assertEquals(600f, g.meltedAt, 0f)
        g.up(0, 330f, 0f, 600f + 720f)
        assertEquals(Timing.PERFECT, g.result)
    }

    @Test
    fun figerSeJoueNImporteOu() {
        val g = freeze()
        g.down(0, -500f, 800f, 0f)       // loin de la cible : le givre monte quand même
        assertTrue(g.pressed)
        assertEquals(.5f, g.gauge(500f), .001f)
        g.down(1, 300f, 0f, 600f)        // un second doigt ne compte pas
        g.up(1, 300f, 0f, 650f)
        assertNull(g.result)
        g.up(0, -500f, 800f, 720f)
        assertEquals(Timing.PERFECT, g.result)
    }

    @Test
    fun figerTropLongtempsFaitEclater() {
        val g = freeze().apply { down(0, 300f, 0f, 0f); update(1000f) }
        assertEquals(Timing.MISS, g.result)
    }

    // ── Poison : tapoter pour garder la dose dans la zone ──────────────────────

    // Une dose = 0,11 ; la jauge perd 0,42 par seconde ; 2 400 ms dont 800 d'échauffement.
    private fun dose() = DoseGesture(300f, 0f, 50f, bump = .11f, drainPerMs = .00042f,
        durationMs = 2400f, warmupMs = 800f, center = .72f, good = .13f,
        perfectShare = .75f, goodShare = .45f, limitMs = 4000f)

    /** Tapote toutes les [every] ms de 0 à la fin, en suivant chaque touche d'une image. */
    private fun DoseGesture.tap(every: Float, from: Float = 0f, count: Int = Int.MAX_VALUE) {
        var t = from; var n = 0
        while (t <= 2400f && n < count && result == null) { down(0, 300f, 0f, t); update(t); t += every; n++ }
        var u = t
        while (result == null && u <= 5000f) { update(u); u += 16f }
    }

    @Test
    fun doserAuBonRythmeEstParfait() {
        // 7 doses pour monter, puis une toutes les 262 ms : la jauge oscille dans la zone
        val g = dose()
        for (i in 0 until 7) g.down(0, 300f, 0f, i * 40f)
        g.tap(262f, from = 300f)
        assertEquals(Timing.PERFECT, g.result)
    }

    @Test
    fun doserTropLentEstRateEtTropVersEstRate() {
        val slow = dose().apply { tap(600f) }
        assertEquals(Timing.MISS, slow.result)
        val over = dose()
        for (i in 0 until 10) over.down(0, 300f, 0f, i * 10f)
        assertEquals(Timing.MISS, over.result)
    }

    @Test
    fun doserLaPartDuTempsDansLaZone() {
        // Monter dans la zone puis ne plus rien faire : la jauge redescend et sort de la zone
        val g = dose()
        g.down(0, 300f, 0f, 0f)                                    // la première dose lance le décompte
        for (i in 0 until 7) g.down(0, 300f, 0f, 700f + i * 10f)   // 0,745 à 760 ms
        // Elle sort de la zone (0,59) vers 760 + 0,155 / 0,00042 ≈ 1 130 ms :
        // 330 ms dans la zone sur les 1 600 comptées (de 800 à 2 400 ms)
        g.update(2400f)
        assertEquals(Timing.MISS, g.result)
        assertEquals(.205f, g.share(2400f), .02f)
    }

    @Test
    fun doserSeTapoteNImporteOu() {
        val g = dose()
        g.down(0, -600f, 900f, 0f)       // loin de la cible : la dose compte
        assertEquals(0f, g.firstAt, 0f)
        assertEquals(.11f, g.gauge(0f), .001f)
        g.down(3, 1200f, -50f, 0f)       // un autre doigt, ailleurs : encore une dose
        assertEquals(.22f, g.gauge(0f), .001f)
    }

    @Test
    fun doserPersonneNeTouchePasEstRate() {
        val g = dose()
        g.update(3990f)
        assertNull(g.result)
        g.update(4000f)
        assertEquals(Timing.MISS, g.result)
    }

    // ── Les éclairs errants : le cadran ────────────────────────────────────────

    // Un tour en 1 000 ms (0,36°/ms) ; zones à 180°, puis 90°, puis 270° plus loin. L'aiguille
    // part de midi (−90°) : elle arrive au centre de la première zone à 500 ms.
    private fun dial() = DialGesture(300f, 0f, 50f, gaps = floatArrayOf(180f, 90f, 270f),
        degPerMs = .36f, goodMs = 156f, perfectMs = 54f)

    @Test
    fun cadranLAiguilleRepartDansLAutreSensAChaqueTouche() {
        val g = dial()
        assertEquals(90f, g.zoneAngle, .01f)
        g.down(0, -999f, 999f, 510f)                 // n'importe où, 10 ms après le centre
        assertEquals(Timing.PERFECT, g.grades[0])
        assertEquals(-1f, g.direction, 0f)
        // Repartie de 93,6° dans l'autre sens, la zone suivante est 90° plus loin
        assertEquals(93.6f, g.playedAt[0], .01f)
        assertEquals(3.6f, g.zoneAngle, .01f)
        assertEquals(93.6f - .36f * 100f, g.needle(610f), .01f)
    }

    @Test
    fun cadranLaMoyenneDesTroisZones() {
        // Centres : 510 + 250 = 760 ms, puis (touche à 860) 860 + 750 = 1 610 ms
        val g = dial().apply { down(0, 0f, 0f, 510f); down(0, 0f, 0f, 860f) }
        assertEquals(Timing.GOOD, g.grades[1])
        assertNull(g.result)
        g.down(0, 0f, 0f, 1610f)
        // Parfait, bien, parfait : 5/3, c'est Parfait
        assertEquals(Timing.PERFECT, g.result)
    }

    @Test
    fun cadranUneToucheTropTotEstRateEtLAiguilleRepart() {
        val g = dial().apply { down(0, 0f, 0f, 100f) }
        assertEquals(Timing.MISS, g.grades[0])
        assertEquals(-1f, g.direction, 0f)
    }

    @Test
    fun cadranUneZoneDepasseeEstRatee() {
        val g = dial()
        g.update(500f + 150f)
        assertNull(g.grades[0])
        g.update(500f + 157f)
        assertEquals(Timing.MISS, g.grades[0])
        // L'aiguille repart du bord de la zone, au moment où sa fenêtre s'est fermée
        assertEquals(-90f + .36f * 656f, g.playedAt[0], .01f)
        // Personne ne touche plus : tout est raté d'un coup, même en sautant beaucoup de temps
        g.update(10000f)
        assertEquals(Timing.MISS, g.result)
        assertTrue(g.grades.all { it == Timing.MISS })
    }

    @Test
    fun cadranPhysiqueDeuxTouchesEtLeurMoyenne() {
        fun physical() = DialGesture(0f, 0f, 50f, floatArrayOf(180f, 90f), .36f, 156f, 54f)
        val perfect = physical().apply { down(0, 0f, 0f, 500f) }
        assertNull(perfect.result)
        assertEquals(-1f, perfect.direction, 0f)
        perfect.down(0, 0f, 0f, 750f)
        assertEquals(Timing.PERFECT, perfect.result)
        val good = physical().apply { down(0, 0f, 0f, 500f); down(0, 0f, 0f, 850f) }
        assertEquals(Timing.GOOD, good.result)
        val miss = physical().apply { down(0, 0f, 0f, 0f); down(0, 0f, 0f, 0f) }
        assertEquals(Timing.MISS, miss.result)
    }

    @Test
    fun criDeGuerreSeNoteAuRelachementEtTolereLeMouvement() {
        val g = WaveGesture(0f, 0f, 50f, 1000f, .72f, .13f, .045f, 4000f)
        g.down(0, 100f, 200f, 300f)
        g.move(0, 500f, 900f, 700f)
        g.down(1, 0f, 0f, 800f)
        g.up(1, 0f, 0f, 1020f)
        assertNull(g.result)
        g.up(0, 500f, 900f, 1020f)
        assertEquals(Timing.PERFECT, g.result)
    }

    @Test
    fun sablierSeRetourneDansLesDeuxSensPuisMontreLeSableEnHaut() {
        for (side in listOf(-1f, 1f)) {
            val g = HourglassGesture(0f, 0f, 100f, 8000f)
            g.down(0, 100f, 0f, 0f)
            g.move(0, 0f, side * 100f, 100f)
            assertEquals(side * 90f, g.rotation, .01f)
            g.up(0, -100f, 0f, 200f)
            assertTrue(g.flipped)
            assertNull(g.result)
            assertEquals(side * 180f, g.displayedRotation(380f), .01f)
            assertTrue(g.sandTime(500f) > 0f)
            g.update(949f)
            assertNull(g.result)
            g.update(950f)
            assertEquals(Timing.PERFECT, g.result)
        }
    }

    @Test
    fun sablierUnTapOuUnPassageParLeCentreNeLeRetournePas() {
        val g = HourglassGesture(0f, 0f, 100f, 8000f)
        g.down(0, 100f, 0f, 0f)
        g.up(0, 100f, 0f, 10f)
        assertEquals(0f, g.rotation, 0f)
        g.down(0, 100f, 0f, 100f)
        g.move(1, 0f, 100f, 200f) // Un second doigt est ignoré.
        g.move(0, 0f, 0f, 300f)
        g.up(0, -100f, 0f, 400f) // Passer par le centre n'est pas une rotation.
        assertEquals(0f, g.rotation, 0f)
        assertTrue(!g.flipped)
        g.update(8000f)
        assertEquals(Timing.MISS, g.result)
    }

    @Test
    fun sablierLaRotationContinueEntreDeuxAppuisEtTraverseMidi() {
        val g = HourglassGesture(0f, 0f, 100f, 8000f)
        g.down(0, -100f, 1f, 0f)
        g.up(0, -100f, -1f, 100f) // Traverse ±180° : seulement environ 1° de rotation.
        assertTrue(g.rotation in 0f..2f)
        g.down(0, 100f, 0f, 200f)
        g.up(0, 0f, 100f, 300f)
        assertTrue(g.rotation in 90f..92f)
        assertTrue(!g.flipped)
        g.down(0, 0f, 100f, 400f)
        g.up(0, -100f, 0f, 500f)
        assertTrue(g.flipped)
    }

}
