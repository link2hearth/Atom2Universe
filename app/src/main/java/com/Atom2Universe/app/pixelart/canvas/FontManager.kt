package com.Atom2Universe.app.pixelart.canvas

import android.content.Context
import android.graphics.Typeface
import android.util.Log

/**
 * Gestionnaire de polices personnalisées.
 * Charge les polices depuis le dossier assets/fonts/.
 */
object FontManager {
    private const val TAG = "FontManager"
    private const val FONTS_DIR = "fonts"

    // Cache des polices chargées
    private val fontCache = mutableMapOf<String, Typeface>()

    // Liste des polices disponibles (nom affiché -> nom de fichier)
    private val availableFonts = mutableMapOf<String, String>()

    // Polices système
    private val systemFonts = listOf(
        "Sans Serif" to "sans-serif",
        "Serif" to "serif",
        "Monospace" to "monospace"
    )

    /**
     * Initialise le gestionnaire en scannant les polices disponibles.
     */
    fun init(context: Context) {
        availableFonts.clear()

        // Ajouter les polices système
        for ((displayName, _) in systemFonts) {
            availableFonts[displayName] = "system"
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

        Log.d(TAG, "Initialized with ${availableFonts.size} fonts")
    }

    /**
     * Retourne la liste des noms de polices disponibles (pour affichage).
     */
    fun getAvailableFontNames(): List<String> {
        return availableFonts.keys.sorted()
    }

    /**
     * Charge et retourne une police par son nom d'affichage.
     */
    fun getTypeface(context: Context, displayName: String): Typeface {
        // Vérifier le cache
        fontCache[displayName]?.let { return it }

        val fileName = availableFonts[displayName]

        val typeface = when {
            fileName == null -> Typeface.DEFAULT
            fileName == "system" -> {
                // Police système
                val systemName = systemFonts.find { it.first == displayName }?.second ?: "sans-serif"
                Typeface.create(systemName, Typeface.NORMAL)
            }
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
