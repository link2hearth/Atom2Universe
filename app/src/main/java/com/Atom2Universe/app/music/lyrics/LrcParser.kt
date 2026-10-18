package com.Atom2Universe.app.music.lyrics

/**
 * Parser pour le format LRC (paroles synchronisées).
 * Format: [mm:ss.xx] texte de la ligne
 * Exemple: [00:12.00]Never gonna give you up
 */
object LrcParser {

    /**
     * Ligne de paroles avec timestamp.
     */
    data class LyricLine(
        val timeMs: Long,           // Temps en millisecondes
        val text: String            // Texte de la ligne
    )

    private val timestampPattern = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]")

    /**
     * Vérifie si les paroles contiennent des timestamps LRC.
     */
    fun isSynchronized(lyrics: String): Boolean {
        return lyrics.contains(timestampPattern)
    }

    /**
     * Parse les paroles LRC et retourne une liste triée par timestamp.
     * Supporte plusieurs timestamps par ligne (même texte répété).
     */
    fun parse(lyrics: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()

        lyrics.lines().forEach { rawLine ->
            val line = rawLine.trim()
            val timestamps = timestampPattern.findAll(line).toList()
            if (timestamps.isEmpty()) return@forEach

            val text = timestampPattern.replace(line, "").trim()
            if (text.isBlank()) return@forEach

            timestamps.forEach { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val subSeconds = match.groupValues[3]
                val subMs = if (subSeconds.length == 3) subSeconds.toLong()
                            else subSeconds.toLong() * 10
                val timeMs = (minutes * 60 * 1000) +
                    (seconds * 1000) +
                    subMs

                lines.add(LyricLine(timeMs, text))
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    /**
     * Extrait les paroles plain text (sans timestamps) depuis le format LRC.
     * Supporte plusieurs timestamps par ligne.
     */
    fun extractPlainText(lyrics: String): String {
        if (!isSynchronized(lyrics)) {
            return lyrics
        }

        return lyrics.lines()
            .map { line -> timestampPattern.replace(line.trim(), "").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    /**
     * Trouve l'index de la ligne active pour un temps donné.
     * Recherche binaire O(log n) — la liste doit être triée par timeMs (garantie par parse()).
     */
    fun getCurrentLineIndex(lines: List<LyricLine>, currentTimeMs: Long): Int {
        if (lines.isEmpty()) return -1
        val idx = lines.binarySearchBy(currentTimeMs) { it.timeMs }
        // idx >= 0 : correspondance exacte
        // idx < 0 : point d'insertion ip = -idx - 1, on veut ip - 1 = -idx - 2
        return if (idx >= 0) idx else (-idx - 2).coerceAtLeast(-1)
    }
}
