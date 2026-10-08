package com.Atom2Universe.app.games.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException
import org.json.JSONObject
import com.Atom2Universe.app.R
import android.content.SharedPreferences

/**
 * Les dos de cartes rangés dans `assets/Assets/Cartes`, en cinq familles.
 * Un dos est tiré au hasard à chaque partie, parmi les familles que le joueur a gardées (section
 * « Dos de cartes » des options de l'appli ; toutes sont gardées au départ, et il en reste toujours
 * au moins une). Le solitaire, le blackjack et les jeux de cartes du kit s'en servent tous : la
 * liste des dossiers n'existe qu'ici.
 */
object CardBacks {

    /** Les extensions et les noms sont lus dans les assets, y compris après compression. */
    class Family(val folder: String, val labelRes: Int, val preferenceId: String, val legacyIndex: Int) {
        fun paths(context: Context): List<String> = runCatching { context.assets.list(folder) }.getOrNull().orEmpty()
            .filter { name -> IMAGE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) } }
            .sorted()
            .map { "$folder/$it" }

        fun samplePath(context: Context): String? {
            val paths = paths(context)
            return paths.firstOrNull {
                val stem = it.substringAfterLast('/').substringBefore('.')
                stem == "chien" || stem.endsWith("-chien")
            } ?: paths.firstOrNull()
        }
    }

    private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp")

    val families = listOf(
        Family("Assets/Cartes/bleu etoiles", R.string.settings_card_backs_blue_stars, "blue_stars", 0),
        Family("Assets/Cartes/rainbow", R.string.settings_card_backs_rainbow, "rainbow", 1),
        Family("Assets/Cartes/jaune vert", R.string.settings_card_backs_yellow_green, "yellow_green", 3),
        Family("Assets/Cartes/origami", R.string.settings_card_backs_origami, "origami", 4),
        Family("Assets/Cartes/gravures", R.string.settings_card_backs_engravings, "engravings", 5),
    )

    fun availableIndices(context: Context): List<Int> =
        families.indices.filter { families[it].paths(context).isNotEmpty() }

    private val legacyPaths = mutableMapOf<String, String>()
    private var legacyPathsLoaded = false

    /** Les parties de Memory enregistrent les anciens noms de fichiers. */
    @Synchronized
    fun resolveLegacyPath(context: Context, path: String): String {
        if (!legacyPathsLoaded) {
            runCatching {
                val json = context.assets.open("Assets/Cartes/renommages.json")
                    .bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                json.keys().forEach { key -> legacyPaths[key] = json.getString(key) }
            }
            legacyPathsLoaded = true
        }
        return legacyPaths[path] ?: path
    }

    private const val PREFS = "card_backs"
    private fun key(index: Int) = "set_${families[index].preferenceId}"

    /** Identifiants stables : retirer une collection ne décale pas les choix enregistrés. */
    @Synchronized
    private fun preferences(context: Context): SharedPreferences {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("named_sets_migrated", false)) {
            val enabled = families.map { prefs.getBoolean("family_${it.legacyIndex}", true) }
            val editor = prefs.edit()
            families.indices.forEach { index ->
                editor.putBoolean(key(index), enabled[index] || enabled.none { it })
            }
            editor.putBoolean("named_sets_migrated", true).apply()
        }
        return prefs
    }

    fun isEnabled(context: Context, index: Int): Boolean =
        preferences(context).getBoolean(key(index), true)

    /**
     * Garde ou écarte une famille. Écarter la dernière famille gardée ne fait rien (on ne se
     * retrouve jamais sans dos) : renvoie vrai si le changement a eu lieu.
     */
    fun setEnabled(context: Context, index: Int, enabled: Boolean): Boolean {
        if (!enabled && index in availableIndices(context) &&
            availableIndices(context).count { isEnabled(context, it) } <= 1 && isEnabled(context, index)) return false
        preferences(context).edit().putBoolean(key(index), enabled).apply()
        return true
    }

    private fun decode(context: Context, path: String, maxEdge: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val resolvedPath = resolveLegacyPath(context, path)
        context.assets.open(resolvedPath).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.assets.open(resolvedPath).use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: IOException) {
        null
    }

    /**
     * Un dos au hasard parmi les familles gardées, ou null si l'image manque (l'appelant dessine
     * alors un dos uni). Les images sont grandes : on les décode à une taille qui suffit à une carte.
     */
    fun loadRandom(context: Context, maxEdge: Int = 640): Bitmap? {
        val available = availableIndices(context)
        val kept = available.filter { isEnabled(context, it) }.ifEmpty { available }
        val family = kept.randomOrNull()?.let { families[it] } ?: return null
        val path = family.paths(context).randomOrNull() ?: return null
        return decode(context, path, maxEdge)
    }

    /** Le dos qui représente une famille dans les options (toujours le même, choisi à la main). */
    fun loadSample(context: Context, index: Int, maxEdge: Int = 400): Bitmap? {
        val path = families[index].samplePath(context) ?: return null
        return decode(context, path, maxEdge)
    }

    /** Aperçu de galerie ; à décoder hors du thread principal. */
    internal fun loadPreview(context: Context, path: String, maxEdge: Int): Bitmap? =
        decode(context, path, maxEdge)

    /** [source] ramené à [width] × [height] pixels, prêt à être dessiné sur une carte. */
    fun scaled(source: Bitmap, width: Int, height: Int): Bitmap =
        Bitmap.createScaledBitmap(source, width.coerceAtLeast(1), height.coerceAtLeast(1), true)
}
