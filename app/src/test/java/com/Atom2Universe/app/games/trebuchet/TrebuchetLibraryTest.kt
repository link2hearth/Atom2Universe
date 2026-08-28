package com.Atom2Universe.app.games.trebuchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'atelier : ranger une machine, la retrouver, et survivre à un enregistrement abîmé.
 *
 * Le dernier point est le seul qui compte vraiment. Ce texte-là vit dans les
 * préférences du téléphone : il a pu être écrit par une version plus ancienne du jeu,
 * tronqué par un arrêt brutal, ou modifié à la main par curiosité. Perdre une machine
 * enregistrée est ennuyeux ; ne plus pouvoir ouvrir le jeu l'est beaucoup plus, et
 * c'est ce que ferait une lecture qui lève une exception.
 */
class TrebuchetLibraryTest {

    private fun machine(
        beam: Float = 14f,
        lever: Float = 5f,
        kind: Projectile = Projectile.BOMBE
    ) = MachineConfig().apply {
        beamLength = beam
        leverRatio = lever
        pivotHeight = 9f
        counterweightMass = 5400f
        hangLength = 1.7f
        pinAngleDeg = 38f
        slingRatio = 0.72f
        projectile = kind
        clamp()
    }

    @Test
    fun `une machine rangée se retrouve telle quelle`() {
        val cfg = machine()
        val texte = MachineLibrary.encode(listOf(MachinePreset("Grosse Berthe", cfg)))
        val relu = MachineLibrary.decode(texte)

        assertEquals(1, relu.size)
        assertEquals("Grosse Berthe", relu[0].name)
        val c = relu[0].config
        assertEquals(cfg.beamLength, c.beamLength, 1e-3f)
        assertEquals(cfg.pivotHeight, c.pivotHeight, 1e-3f)
        assertEquals(cfg.leverRatio, c.leverRatio, 1e-3f)
        assertEquals(cfg.counterweightMass, c.counterweightMass, 1e-2f)
        assertEquals(cfg.hangLength, c.hangLength, 1e-3f)
        assertEquals(cfg.pinAngleDeg, c.pinAngleDeg, 1e-3f)
        assertEquals(cfg.slingRatio, c.slingRatio, 1e-3f)
        assertEquals(cfg.projectile, c.projectile)
    }

    @Test
    fun `une machine enregistrée ne bouge plus quand on bricole`() {
        val vivante = machine(beam = 14f)
        val gardee = MachinePreset("Témoin", vivante)
        // Le joueur continue de régler sa machine : celle qu'il a mise de côté ne doit
        // pas suivre, sans quoi enregistrer ne servirait à rien.
        vivante.beamLength = 20f
        vivante.projectile = Projectile.LOURD
        assertEquals(14f, gardee.config.beamLength, 1e-3f)
        assertEquals(Projectile.BOMBE, gardee.config.projectile)
    }

    @Test
    fun `enregistrer sous un nom déjà pris remplace, et remet en tête`() {
        var liste = emptyList<MachinePreset>()
        liste = MachineLibrary.put(liste, MachinePreset("Alpha", machine(beam = 10f)))
        liste = MachineLibrary.put(liste, MachinePreset("Bêta", machine(beam = 12f)))
        liste = MachineLibrary.put(liste, MachinePreset("alpha", machine(beam = 18f)))

        assertEquals("la liste a doublé un nom", 2, liste.size)
        assertEquals("le dernier enregistré n'est pas en tête", "alpha", liste[0].name)
        assertEquals(18f, liste[0].config.beamLength, 1e-3f)

        liste = MachineLibrary.remove(liste, "BÊTA")
        assertEquals(1, liste.size)
    }

    @Test
    fun `la liste est plafonnée`() {
        var liste = emptyList<MachinePreset>()
        repeat(MachineLibrary.MAX_PRESETS + 5) {
            liste = MachineLibrary.put(liste, MachinePreset("m$it", machine()))
        }
        assertEquals(MachineLibrary.MAX_PRESETS, liste.size)
        // La plus récente est en tête, la plus ancienne est celle qui est partie.
        assertEquals("m${MachineLibrary.MAX_PRESETS + 4}", liste[0].name)
        assertTrue("une machine oubliée traîne encore", liste.none { it.name == "m0" })
    }

    @Test
    fun `un enregistrement abîmé ne fait perdre que ses lignes abîmées`() {
        val bon = MachineLibrary.encode(
            listOf(
                MachinePreset("Bonne", machine()),
                MachinePreset("Autre", machine(beam = 8f))
            )
        )
        val sale = "\n" +
            "ligne sans rien\n" +
            "\tque des tabulations\t\t\t\t\t\t\t\n" +
            bon +
            "Tronquée\t12.0\t8.0\n" +
            "Chiffres\tpouet\t8.0\t4.0\t3000.0\t2.4\t45.0\t0.65\tBOULET\n" +
            "Projectile\t12.0\t8.0\t4.0\t3000.0\t2.4\t45.0\t0.65\tINCONNU\n"

        val relu = MachineLibrary.decode(sale)
        println("ATELIER ${relu.size} machines relues : ${relu.joinToString { it.name }}")
        assertEquals(3, relu.size)
        assertEquals("Bonne", relu[0].name)
        assertEquals("Autre", relu[1].name)
        // Un projectile inconnu — une version plus ancienne, ou plus récente — ne jette
        // pas la machine : elle repart avec le boulet, qui existera toujours.
        assertEquals("Projectile", relu[2].name)
        assertEquals(Projectile.BOULET, relu[2].config.projectile)
    }

    @Test
    fun `un nom est nettoyé de ce qui casserait le format`() {
        val p = MachinePreset("  la\tmachine\nà\rBernard, celle qui porte très loin  ", machine())
        println("ATELIER nom nettoyé : « ${p.name} »")
        assertTrue("le nom garde une tabulation", !p.name.contains('\t'))
        assertTrue("le nom garde un saut de ligne", !p.name.contains('\n'))
        assertTrue("le nom n'est pas raccourci", p.name.length <= MachinePreset.MAX_NAME)
        // Et il doit survivre à l'aller-retour, sinon le menu ne montrerait pas ce que
        // le joueur a tapé.
        assertEquals(p.name, MachineLibrary.decode(MachineLibrary.encode(listOf(p)))[0].name)
    }

    @Test
    fun `une machine relue est bornée comme une machine réglée`() {
        val fou = "Monstre\t999.0\t500.0\t99.0\t999999.0\t99.0\t900.0\t9.0\tLOURD\n"
        val c = MachineLibrary.decode(fou)[0].config
        println(
            "ATELIER machine folle bornée : poutre=${c.beamLength} levier=${c.leverRatio} " +
                "masse=${c.counterweightMass} crochet=${c.pinAngleDeg}"
        )
        assertTrue("la poutre n'est pas bornée", c.beamLength <= TrebuchetRules.BEAM_MAX)
        assertTrue("le levier n'est pas borné", c.leverRatio <= TrebuchetRules.LEVER_MAX)
        assertTrue("la masse n'est pas bornée", c.counterweightMass <= TrebuchetRules.CW_MAX)
        assertTrue("le crochet n'est pas borné", c.pinAngleDeg <= TrebuchetRules.PIN_MAX_DEG)
    }
}
