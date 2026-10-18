package com.Atom2Universe.app.games.infernale

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Un tableau range : son nom, la graine de son decor, et la liste de ce qu'on y a pose.
 *
 * On sauvegarde le **montage** (les poses) et jamais l'etat d'une machine en marche :
 * ouvrir un tableau le rend toujours tel qu'on l'avait laisse avant de lancer.
 */
class TableauSauve(
    val id: String,
    val nom: String,
    val graine: Long,
    val poses: List<Pose>,
    /** Date de derniere modification, en millisecondes. */
    val modifie: Long,
    /** Les liens plaque -> canon, par rang dans [poses]. */
    val liens: List<Lien> = emptyList()
)

/**
 * Le texte d'une pose, sur une ligne : `TYPE x y reglage taille taille2 miroir force`.
 *
 * Du texte simple plutot qu'un format a bibliotheque : il se lit a l'oeil, se teste sans
 * Android, et une ligne abimee n'en fait perdre qu'une.
 */
object PoseTexte {

    fun ecrire(p: Pose): String =
        "${p.type.name} ${p.x} ${p.y} ${p.reglage} ${p.taille} ${p.taille2} ${if (p.miroir) 1 else 0} ${p.force}"

    fun lire(ligne: String): Pose? {
        val m = ligne.trim().split(' ')
        if (m.size < 7) return null
        val type = TypePiece.entries.firstOrNull { it.name == m[0] } ?: return null
        val nombres = (1..5).map { m[it].toFloatOrNull() ?: return null }
        if (nombres.any { it.isNaN() || it.isInfinite() }) return null
        return Pose(
            type = type,
            x = nombres[0], y = nombres[1],
            reglage = nombres[2], taille = nombres[3], taille2 = nombres[4],
            miroir = m[6] == "1",
            force = m.getOrNull(7)?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f
        )
    }
}

/**
 * Les tableaux sauvegardes, un fichier texte chacun dans [dossier].
 *
 * Aucune base de donnees : quelques dizaines de lignes par tableau, lues en bloc. Un
 * fichier est ecrit a cote puis substitue, pour qu'une coupure en plein enregistrement ne
 * laisse jamais un tableau a moitie ecrit.
 */
class Sauvegardes(private val dossier: File) {

    private companion object {
        const val ENTETE_NOM = "nom\t"
        const val ENTETE_GRAINE = "graine\t"
        const val ENTETE_DATE = "date\t"
        const val EXT = ".txt"
    }

    /** Les tableaux, le plus recemment modifie en premier. */
    fun lister(): List<TableauSauve> =
        (dossier.listFiles { f -> f.isFile && f.name.endsWith(EXT) } ?: emptyArray())
            .mapNotNull { lire(it) }
            .sortedByDescending { it.modifie }

    fun charger(id: String): TableauSauve? = fichier(id).takeIf { it.isFile }?.let { lire(it) }

    /** Cree un tableau vide, le range, et le rend. */
    fun creer(nom: String, maintenant: Long = System.currentTimeMillis()): TableauSauve {
        var id = maintenant.toString()
        // Deux creations dans la meme milliseconde donneraient le meme fichier.
        var n = 0
        while (fichier(id).exists()) id = "${maintenant}_${++n}"
        val t = TableauSauve(id, nettoyer(nom), graine = maintenant xor (maintenant shl 17), poses = emptyList(), modifie = maintenant)
        ecrire(t)
        return t
    }

    /** Range l'etat courant d'un tableau. */
    fun enregistrer(t: TableauSauve) = ecrire(t)

    fun renommer(id: String, nom: String) {
        val t = charger(id) ?: return
        ecrire(TableauSauve(t.id, nettoyer(nom), t.graine, t.poses, System.currentTimeMillis(), t.liens))
    }

    fun supprimer(id: String) {
        fichier(id).delete()
    }

    private fun fichier(id: String) = File(dossier, id.filter { it.isLetterOrDigit() || it == '_' } + EXT)

    /** Un nom tient sur une ligne : ni retour a la ligne ni tabulation. */
    private fun nettoyer(nom: String): String = nom.replace(Regex("[\\r\\n\\t]+"), " ").trim().take(60)

    private fun ecrire(t: TableauSauve) {
        dossier.mkdirs()
        val texte = buildString {
            append(ENTETE_NOM).append(nettoyer(t.nom)).append('\n')
            append(ENTETE_GRAINE).append(t.graine).append('\n')
            append(ENTETE_DATE).append(t.modifie).append('\n')
            for (p in t.poses) append(PoseTexte.ecrire(p)).append('\n')
            for (l in t.liens) append("LIEN ").append(l.plaque).append(' ').append(l.cible).append('\n')
        }
        val cible = fichier(t.id)
        val temporaire = File(dossier, cible.name + ".tmp")
        temporaire.writeText(texte, Charsets.UTF_8)
        Files.move(temporaire.toPath(), cible.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun lire(f: File): TableauSauve? = try {
        var nom = ""
        var graine = 0L
        var date = f.lastModified()
        val poses = ArrayList<Pose>()
        val liens = ArrayList<Lien>()
        for (ligne in f.readLines(Charsets.UTF_8)) {
            when {
                ligne.startsWith(ENTETE_NOM) -> nom = ligne.removePrefix(ENTETE_NOM)
                ligne.startsWith(ENTETE_GRAINE) -> graine = ligne.removePrefix(ENTETE_GRAINE).toLongOrNull() ?: 0L
                ligne.startsWith(ENTETE_DATE) -> date = ligne.removePrefix(ENTETE_DATE).toLongOrNull() ?: date
                ligne.startsWith("LIEN ") -> {
                    val m = ligne.split(' ')
                    val a = m.getOrNull(1)?.toIntOrNull()
                    val b = m.getOrNull(2)?.toIntOrNull()
                    if (a != null && b != null) liens.add(Lien(a, b))
                }
                else -> PoseTexte.lire(ligne)?.let { poses.add(it) }
            }
        }
        TableauSauve(f.name.removeSuffix(EXT), nom, graine, poses, date, liens)
    } catch (_: Exception) {
        null
    }
}
