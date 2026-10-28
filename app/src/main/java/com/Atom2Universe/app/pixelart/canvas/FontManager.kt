package com.Atom2Universe.app.pixelart.canvas

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import com.Atom2Universe.app.R
import com.Atom2Universe.app.fonts.PersonalFonts

/**
 * Gestionnaire de polices personnalisées.
 * Polices système, pack assets/fonts/ optionnel et polices personnelles importées.
 */
object FontManager {
    private const val TAG = "FontManager"
    private const val FONTS_DIR = "fonts"

    // Cache des polices chargées
    private val fontCache = mutableMapOf<String, Typeface>()

    // Liste des polices disponibles (nom affiché -> nom de fichier)
    private val availableFonts = mutableMapOf<String, String>()
    private var personalRevision = -1L

    // Polices système : identifiant enregistré dans les dessins (ne change jamais) -> famille Android.
    private val systemFonts = listOf(
        "Sans Serif" to "sans-serif",
        "Serif" to "serif",
        "Monospace" to "monospace"
    )

    // Nom affiché de chaque police système, dans la langue de l'appli (l'identifiant, lui, reste le même).
    private val systemLabelRes = mapOf(
        "Sans Serif" to R.string.px_font_sans,
        "Serif" to R.string.px_font_serif,
        "Monospace" to R.string.px_font_mono,
    )
    private val systemLabels = mutableMapOf<String, String>()

    /**
     * Initialise le gestionnaire en scannant les polices disponibles.
     */
    @Synchronized fun init(context: Context) {
        availableFonts.clear()
        fontCache.clear()
        personalRevision = PersonalFonts.revision

        // Ajouter les polices système
        for ((displayName, _) in systemFonts) {
            availableFonts[displayName] = "system"
            systemLabelRes[displayName]?.let { systemLabels[displayName] = context.getString(it) }
        }

        // Scanner le dossier fonts
        try {
            val assetManager = context.assets
            val fontFiles = assetManager.list(FONTS_DIR) ?: emptyArray()

            for (fontFile in fontFiles) {
                if (fontFile.endsWith(".ttf") || fontFile.endsWith(".otf")) {
                    // Créer un nom d'affichage à partir du nom de fichier
                    val displayName = fontFile
                        .removeSuffix(".ttf")
                        .removeSuffix(".otf")
                        .replace("-", " ")
                        .replace("_", " ")

                    availableFonts[displayName] = fontFile
                    Log.d(TAG, "Found font: $displayName ($fontFile)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning fonts directory", e)
        }

        for (font in PersonalFonts.list(context)) availableFonts[font.label] = font.id

        Log.d(TAG, "Initialized with ${availableFonts.size} fonts")
    }

    /**
     * Retourne la liste des noms de polices disponibles (pour affichage).
     */
    @Synchronized fun getAvailableFontNames(): List<String> {
        return availableFonts.keys.sorted()
    }

    /** Imported fonts use a stable ID in drawings, even when their display names collide later. */
    @Synchronized fun getAvailableFonts(): List<Pair<String, String>> = availableFonts.entries
        .map { (label, source) ->
            val shown = if (source == "system") systemLabels[label] ?: label else label
            (if (PersonalFonts.isPersonal(source)) source else label) to shown
        }
        .sortedBy { it.second }

    /**
     * Charge et retourne une police par son nom d'affichage.
     */
    @Synchronized fun getTypeface(context: Context, displayName: String): Typeface {
        if (availableFonts.isEmpty() || personalRevision != PersonalFonts.revision) init(context)
        // Vérifier le cache
        fontCache[displayName]?.let { return it }

        val fileName = if (PersonalFonts.isPersonal(displayName)) displayName else availableFonts[displayName]

        val typeface = when {
            fileName == null -> Typeface.DEFAULT
            fileName == "system" -> {
                // Police système
                val systemName = systemFonts.find { it.first == displayName }?.second ?: "sans-serif"
                Typeface.create(systemName, Typeface.NORMAL)
            }
            PersonalFonts.isPersonal(fileName) -> PersonalFonts.loadTypeface(context, fileName) ?: Typeface.DEFAULT
            else -> {
                // Police personnalisée
                try {
                    Typeface.createFromAsset(context.assets, "$FONTS_DIR/$fileName")
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading font: $fileName", e)
                    Typeface.DEFAULT
                }
            }
        }

        // Mettre en cache
        fontCache[displayName] = typeface
        return typeface
    }

    /**
     * Retourne le nom de fichier pour une police (pour sérialisation).
     */
    fun getFontFileName(displayName: String): String? {
        return availableFonts[displayName]
    }

    /**
     * Retourne le nom d'affichage pour un nom de fichier (pour désérialisation).
     */
    fun getDisplayName(fileName: String): String {
        return availableFonts.entries.find { it.value == fileName }?.key
            ?: fileName.removeSuffix(".ttf").removeSuffix(".otf")
    }
}
