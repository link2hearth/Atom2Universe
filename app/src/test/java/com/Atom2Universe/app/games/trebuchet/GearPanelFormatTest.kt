package com.Atom2Universe.app.games.trebuchet

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Les formats du tableau de bord de l'atelier, essayés avec l'argument qu'ils recevront.
 *
 * **Ce test existe pour une panne réelle et bête.** Le panneau met ses nombres en forme
 * avec `String.format`, et l'un des formats disait `%d` là où la vue passe un `Float` :
 * le compilateur ne voit rien — une chaîne de ressources n'est qu'un texte — et
 * l'application tombait à la première image affichée, sur une
 * `IllegalFormatConversionException`. Rien dans le code Kotlin ne peut attraper ça,
 * parce que le format et son argument ne se rencontrent qu'à l'exécution.
 *
 * On lit donc les fichiers de ressources tels quels et on essaie chaque format avec le
 * type que [GearMachineView] lui donne. Une erreur de type devient une erreur de test, et
 * les deux langues restent d'accord entre elles.
 */
class GearPanelFormatTest {

    /**
     * Les formats nourris par `Readout`, qui ne sait manipuler que des `Float`.
     *
     * Toute chaîne ajoutée à un `Readout` doit venir grossir cette liste — c'est le seul
     * endroit qui relie le type de l'argument au nom de la ressource.
     */
    private val floatFormats = listOf(
        "trebuchet_gear_panel_rpm",
        "trebuchet_gear_panel_bar",
        "trebuchet_gear_panel_kj",
        "trebuchet_gear_panel_m",
        "trebuchet_gear_panel_kw",
        "trebuchet_gear_panel_nm",
        "trebuchet_gear_panel_ratio_value",
        "trebuchet_gear_panel_ms",
        "trebuchet_gear_panel_count",
        "trebuchet_gear_panel_litres"
    )

    /** Les compteurs, qui sont de vrais entiers : ceux-là veulent bien `%d`. */
    private val intFormats = listOf("trebuchet_gear_panel_wheels")

    private fun stringsFile(locale: String): File {
        val name = if (locale.isEmpty()) "values" else "values-$locale"
        val relative = "app/src/main/res/$name/strings_trebuchet.xml"
        // Gradle lance les tests depuis le module, l'IDE parfois depuis la racine : on
        // remonte jusqu'à trouver le fichier plutôt que de parier sur l'un des deux.
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            val inModule = File(dir, relative.removePrefix("app/"))
            if (inModule.isFile) return inModule
            dir = dir.parentFile
        }
        fail("fichier de chaines introuvable pour « $name »")
        error("inatteignable")
    }

    /** La valeur brute d'une chaîne, sans démêler les échappements : on ne fait que la mettre en forme. */
    private fun value(xml: String, name: String): String? =
        Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)

    private fun checkLocale(locale: String) {
        val xml = stringsFile(locale).readText()
        for (name in floatFormats) {
            val format = value(xml, name)
            assertNotNull("« $name » manque dans « ${locale.ifEmpty { "values" }} »", format)
            try {
                val rendered = String.format(Locale.ROOT, format!!, 12.5f)
                assertTrue("« $name » ne rend rien", rendered.isNotEmpty())
            } catch (e: Exception) {
                fail("« $name » = « $format » refuse un Float : $e")
            }
        }
        for (name in intFormats) {
            val format = value(xml, name)
            assertNotNull("« $name » manque dans « ${locale.ifEmpty { "values" }} »", format)
            try {
                String.format(Locale.ROOT, format!!, 3)
            } catch (e: Exception) {
                fail("« $name » = « $format » refuse un Int : $e")
            }
        }
        // Les deux formats à plusieurs arguments, essayés avec leur vrai couple.
        val wheelsPart = value(xml, "trebuchet_gear_panel_wheels_part")
        assertNotNull("« trebuchet_gear_panel_wheels_part » manque", wheelsPart)
        try {
            String.format(Locale.ROOT, wheelsPart!!, 3, 8)
        } catch (e: Exception) {
            fail("« trebuchet_gear_panel_wheels_part » refuse deux Int : $e")
        }
        val span = value(xml, "trebuchet_gear_panel_span")
        assertNotNull("« trebuchet_gear_panel_span » manque", span)
        try {
            String.format(Locale.ROOT, span!!, "Moulin", 5f)
        } catch (e: Exception) {
            fail("« trebuchet_gear_panel_span » refuse un nom et un Float : $e")
        }
    }

    @Test
    fun `les formats du panneau acceptent leurs arguments en anglais`() = checkLocale("")

    @Test
    fun `les formats du panneau acceptent leurs arguments en francais`() = checkLocale("fr")
}
