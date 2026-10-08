package com.Atom2Universe.app.games.starbridges

import android.content.Context
import com.Atom2Universe.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Une constellation résolue, telle qu'elle vit dans l'atlas.
 *
 * On garde la figure **du joueur** — ses liens à lui, pas ceux du générateur : toute
 * configuration valide gagne, et c'est la sienne qu'il a tracée. Le nom est gardé en
 * indices, pour se traduire avec la langue de l'appli.
 */
class AtlasEntry(
    val seed: String,
    val size: Int,
    val noun: Int,
    val adjective: Int,
    /** Étoiles : x, y sur la grille et nombre de liens, à plat par triplets. */
    val stars: IntArray,
    /** Liens : indices d'étoiles dans [stars], à plat par paires. */
    val links: IntArray,
    /** Place dans le ciel de l'atlas, en unités de grille, et inclinaison en degrés. */
    val skyX: Float,
    val skyY: Float,
    val rotation: Float,
    val solvedAt: Long,
    val seconds: Long
) {
    val starCount get() = stars.size / 3
    /** Rayon qui contient toute la figure, en unités de grille. */
    val radius get() = size * 0.75f

    /**
     * Une ligne compacte : `1;graine;taille;nom;adjectif;x;y;inclinaison;date;secondes;étoiles;liens`.
     * Les étoiles s'écrivent en trois chiffres chacune (x, y, liens : tout tient entre 0 et
     * 8), les liens en deux caractères d'un alphabet de 64. Une grande figure tient en
     * deux cents octets, contre sept cents en JSON.
     */
    fun toLine(): String {
        val sb = StringBuilder(200)
        sb.append("1;").append(seed).append(';').append(size).append(';').append(noun).append(';').append(adjective)
            .append(';').append(round1(skyX)).append(';').append(round1(skyY)).append(';').append(round1(rotation))
            .append(';').append(solvedAt).append(';').append(seconds).append(';')
        for (v in stars) sb.append(('0' + v.coerceIn(0, 9)))
        sb.append(';')
        for (v in links) sb.append(ALPHABET[v.coerceIn(0, ALPHABET.length - 1)])
        return sb.toString()
    }

    companion object {
        private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_"

        private fun round1(v: Float) = Math.round(v * 10f) / 10f

        fun fromLine(line: String): AtlasEntry? {
            val f = line.split(';')
            if (f.size != 12 || f[0] != "1") return null
            return try {
                AtlasEntry(
                    f[1], f[2].toInt(), f[3].toInt(), f[4].toInt(),
                    IntArray(f[10].length) { f[10][it] - '0' },
                    IntArray(f[11].length) { ALPHABET.indexOf(f[11][it]) },
                    f[5].toFloat(), f[6].toFloat(), f[7].toFloat(), f[8].toLong(), f[9].toLong()
                )
            } catch (_: RuntimeException) {
                null
            }
        }

        /** L'ancien rangement, en JSON dans les préférences : ne sert plus qu'à migrer. */
        fun fromJson(o: JSONObject): AtlasEntry {
            fun ints(a: JSONArray) = IntArray(a.length()) { a.getInt(it) }
            return AtlasEntry(
                o.getString("seed"), o.getInt("size"), o.getInt("noun"), o.getInt("adj"),
                ints(o.getJSONArray("stars")), ints(o.getJSONArray("links")),
                o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("rot").toFloat(),
                o.getLong("at"), o.optLong("sec", 0L)
            )
        }
    }
}

/**
 * Les constellations du joueur, dans **leur propre fichier**, une par ligne.
 *
 * Elles vivaient d'abord dans les préférences du jeu, à côté de la partie en cours — or
 * Android réécrit tout le fichier de préférences à chaque sauvegarde, et la partie se
 * sauvegarde à chaque trait posé : mille constellations, c'était 600 Ko réécrits par
 * geste. Ici, ajouter une constellation **ajoute une ligne** au bout du fichier, sans
 * toucher aux autres ; et la partie en cours ne le voit jamais.
 */
class ConstellationAtlas(context: Context) {

    private val file = File(context.filesDir, FILE)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        const val PREFS = "starbridges_save"
        private const val FILE = "constellation_atlas.txt"
        private const val LEGACY_KEY = "atlas"
        /** Écart minimal entre deux figures, en unités de grille. */
        private const val MARGIN = 1.6f
    }

    private var cache: List<AtlasEntry>? = null

    fun entries(): List<AtlasEntry> {
        cache?.let { return it }
        migrate()
        val list = if (file.exists()) file.readLines().mapNotNull { AtlasEntry.fromLine(it) } else emptyList()
        cache = list
        return list
    }

    /** Les premières constellations ont été rangées en JSON dans les préférences : on les déménage. */
    private fun migrate() {
        val raw = prefs.getString(LEGACY_KEY, null) ?: return
        try {
            val a = JSONArray(raw)
            val lines = (0 until a.length()).map { AtlasEntry.fromJson(a.getJSONObject(it)).toLine() }
            if (lines.isNotEmpty()) file.appendText(lines.joinToString("\n", postfix = "\n"))
        } catch (_: Exception) {
        }
        prefs.edit().remove(LEGACY_KEY).apply()
    }

    fun find(seed: String): AtlasEntry? = entries().firstOrNull { it.seed == seed }

    /**
     * Ajoute la grille résolue et rend son entrée ; rend l'entrée existante si cette grille
     * est déjà dans l'atlas.
     *
     * **Le nom** vient de la graine, mais deux constellations ne portent jamais le même :
     * en cas de doublon, on passe à l'adjectif suivant. **La place** se cherche sur une
     * spirale partant du centre, au premier endroit où la figure ne touche aucune autre :
     * le ciel se remplit du milieu vers les bords, et une place donnée ne bouge plus.
     */
    fun add(game: StarBridgesGame, nounCount: Int, adjectiveCount: Int): AtlasEntry {
        val list = entries()
        list.firstOrNull { it.seed == game.seed }?.let { return it }

        var (noun, adjective) = game.nameIndices(nounCount, adjectiveCount)
        val taken = list.map { it.noun to it.adjective }.toHashSet()
        var guard = 0
        while ((noun to adjective) in taken && guard < nounCount * adjectiveCount) {
            adjective = (adjective + 1) % adjectiveCount
            if (adjective == 0) noun = (noun + 1) % nounCount
            guard++
        }

        val index = HashMap<Int, Int>()
        val stars = IntArray(game.nodes.size * 3)
        game.nodes.forEachIndexed { i, n ->
            index[n.id] = i
            stars[i * 3] = n.x; stars[i * 3 + 1] = n.y; stars[i * 3 + 2] = n.required
        }
        val placed = game.bridges.filterValues { it > 0 }.keys.toList()
        val links = IntArray(placed.size * 2)
        placed.forEachIndexed { i, k ->
            val (a, b) = game.endsOf(k)
            links[i * 2] = index[a] ?: 0; links[i * 2 + 1] = index[b] ?: 0
        }

        val radius = game.size * 0.75f
        var x = 0f; var y = 0f
        var k = 0
        while (true) {
            val angle = k * 2.39996f
            val dist = 2.2f * sqrt(k.toFloat())
            x = cos(angle) * dist; y = sin(angle) * dist
            if (list.none { hypot(it.skyX - x, it.skyY - y) < it.radius + radius + MARGIN }) break
            k++
        }
        val rnd = Random(game.seed.hashCode())
        val entry = AtlasEntry(
            game.seed, game.size, noun, adjective, stars, links, x, y,
            rotation = (rnd.nextFloat() * 2f - 1f) * 35f,
            solvedAt = System.currentTimeMillis(), seconds = game.elapsedSeconds
        )
        file.appendText(entry.toLine() + "\n")
        cache = list + entry
        return entry
    }
}

/** Les noms de constellations, dans la langue de l'appli. */
object ConstellationNames {

    fun nounCount(context: Context) = context.resources.getStringArray(R.array.constellation_nouns).size
    fun adjectiveCount(context: Context) = context.resources.getStringArray(R.array.constellation_adjectives).size

    /**
     * Chaque nom porte son genre en tête (« f:la Lyre ») ; chaque adjectif ses deux formes
     * (« boréal|boréale »). L'ordre des mots vient de la chaîne de format : le français dit
     * « la Lyre brisée », l'anglais « The Broken Lyre ».
     */
    fun name(context: Context, noun: Int, adjective: Int): String {
        val nouns = context.resources.getStringArray(R.array.constellation_nouns)
        val adjectives = context.resources.getStringArray(R.array.constellation_adjectives)
        val rawNoun = nouns[Math.floorMod(noun, nouns.size)]
        val feminine = rawNoun.startsWith("f:")
        val nounText = rawNoun.substringAfter(':')
        val forms = adjectives[Math.floorMod(adjective, adjectives.size)].split('|')
        val adjectiveText = if (feminine && forms.size > 1) forms[1] else forms[0]
        // « la Lyre brisée » en tête de ligne devient « La Lyre brisée ».
        return context.getString(R.string.constellation_name_format, nounText, adjectiveText)
            .replaceFirstChar { it.uppercase() }
    }
}
