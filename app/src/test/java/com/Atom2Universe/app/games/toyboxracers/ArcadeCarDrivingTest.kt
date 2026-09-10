package com.Atom2Universe.app.games.toyboxracers

import com.Atom2Universe.app.games.toyboxracers.driving.ArcadeCar
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxTrackSection
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxWorld
import com.Atom2Universe.app.games.toyboxracers.editor.TrackStyle
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conduite façon kart : saut, dérapage tenu au bouton, relance et accroche.
 *
 * Les scénarios roulent sur une grande dalle de piste construite pour le test,
 * sans mur ni mobilier : un dérapage tenu fait tourner la voiture en rond, et
 * sur un circuit réel elle irait taper le décor avant la fin de la mesure.
 */
class ArcadeCarDrivingTest {

    private val step = 1f / 60f

    @Test
    fun leBoutonSautDecolleLaVoiturePuisLaRepose() {
        val car = launchedCar()
        val groundY = car.worldPosition.y
        val hopsBefore = car.hopSerial
        val landingsBefore = car.landingSerial

        car.update(step, hold(hopping = true))
        assertTrue("Le bouton doit décoller la voiture dès la première image", car.airborne)
        assertEquals(hopsBefore + 1, car.hopSerial)

        var apex = car.worldPosition.y
        repeat(60) {
            car.update(step, hold(hopping = true))
            apex = maxOf(apex, car.worldPosition.y)
        }
        assertFalse("Le saut doit retomber tout seul en moins d'une seconde", car.airborne)
        val height = apex - groundY
        assertTrue("Le saut reste court, ce n'est pas un saut de plateforme : $height", height < 1.2f)
        assertTrue("Le saut doit tout de même décoller : $height", height > 0.2f)
        assertTrue("La réception doit être signalée au rendu", car.landingSerial > landingsBefore)
    }

    @Test
    fun tenirLeBoutonEnViragePosteLaVoitureEnTravers() {
        val car = launchedCar()
        repeat(120) { car.update(step, hold(steering = 1f, hopping = true)) }

        assertTrue("Le bouton tenu doit installer un dérapage", car.drifting)
        assertEquals("Le côté est celui du braquage au moment de l'engagement", 1, car.driftDirection)
        // Braquage d'un côté : la voiture glisse de l'autre, donc le travers
        // mesuré dans le repère du véhicule est de signe opposé au braquage.
        assertTrue(
            "La voiture doit vraiment glisser sur le côté : ${car.headingOffset}",
            car.headingOffset < -0.12f
        )
        assertTrue("Le ruban doit se charger", car.turboCharge > 0f)
    }

    @Test
    fun leStickNeChangePlusDeCoteEnPleinDerapage() {
        val car = launchedCar()
        repeat(90) { car.update(step, hold(steering = 1f, hopping = true)) }
        assertEquals(1, car.driftDirection)

        val yawBefore = car.yawRadians
        // Le joueur pousse à l'opposé : la courbe doit s'élargir, jamais s'inverser.
        repeat(60) { car.update(step, hold(steering = -1f, hopping = true)) }

        assertEquals(
            "Le côté du dérapage ne change pas tant que le bouton est tenu",
            1, car.driftDirection
        )
        assertTrue("La voiture continue de tourner du même côté", car.yawRadians > yawBefore)
    }

    @Test
    fun relacherLeBoutonRelanceLaVoiture() {
        val car = launchedCar()
        repeat(150) { car.update(step, hold(steering = 1f, hopping = true)) }
        val releasesBefore = car.turboReleaseSerial
        assertTrue(car.turboCharge > 0f)

        car.update(step, hold(steering = 0f, hopping = false))

        assertEquals(
            "Le relâchement doit déclencher exactement une relance",
            releasesBefore + 1, car.turboReleaseSerial
        )
        assertTrue("La relance doit durer", car.turboBoostSeconds > 0f)
        assertEquals("La charge repart de zéro après la relance", 0f, car.turboCharge, 0.0001f)
        assertFalse(car.drifting)
    }

    @Test
    fun leDerapageTenuNeCreeAucuneVitesse() {
        val car = launchedCar()
        val startSpeed = car.speed
        var peak = startSpeed
        // Sans gaz et sans relance, un dérapage ne peut que coûter de la vitesse :
        // c'est le garde-fou qui empêche la glisse de devenir un raccourci gratuit.
        repeat(180) {
            car.update(step, hold(steering = 1f, hopping = true))
            peak = maxOf(peak, car.speed)
        }
        assertTrue(
            "La glisse ne doit jamais accélérer la voiture : $peak > $startSpeed",
            peak <= startSpeed + 0.01f
        )
    }

    @Test
    fun laGachetteAMoitieEnfonceeDonneMoinsDeCouple() {
        val slow = launchedCar(speed = 0f)
        val fast = launchedCar(speed = 0f)
        repeat(60) {
            slow.update(step, ArcadeCar.Input(0f, accelerating = true, braking = false, throttle = 0.4f))
            fast.update(step, ArcadeCar.Input(0f, accelerating = true, braking = false, throttle = 1f))
        }
        assertTrue("Une gâchette à 40 % doit accélérer moins fort", slow.speed < fast.speed * 0.7f)
        assertTrue("Elle doit tout de même faire avancer la voiture", slow.speed > 0.5f)
    }

    @Test
    fun lAdherenceSuitLaMatiereSousLesRoues() {
        val asphalt = launchedCar(style = TrackStyle.CLASSIC)
        asphalt.update(step, hold())
        assertEquals(TrackStyle.CLASSIC.grip, asphalt.surfaceGrip, 0.0001f)

        val ice = launchedCar(style = TrackStyle.ICE)
        ice.update(step, hold())
        assertEquals(TrackStyle.ICE.grip, ice.surfaceGrip, 0.0001f)
        assertTrue("La glace doit accrocher moins que le bitume", ice.surfaceGrip < asphalt.surfaceGrip)
    }

    @Test
    fun laGlaceLaisseLaVoitureFilerPlusLoinQueLeBitume() {
        val asphalt = launchedCar(style = TrackStyle.CLASSIC)
        val ice = launchedCar(style = TrackStyle.ICE)
        repeat(90) {
            asphalt.update(step, hold(steering = 1f))
            ice.update(step, hold(steering = 1f))
        }
        // Moins d'accroche, c'est moins de virage pour le même braquage : la
        // voiture continue tout droit au lieu de suivre la courbe demandée.
        assertTrue(
            "Sur la glace, la voiture doit tourner moins : ${ice.yawRadians} vs ${asphalt.yawRadians}",
            ice.yawRadians < asphalt.yawRadians
        )
    }

    @Test
    fun sauterDansUnMondeDeLEditeurNeFaitPasTraverserLaPiste() {
        // La piste construite est nettement au-dessus du circuit procédural qui
        // dort encore en mémoire. Sauter ne doit jamais faire atterrir la voiture
        // sur cette dalle invisible : ce serait passer au travers de la piste.
        val car = launchedCar(plateY = 6f)
        val resting = car.worldPosition.y
        assertTrue("La voiture doit démarrer sur la dalle construite : $resting", resting > 5.9f)

        var lowest = resting
        car.update(step, hold(hopping = true))
        repeat(120) {
            car.update(step, hold(hopping = true))
            lowest = minOf(lowest, car.worldPosition.y)
        }

        assertTrue("La voiture ne doit jamais descendre sous la piste : $lowest", lowest > 5.9f)
        assertFalse("Elle doit se reposer sur la piste construite", car.airborne)
        assertEquals(resting, car.worldPosition.y, 0.02f)
    }

    @Test
    fun leSautDecolleDepuisLaSurfaceEnjambeeEtNonDepuisLeChassisEnfonce() {
        // Sur une bosse, la caisse repose sur la MOYENNE des appuis de roues :
        // son plancher passe donc sous la surface qui la porte en son milieu.
        // Le saut doit d'abord la reposer dessus, sinon le premier contact du vol
        // est pris pour un choc de flanc et la voiture traverse la piste.
        val car = launchedCar()
        val surface = car.worldPosition.y - PrototypeTrack.CAR_CLEARANCE
        val sunk = car.worldPosition.y - 0.12f
        car.setPrivateField("airborneY", sunk)
        car.setPrivateField("worldPosition", PrototypeTrack.Vec3(car.worldPosition.x, sunk, car.worldPosition.z))

        car.update(step, hold(hopping = true))

        assertTrue("Le saut doit partir d'au-dessus de la surface enjambée", car.airborne)
        assertTrue(
            "Le plancher de la caisse ne doit plus être sous la piste : ${car.worldPosition.y}",
            car.worldPosition.y - PrototypeTrack.CAR_CLEARANCE >= surface - 0.001f
        )
    }

    @Test
    fun laBandeBoostRelanceSansRienTenir() {
        // La relance de la bande est déjà partie pendant l'assise de la voiture :
        // c'est bien le simple contact qui déclenche, sans gaz ni bouton.
        val car = launchedCar(style = TrackStyle.BOOST)
        assertTrue("Rouler sur la bande doit relancer", car.turboBoostSeconds > 0f)
        val serial = car.turboReleaseSerial

        repeat(120) { car.update(step, hold()) }

        assertEquals("Rester dessus ne relance pas en boucle", serial, car.turboReleaseSerial)
        assertTrue("La relance reste entretenue tant qu'on roule dessus", car.turboBoostSeconds > 0.9f)
        assertTrue("La bande doit dépasser la vitesse normale : ${car.speed}", car.speed > 21f)
    }

    @Test
    fun uneBandeClassiqueNeRelanceJamais() {
        val car = launchedCar(style = TrackStyle.CLASSIC)
        repeat(120) { car.update(step, hold()) }
        assertEquals(0f, car.turboBoostSeconds, 0.0001f)
    }

    @Test
    fun quitterLaBandeLaisseLaRelanceSEteindre() {
        val car = launchedCar(style = TrackStyle.BOOST, plateLength = 20f)
        assertTrue(car.turboBoostSeconds > 0f)
        // 20 unités à 14 unités/s : la bande est franchie en moins d'une seconde
        // et demie, la relance s'éteint dans la seconde et demie qui suit.
        repeat(300) { car.update(step, hold()) }
        assertEquals("La relance ne doit pas survivre à la bande", 0f, car.turboBoostSeconds, 0.0001f)
    }

    @Test
    fun marteleLeSautEnMonteeNeFaitJamaisTraverserLaPiste() {
        // Le geste exact qui cassait : arriver sur une rampe en tapant le bouton
        // saut. La caisse doit rester au-dessus de la piste à chaque image.
        val ramp = steepRamp()
        val car = rampCar(ramp)

        var worstClearance = Float.MAX_VALUE
        var sawHop = false
        repeat(300) { frame ->
            // Martèlement : appui/relâche toutes les quatre images.
            val hopping = (frame / 4) % 2 == 0
            car.update(step, ArcadeCar.Input(0f, accelerating = true, braking = false, hopping = hopping))
            if (car.airborne) sawHop = true
            val surface = ramp.surfaceYAt(car.worldPosition.x, car.worldPosition.z) ?: return@repeat
            worstClearance = minOf(
                worstClearance,
                car.worldPosition.y - PrototypeTrack.CAR_CLEARANCE - surface
            )
        }

        assertTrue("Le martèlement doit bien faire sauter la voiture", sawHop)
        assertTrue(
            "Le plancher de la caisse ne doit jamais passer sous la rampe : $worstClearance",
            worstClearance > -0.15f
        )
    }

    @Test
    fun leSautSAjouteALaMonteeAuLieuDeLaRemplacer() {
        val ramp = steepRamp()
        val climbing = rampCar(ramp)
        // Assez de rampe pour que la caisse monte plus vite que l'impulsion du
        // saut : c'est exactement le régime où l'ancien code la faisait couler.
        repeat(150) { climbing.update(step, ArcadeCar.Input(0f, accelerating = true, braking = false)) }
        val beforeHop = climbing.worldPosition.y

        climbing.update(step, ArcadeCar.Input(0f, accelerating = true, braking = false, hopping = true))
        val afterHop = climbing.worldPosition.y
        val surface = ramp.surfaceYAt(climbing.worldPosition.x, climbing.worldPosition.z)!!

        assertTrue("Le saut doit décoller", climbing.airborne)
        // En rampe, la caisse monte déjà : le saut doit ajouter à cette montée,
        // pas la remplacer. Sinon la rampe passe par-dessus la voiture.
        assertTrue("Le saut doit faire monter la caisse : $beforeHop -> $afterHop", afterHop > beforeHop)
        assertTrue(
            "La caisse doit rester au-dessus de la rampe dès la première image",
            afterHop - PrototypeTrack.CAR_CLEARANCE > surface
        )
    }

    private fun hold(steering: Float = 0f, hopping: Boolean = false) =
        ArcadeCar.Input(steering, accelerating = false, braking = false, hopping = hopping)

    /** Voiture posée sur une grande dalle plate, lancée dans son axe. */
    private fun launchedCar(
        speed: Float = 14f,
        style: TrackStyle = TrackStyle.CLASSIC,
        plateY: Float = 0f,
        plateLength: Float = 600f
    ): ArcadeCar {
        val car = ArcadeCar(PrototypeTrack())
        car.sandboxMode = true
        car.setEditorWorld(
            ToyboxWorld(
                volumes = emptyList(),
                trackSections = listOf(
                    ToyboxTrackSection(
                        id = 1L,
                        x = 0f, y = plateY, z = 0f,
                        yawDegrees = 0f,
                        length = plateLength,
                        width = 600f,
                        style = style
                    )
                )
            )
        )
        car.reset()
        car.setPrivateField("velocityX", kotlin.math.sin(car.yawRadians) * speed)
        car.setPrivateField("velocityZ", kotlin.math.cos(car.yawRadians) * speed)
        // Le départ pose la caisse à la hauteur nominale de la piste ; c'est la
        // suspension qui l'assied ensuite sur ses appuis réels. Les mesures
        // partent de cette assiette-là, pas de la pose théorique.
        repeat(5) { car.update(step, hold()) }
        return car
    }

    /** Rampe à 31° : au-delà d'environ 23°, une voiture lancée monte plus vite
     * que la seule impulsion du saut. C'est le seuil qui révèle le défaut. */
    private fun steepRamp() = ToyboxTrackSection(
        id = 1L, x = 0f, y = 0f, z = 0f, yawDegrees = 0f,
        length = 240f, width = 60f, endY = 144f
    )

    /** Voiture posée au bas d'une rampe donnée, à l'arrêt, prête à monter. */
    private fun rampCar(ramp: ToyboxTrackSection): ArcadeCar {
        val car = ArcadeCar(PrototypeTrack())
        car.sandboxMode = true
        car.setEditorWorld(ToyboxWorld(volumes = emptyList(), trackSections = listOf(ramp)))
        car.reset()
        repeat(5) { car.update(step, hold()) }
        return car
    }

    private fun ArcadeCar.setPrivateField(name: String, value: Any) {
        javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(this@setPrivateField, value)
        }
    }
}
