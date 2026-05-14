package com.Atom2Universe.app.audioeditor

/**
 * Data class representing audio file metadata (ID3 tags).
 */
data class AudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null
) {
    /**
     * Build FFmpeg metadata arguments.
     * Returns metadata flags like: -metadata title="Title" -metadata artist="Artist"
     */
    fun toFFmpegArgs(): String {
        val args = StringBuilder()

        title?.takeIf { it.isNotBlank() }?.let {
            args.append(" -metadata title=\"${escapeFFmpegString(it)}\"")
        }

        artist?.takeIf { it.isNotBlank() }?.let {
            args.append(" -metadata artist=\"${escapeFFmpegString(it)}\"")
        }

        album?.takeIf { it.isNotBlank() }?.let {
            args.append(" -metadata album=\"${escapeFFmpegString(it)}\"")
        }

        year?.takeIf { it.isNotBlank() }?.let {
            args.append(" -metadata date=\"${escapeFFmpegString(it)}\"")
        }

        return args.toString()
    }

    companion object {
        /**
         * Escape special characters for FFmpeg command.
         * Handles shell metacharacters to prevent command injection.
         */
        fun escapeFFmpegString(input: String): String {
            return input
                .replace("\\", "\\\\")   // Backslash first
                .replace("\"", "\\\"")   // Double quotes
                .replace("'", "'\\''")   // Single quotes
                .replace("\$", "\\\$")   // Dollar sign (shell variable expansion)
                .replace("`", "\\`")     // Backticks (command substitution)
                .replace("!", "\\!")     // Exclamation (history expansion in some shells)
                .replace("\n", " ")      // Newlines could break command
                .replace("\r", "")       // Carriage returns
        }

        /**
         * Validate and sanitize a file path for FFmpeg.
         * Returns null if the path is invalid or potentially dangerous.
         */
        fun sanitizeFilePath(path: String): String? {
            // Reject paths with suspicious patterns
            if (path.contains("..") ||
                path.contains("\u0000") ||
                path.startsWith("-") ||
                path.contains("|") ||
                path.contains(";") ||
                path.contains("&") ||
                path.contains(">") ||
                path.contains("<")) {
                return null
            }
            return path
        }
    }

    private fun escapeFFmpegString(input: String): String {
        return AudioMetadata.escapeFFmpegString(input)
    }
}
