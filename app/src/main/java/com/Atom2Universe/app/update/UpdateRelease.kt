package com.Atom2Universe.app.update

import org.json.JSONObject
import java.net.URI

/** Les publications stables utilisent des tags v0.2.2 (ou 0.2.2). */
data class UpdateRelease(val tag: String, val notes: String, val url: String, val size: Long, val digest: String) {
    companion object {
        private val versionPattern = Regex("[vV]?(\\d+(?:\\.\\d+){1,3})")

        fun versionParts(version: String): List<Long>? {
            val numbers = versionPattern.matchEntire(version)?.groupValues?.get(1) ?: return null
            return numbers.split('.').map { it.toLongOrNull() ?: return null }
        }

        fun isNewer(candidate: String, installed: String): Boolean {
            val next = versionParts(candidate) ?: return false
            val current = versionParts(installed) ?: return false
            for (i in 0 until maxOf(next.size, current.size)) {
                val comparison = (next.getOrElse(i) { 0 }).compareTo(current.getOrElse(i) { 0 })
                if (comparison != 0) return comparison > 0
            }
            return false
        }

        fun fromGitHub(json: JSONObject, installed: String): UpdateRelease? {
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
            val tag = json.getString("tag_name")
            if (!isNewer(tag, installed)) return null
            val assets = json.getJSONArray("assets")
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }.filter {
                val name = it.optString("name").lowercase(java.util.Locale.ROOT)
                name.endsWith(".apk") && !name.contains("debug") &&
                    !name.contains("x86") && !name.contains("armeabi") &&
                    it.optString("state") == "uploaded"
            }
            // Une publication ambiguë doit être corrigée à la source, pas choisie au hasard.
            val asset = apks.singleOrNull() ?: return null
            val url = asset.getString("browser_download_url")
            val uri = URI(url)
            require(uri.scheme == "https" && uri.host == "github.com" &&
                uri.path.startsWith("/link2hearth/Atom2Universe/releases/download/"))
            val size = asset.getLong("size")
            require(size > 0)
            return UpdateRelease(tag, json.optString("body").take(12000), url, size, asset.optString("digest"))
        }
    }
}
