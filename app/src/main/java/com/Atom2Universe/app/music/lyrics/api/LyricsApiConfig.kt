package com.Atom2Universe.app.music.lyrics.api

import android.net.Uri

data class LyricsApiConfig(
    val urlTemplate: String,
    val headers: Map<String, String>,
    val lyricsPath: String?,
    val syncedLyricsPath: String?,
    val sourceLabel: String
) {
    companion object {
        private const val DEFAULT_PRIMARY_TEMPLATE =
            "{base}/search?track_name={title}&artist_name={artist}&album_name={album}&duration={duration}"
        private const val DEFAULT_FALLBACK_TEMPLATE = "{base}/{artist}/{title}"

        fun fromUserInput(
            rawUrl: String,
            headersText: String?,
            lyricsPath: String?,
            syncedLyricsPath: String?,
            isPrimary: Boolean
        ): LyricsApiConfig? {
            val trimmedUrl = rawUrl.trim()
            if (trimmedUrl.isBlank()) return null

            val template = resolveTemplate(trimmedUrl, isPrimary)
            val headers = parseHeaders(headersText)
            val sourceLabel = deriveSourceLabel(template, isPrimary)

            return LyricsApiConfig(
                urlTemplate = template,
                headers = headers,
                lyricsPath = lyricsPath?.trim()?.ifBlank { null },
                syncedLyricsPath = syncedLyricsPath?.trim()?.ifBlank { null },
                sourceLabel = sourceLabel
            )
        }

        private fun resolveTemplate(rawUrl: String, isPrimary: Boolean): String {
            val trimmed = rawUrl.trim()
            val hasPlaceholders = trimmed.contains("{artist}") ||
                trimmed.contains("{title}") ||
                trimmed.contains("{album}") ||
                trimmed.contains("{duration}") ||
                trimmed.contains("{query}")

            if (hasPlaceholders) {
                return trimmed
            }

            var base = trimmed.trimEnd('/')

            // lrclib.net nécessite /api avant les endpoints (/search, /get, etc.)
            if (base.contains("lrclib.net", ignoreCase = true) &&
                !base.endsWith("/api", ignoreCase = true)) {
                base = "$base/api"
            }

            val template = if (isPrimary) DEFAULT_PRIMARY_TEMPLATE else DEFAULT_FALLBACK_TEMPLATE
            return template.replace("{base}", base)
        }

        private fun parseHeaders(headersText: String?): Map<String, String> {
            if (headersText.isNullOrBlank()) return emptyMap()
            return headersText.lines()
                .mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isBlank()) {
                        null
                    } else {
                        val parts = trimmed.split(":", limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val value = parts[1].trim()
                            if (key.isNotBlank() && value.isNotBlank()) key to value else null
                        } else {
                            null
                        }
                    }
                }
                .toMap()
        }

        private fun deriveSourceLabel(template: String, isPrimary: Boolean): String {
            val sanitized = sanitizeTemplate(template)
            val host = runCatching {
                Uri.parse(sanitized).host
            }.getOrNull()
            return host ?: if (isPrimary) "primary" else "fallback"
        }

        private fun sanitizeTemplate(template: String): String {
            val result = StringBuilder()
            var index = 0
            while (index < template.length) {
                val start = template.indexOf('{', index)
                if (start == -1) {
                    result.append(template.substring(index))
                    break
                }
                result.append(template.substring(index, start))
                val end = template.indexOf('}', start + 1)
                if (end == -1) {
                    result.append(template.substring(start))
                    break
                }
                result.append("sample")
                index = end + 1
            }
            return result.toString()
        }
    }
}
