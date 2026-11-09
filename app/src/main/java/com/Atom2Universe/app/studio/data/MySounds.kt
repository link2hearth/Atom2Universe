package com.Atom2Universe.app.studio.data

import com.Atom2Universe.app.studio.dsp.SynthPatch
import org.json.JSONArray
import org.json.JSONException
import java.io.File

/**
 * Les sons de synthé gardés par l'utilisateur (« Garder ce son ») : un petit fichier JSON commun à tous les morceaux.
 * Chaque son porte l'identifiant « mine:<nom> » : le nom s'affiche tel quel, même dans un morceau où le son a été oublié
 * depuis (le morceau garde son propre exemplaire du réglage).
 */
class MySounds(private val file: File) {

    /** Les sons gardés, dans l'ordre où ils l'ont été. Un fichier abîmé donne une liste vide. */
    fun all(): List<SynthPatch> {
        if (!file.exists()) return emptyList()
        return try {
            val a = JSONArray(file.readText())
            List(a.length()) { StudioProjectCodec.decodeSynth(a.getJSONObject(it)) }
                .filter { it.id.startsWith(SynthPatch.MINE_PREFIX) }
        } catch (_: JSONException) {
            emptyList()
        } catch (_: java.io.IOException) {
            emptyList()
        }
    }

    /** Garde [patch] sous le nom [name] (un son du même nom est remplacé) et rend le son tel qu'il est gardé. */
    fun keep(patch: SynthPatch, name: String): SynthPatch {
        val kept = patch.copy(id = SynthPatch.MINE_PREFIX + name.trim())
        val list = all().filter { it.id != kept.id } + kept
        write(list)
        return kept
    }

    /** Oublie le son d'identifiant [id]. */
    fun forget(id: String) = write(all().filter { it.id != id })

    private fun write(list: List<SynthPatch>) {
        file.parentFile?.mkdirs()
        val a = JSONArray()
        for (p in list) a.put(StudioProjectCodec.encodeSynth(p))
        // Écriture dans un fichier voisin puis remplacement : un arrêt en plein milieu ne perd jamais la liste
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(a.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        /** Le nom affiché d'un son gardé (son identifiant sans le préfixe). */
        fun nameOf(id: String): String = id.removePrefix(SynthPatch.MINE_PREFIX)
    }
}
